package com.zai.chat.network.transport

import com.zai.chat.config.ZaiConfig
import com.zai.chat.network.model.ChatCompletionRequest
import com.zai.chat.network.sse.ManualSseReader
import com.zai.chat.network.sse.StreamEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seam interface for chat completion streaming.
 * Decouples ChatRepository from the underlying transport (native OkHttp vs headless WebView).
 */
interface CompletionTransport {
    fun stream(request: ChatCompletionRequest): Flow<StreamEvent>
}

/**
 * Direct native OkHttp completion transport.
 * Used for direct un-gated APIs or test configurations.
 */
@Singleton
class OkHttpCompletionTransport @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) : CompletionTransport {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override fun stream(request: ChatCompletionRequest): Flow<StreamEvent> = flow {
        val http = Request.Builder()
            .url(ZaiConfig.BASE_URL + ZaiConfig.COMPLETIONS_PATH)
            .header("Accept", "text/event-stream")
            .post(json.encodeToString(request).toRequestBody(jsonMediaType))
            .build()

        val call = okHttpClient.newCall(http)
        currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }

        try {
            val response = call.execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    val errSnippet = resp.body?.string().orEmpty().take(400)
                    emit(
                        StreamEvent.Error(
                            throwable = IOException("HTTP ${resp.code}: $errSnippet"),
                            partialContent = "",
                            partialReasoning = ""
                        )
                    )
                    return@flow
                }
                val body = resp.body
                if (body == null) {
                    emit(StreamEvent.Error(IOException("Empty response body"), "", ""))
                    return@flow
                }
                ManualSseReader(body, json).streamEvents().collect { emit(it) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            emit(StreamEvent.Error(e, "", ""))
        }
    }.flowOn(Dispatchers.IO)
}
