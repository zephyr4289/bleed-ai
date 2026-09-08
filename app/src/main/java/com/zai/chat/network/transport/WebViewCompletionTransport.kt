package com.zai.chat.network.transport

import android.util.Log
import com.zai.chat.config.ZaiConfig
import com.zai.chat.data.local.preferences.JwtParser
import com.zai.chat.data.local.preferences.TokenManager
import com.zai.chat.network.auth.AuthEventManager
import com.zai.chat.network.model.ChatCompletionRequest
import com.zai.chat.network.model.CitationItem
import com.zai.chat.network.model.UsageInfo
import com.zai.chat.network.sse.StreamEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class BridgeEnvelope(
    val v: Int = 1,
    val t: String = "",
    val text: String = "",
    val code: Int = 0,
    val msg: String = "",
    val partialContent: String = "",
    val partialReasoning: String = "",
    val totalTokens: Int = 0,
    val citations: List<CitationItem> = emptyList()
)

@Singleton
class WebViewCompletionTransport @Inject constructor(
    private val webViewEngine: WebViewEngine,
    private val tokenManager: TokenManager,
    private val requestSigner: RequestSigner,
    private val authEventManager: AuthEventManager,
    private val json: Json
) : CompletionTransport {

    override fun stream(request: ChatCompletionRequest): Flow<StreamEvent> = flow {
        Log.i("BleedAI-Transport", "stream() -> Initiating completion for chat: ${request.chatId}, model: ${request.model}")
        val token = tokenManager.getStoredToken()
        if (token.isNullOrBlank()) {
            Log.e("BleedAI-Transport", "stream() -> No active session token found!")
            emit(StreamEvent.Error(IOException("No active session token configured"), "", ""))
            return@flow
        }

        val claims = JwtParser.parse(token)
        val userId = claims?.subject ?: "user"
        val promptText = request.messages.lastOrNull { it.role == "user" }?.content.orEmpty()

        val signed = requestSigner.sign(
            prompt = promptText,
            userId = userId,
            token = token
        )

        val targetUrl = "${ZaiConfig.BASE_URL}${ZaiConfig.COMPLETIONS_PATH}?${signed.queryParams}&signature_timestamp=${signed.timestamp}"
        Log.d("BleedAI-Transport", "Target completion URL: $targetUrl")

        val headersMap = mapOf(
            "Authorization" to "Bearer $token",
            "Content-Type" to "application/json",
            "Accept-Language" to "en-US",
            "X-FE-Version" to ZaiConfig.FE_VERSION_HEADER_VALUE,
            "X-Signature" to signed.signature
        )
        val headersJson = json.encodeToString(headersMap)

        val bodyObj = buildJsonObject {
            put("stream", true)
            put("model", request.model.ifBlank { ZaiConfig.MODEL_DEFAULT })
            put("messages", json.encodeToJsonElement(request.messages))
            putJsonObject("params") {}
            putJsonObject("features") {
                put("image_generation", false)
                put("web_search", request.webSearch)
                put("auto_web_search", false)
                put("preview_mode", false)
                put("enable_thinking", request.reasoning)
            }
            putJsonObject("variables") {}
            put("chat_id", request.chatId)
            request.fileIds?.let { put("files", json.encodeToJsonElement(it)) }
        }

        val bodyJson = json.encodeToString(bodyObj)
        Log.d("BleedAI-Transport", "Wire body payload (${bodyJson.length} chars): ${bodyJson.take(300)}")

        // Register cancellation hook to abort in-flight fetch in WebView
        currentCoroutineContext()[Job]?.invokeOnCompletion {
            Log.w("BleedAI-Transport", "Completion coroutine cancelled -> invoking abort()")
            webViewEngine.abort()
        }

        // Drain any stale events in channel
        while (true) {
            val stale = webViewEngine.eventChannel.tryReceive().getOrNull() ?: break
            Log.d("BleedAI-Transport", "Drained stale bridge event: $stale")
        }

        val ready = webViewEngine.ensureReady()
        Log.i("BleedAI-Transport", "WebViewEngine ready status: $ready")

        val sendJs = TransportScripts.buildSendJs(
            url = targetUrl,
            headersJson = headersJson,
            bodyJson = bodyJson
        )

        webViewEngine.eval(sendJs)
        Log.d("BleedAI-Transport", "Injected fetch script into WebView runtime")

        try {
            while (true) {
                val rawEvent = webViewEngine.eventChannel.receive()
                val envelope = try {
                    json.decodeFromString<BridgeEnvelope>(rawEvent)
                } catch (e: Exception) {
                    Log.w("BleedAI-Transport", "Failed to decode bridge envelope: $rawEvent, error: ${e.message}")
                    continue
                }

                when (envelope.t) {
                    "reasoning" -> {
                        Log.d("BleedAI-Transport", "Reasoning chunk (${envelope.text.length} chars)")
                        emit(StreamEvent.ReasoningDelta(envelope.text))
                    }
                    "content" -> {
                        Log.d("BleedAI-Transport", "Content chunk (${envelope.text.length} chars)")
                        emit(StreamEvent.ContentDelta(envelope.text))
                    }
                    "citations" -> {
                        Log.d("BleedAI-Transport", "Citations received (${envelope.citations.size} items)")
                        emit(StreamEvent.Citations(envelope.citations))
                    }
                    "usage" -> {
                        Log.d("BleedAI-Transport", "Usage tokens: ${envelope.totalTokens}")
                        emit(StreamEvent.Usage(UsageInfo(totalTokens = envelope.totalTokens)))
                    }
                    "done" -> {
                        Log.i("BleedAI-Transport", "Stream completed with DONE")
                        emit(StreamEvent.Done)
                        break
                    }
                    "error" -> {
                        Log.e("BleedAI-Transport", "Stream error received: code=${envelope.code}, msg=${envelope.msg}")
                        if (envelope.code == 401) {
                            authEventManager.emitTokenExpired()
                        }
                        emit(
                            StreamEvent.Error(
                                throwable = IOException("Stream Error (code ${envelope.code}): ${envelope.msg}"),
                                partialContent = envelope.partialContent,
                                partialReasoning = envelope.partialReasoning
                            )
                        )
                        break
                    }
                }
            }
        } catch (e: CancellationException) {
            Log.w("BleedAI-Transport", "Stream cancelled by caller")
            webViewEngine.abort()
            throw e
        } catch (e: Exception) {
            Log.e("BleedAI-Transport", "Unhandled exception in stream transport: ${e.message}", e)
            emit(StreamEvent.Error(e, "", ""))
        }
    }.flowOn(Dispatchers.IO)
}
