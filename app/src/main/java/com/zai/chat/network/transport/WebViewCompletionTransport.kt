package com.zai.chat.network.transport

import com.zai.chat.config.ZaiConfig
import com.zai.chat.data.local.preferences.JwtParser
import com.zai.chat.data.local.preferences.TokenManager
import com.zai.chat.network.auth.AuthEventManager
import com.zai.chat.network.model.ChatCompletionRequest
import com.zai.chat.network.sse.CitationsPayload
import com.zai.chat.network.sse.StreamEvent
import com.zai.chat.network.sse.UsagePayload
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
    val citations: List<CitationsPayload> = emptyList()
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
        val token = tokenManager.getStoredToken()
        if (token.isNullOrBlank()) {
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

        val headersMap = mapOf(
            "Authorization" to "Bearer $token",
            "Content-Type" to "application/json",
            "Accept-Language" to "en-US",
            "X-FE-Version" to ZaiConfig.FE_VERSION_HEADER_VALUE,
            "X-Signature" to signed.signature
        )
        val headersJson = json.encodeToString(headersMap)

        val bodyMap = linkedMapOf<String, Any?>(
            "stream" to true,
            "model" to request.model.ifBlank { ZaiConfig.MODEL_DEFAULT },
            "messages" to request.messages,
            "params" to emptyMap<String, String>(),
            "features" to mapOf(
                "image_generation" to false,
                "web_search" to request.webSearch,
                "auto_web_search" to false,
                "preview_mode" to false,
                "enable_thinking" to request.reasoning
            ),
            "variables" to emptyMap<String, String>()
        )
        request.chatId?.let { bodyMap["chat_id"] = it }
        request.fileIds?.let { bodyMap["files"] = it }

        val bodyJson = json.encodeToString(bodyMap)

        // Register cancellation hook to abort in-flight fetch in WebView
        currentCoroutineContext()[Job]?.invokeOnCompletion {
            kotlinx.coroutines.GlobalScope.let {
                // Non-blocking fire and forget JS abort
                webViewEngine.eval(TransportScripts.ABORT_JS)
            }
        }

        // Drain any stale events in channel
        while (true) {
            val stale = webViewEngine.eventChannel.tryReceive().getOrNull() ?: break
        }

        webViewEngine.ensureReady()

        val sendJs = TransportScripts.buildSendJs(
            url = targetUrl,
            headersJson = headersJson,
            bodyJson = bodyJson
        )

        webViewEngine.eval(sendJs)

        try {
            while (true) {
                val rawEvent = webViewEngine.eventChannel.receive()
                val envelope = try {
                    json.decodeFromString<BridgeEnvelope>(rawEvent)
                } catch (_: Exception) {
                    continue
                }

                when (envelope.t) {
                    "reasoning" -> emit(StreamEvent.ReasoningDelta(envelope.text))
                    "content" -> emit(StreamEvent.ContentDelta(envelope.text))
                    "citations" -> emit(StreamEvent.Citations(envelope.citations))
                    "usage" -> emit(StreamEvent.Usage(UsagePayload(totalTokens = envelope.totalTokens)))
                    "done" -> {
                        emit(StreamEvent.Done)
                        break
                    }
                    "error" -> {
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
            webViewEngine.eval(TransportScripts.ABORT_JS)
            throw e
        } catch (e: Exception) {
            emit(StreamEvent.Error(e, "", ""))
        }
    }.flowOn(Dispatchers.IO)
}
