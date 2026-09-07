package com.zai.chat.network.sse

import com.zai.chat.network.model.ChatCompletionChunk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import java.io.IOException

/**
 * Deliberately hand-rolled SSE parser (no library) because we need:
 *  - per-line tolerance (skip malformed lines, never kill the stream),
 *  - access to partial accumulations on mid-stream failure,
 *  - cooperative cancellation between blocking reads.
 *
 * Handles: "data:" payloads, ":" keepalive comments, [DONE], implicit
 * end-of-stream (server closes without [DONE] → treated as clean Done).
 * OpenAI-style servers emit complete JSON per data line; multi-line events
 * are not assembled (not needed for this API — revisit only if P12 captures
 * split JSON lines).
 */
class ManualSseReader(
    private val responseBody: ResponseBody,
    private val json: Json
) {
    fun streamEvents(): Flow<StreamEvent> = flow {
        val source = responseBody.source()
        val content = StringBuilder()
        val reasoning = StringBuilder()

        try {
            while (currentCoroutineContext().isActive && !source.exhausted()) {
                val line = source.readUtf8Line() ?: break

                when {
                    line.isBlank() -> continue
                    line.startsWith(":") -> continue                 // SSE comment/keepalive
                    !line.startsWith("data:") -> continue            // event:/id:/retry: ignored

                    else -> {
                        val payload = line.removePrefix("data:").trim()
                        if (payload == "[DONE]") {
                            emit(StreamEvent.Done)
                            return@flow
                        }

                        val chunk = try {
                            json.decodeFromString<ChatCompletionChunk>(payload)
                        } catch (e: Exception) {
                            continue                                  // malformed line → skip
                        }

                        chunk.choices.firstOrNull()?.delta?.let { delta ->
                            delta.reasoningContent?.takeIf { it.isNotEmpty() }?.let {
                                reasoning.append(it)
                                emit(StreamEvent.ReasoningDelta(it))
                            }
                            delta.content?.takeIf { it.isNotEmpty() }?.let {
                                content.append(it)
                                emit(StreamEvent.ContentDelta(it))
                            }
                        }

                        chunk.citations?.takeIf { it.isNotEmpty() }?.let {
                            emit(StreamEvent.Citations(it))
                        }
                        chunk.usage?.let { emit(StreamEvent.Usage(it)) }
                    }
                }
            }
            // Server closed the stream without [DONE] — still a clean end.
            emit(StreamEvent.Done)
        } catch (e: CancellationException) {
            throw e                                                   // never swallow cancellation
        } catch (e: IOException) {
            emit(
                StreamEvent.Error(
                    throwable = e,
                    partialContent = content.toString(),
                    partialReasoning = reasoning.toString()
                )
            )
        } finally {
            runCatching { responseBody.close() }
        }
    }.flowOn(Dispatchers.IO)
}
