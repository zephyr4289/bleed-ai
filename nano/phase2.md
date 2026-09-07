# 🌐 Phase 2 — Network Layer + Manual SSE Engine

**Goal:** The complete wire layer — DTOs, auth interceptor, line-by-line SSE parser, API service, DI module. This is the streaming heart of the app. **Gate:** CI green. Nothing UI-facing changes yet.

**Phase 2 contract (what P4's repository will build against — pin this down now):**

```
streamChatCompletion()  →  Flow<StreamEvent> that NEVER throws
                           (except CancellationException, which always propagates).
                           Exactly one terminal event: Done OR Error(with partials).
                           Connect failures, HTTP errors, mid-stream drops → all
                           become Error events. Repository/ViewModel never needs
                           try/catch around the stream.

suspend endpoints       →  throw IOException on failure (repository catches —
                           standard suspend semantics).

AuthInterceptor         →  silently stamps headers; on any 401 fires
                           AuthEvent.TokenExpired once (P5's dialog listens).
```

Two nice properties of this phase: it touches **only new files** (no edits to P0/P1), and it depends only on `TokenManager` — **not** on the DataStore file from last phase. So even if P1's `SettingsDataStore` still needs a fix from the PDF, this phase's CI result is independent.

## Setup

```bash
cd ~/zai
mkdir -p app/src/main/java/com/zai/chat/network/{model,auth,sse} app/src/main/java/com/zai/chat/di
```

---

## File 1: `network/model/ChatCompletionRequest.kt`

```kotlin
package com.zai.chat.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * [RECON] Outgoing completion body. Field names mirror the browser's POST.
 * P12 replaces guesses with the captured ground truth — single-file change.
 */
@Serializable
data class ChatCompletionRequest(
    @SerialName("model") val model: String,
    @SerialName("chat_id") val chatId: String,
    @SerialName("messages") val messages: List<RequestMessage>,
    @SerialName("stream") val stream: Boolean = true,
    @SerialName("web_search") val webSearch: Boolean = false,
    @SerialName("reasoning") val reasoning: Boolean = true,
    @SerialName("file_ids") val fileIds: List<String>? = null
)

@Serializable
data class RequestMessage(
    @SerialName("role") val role: String,
    @SerialName("content") val content: String
)
```

> Note: `encodeDefaults = true` in our Json instance (File 10) means `stream`, `web_search`, `reasoning` always appear on the wire even at default values — some servers reject absent keys.

## File 2: `network/model/ChatCompletionChunk.kt`

```kotlin
package com.zai.chat.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** [RECON] Inbound SSE chunk. GLM streams content and reasoning as two
 *  independent delta channels inside the same chunk format. */
@Serializable
data class ChatCompletionChunk(
    @SerialName("id") val id: String? = null,
    @SerialName("choices") val choices: List<ChunkChoice> = emptyList(),
    @SerialName("usage") val usage: UsageInfo? = null,
    @SerialName("citations") val citations: List<CitationItem>? = null
)

@Serializable
data class ChunkChoice(
    @SerialName("index") val index: Int = 0,
    @SerialName("delta") val delta: ChunkDelta = ChunkDelta(),
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class ChunkDelta(
    @SerialName("role") val role: String? = null,
    @SerialName("content") val content: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null
)

@Serializable
data class UsageInfo(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0
)

/** [RECON] Web-search citation shape — verified against a live capture in P12. */
@Serializable
data class CitationItem(
    @SerialName("index") val index: Int,
    @SerialName("url") val url: String,
    @SerialName("title") val title: String,
    @SerialName("snippet") val snippet: String? = null
)
```

## File 3: `network/model/ChatListResponse.kt`

```kotlin
package com.zai.chat.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** [RECON] GET /api/v1/chats/?page={n} → bare JSON array of these. */
@Serializable
data class ChatListItemResponse(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("updated_at") val updatedAt: Long,   // [RECON] may be ISO-8601 string on some builds
    @SerialName("pinned") val pinned: Boolean = false,
    @SerialName("folder_id") val folderId: String? = null
)

/** [RECON] GET /api/v1/chats/{id} → wrapper containing full message history. */
@Serializable
data class ChatDetailResponse(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("chat") val chat: ChatContentPayload
)

@Serializable
data class ChatContentPayload(
    @SerialName("messages") val messages: List<RemoteMessagePayload> = emptyList()
)

@Serializable
data class RemoteMessagePayload(
    @SerialName("id") val id: String,
    @SerialName("role") val role: String,
    @SerialName("content") val content: String,
    @SerialName("reasoning") val reasoning: String? = null,
    @SerialName("timestamp") val timestamp: Long = System.currentTimeMillis()
)
```

## File 4: `network/model/FileUploadResponse.kt`

```kotlin
package com.zai.chat.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** [RECON] Multipart upload response — we only need the id to reference in completions. */
@Serializable
data class FileUploadResponse(
    @SerialName("id") val id: String,
    @SerialName("filename") val filename: String
)

/** [RECON] GET /api/models → OpenAI-style wrapper. */
@Serializable
data class ModelsListResponse(
    @SerialName("data") val data: List<ModelEntry> = emptyList()
)

@Serializable
data class ModelEntry(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String? = null
)
```

## File 5: `network/auth/AuthEvent.kt`

```kotlin
package com.zai.chat.network.auth

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AuthEvent {
    data object TokenExpired : AuthEvent
}

/**
 * App-wide auth signal bus. The interceptor detects 401s deep inside the
 * network stack and publishes here; P5's reconnect dialog subscribes in
 * MainActivity. Decoupled by design — the network layer never knows about UI.
 */
@Singleton
class AuthEventManager @Inject constructor() {
    private val _events = MutableSharedFlow<AuthEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<AuthEvent> = _events.asSharedFlow()

    fun emitTokenExpired() {
        _events.tryEmit(AuthEvent.TokenExpired)
    }
}
```

## File 6: `network/auth/AuthInterceptor.kt`

```kotlin
package com.zai.chat.network.auth

import com.zai.chat.config.ZaiConfig
import com.zai.chat.data.local.preferences.TokenManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stamps every request with browser-identical headers + bearer token.
 *
 * P12 upgrade paths (both isolated to this file):
 *  - If recon shows the FE version header key changed → ZaiConfig edit only.
 *  - If bearer is rejected in favor of the full cookie string
 *    (cf_clearance etc.) → swap the Authorization block for a Cookie header.
 *  - If requests are HMAC-signed → build the sign header here using
 *    ZaiConfig.SIGN_SALT. No call sites change either way.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager,
    private val authEventManager: AuthEventManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
            .header("User-Agent", ZaiConfig.USER_AGENT)
            .header("Origin", ZaiConfig.ORIGIN)
            .header("Referer", ZaiConfig.REFERER)

        // P1 design: empty key = header disabled
        if (ZaiConfig.FE_VERSION_HEADER_KEY.isNotEmpty()) {
            builder.header(
                ZaiConfig.FE_VERSION_HEADER_KEY,
                ZaiConfig.FE_VERSION_HEADER_VALUE
            )
        }

        tokenManager.getStoredToken()?.let { token ->
            builder.header("Authorization", "Bearer $token")
        }

        val response = chain.proceed(builder.build())

        // Body untouched (streaming stays intact) — we only observe the code.
        if (response.code == 401) {
            authEventManager.emitTokenExpired()
        }
        return response
    }
}
```

## File 7: `network/sse/StreamEvent.kt`

```kotlin
package com.zai.chat.network.sse

import com.zai.chat.network.model.CitationItem
import com.zai.chat.network.model.UsageInfo

/**
 * Unified event stream for one completion. Terminal states:
 * exactly one of [Done] / [Error] is emitted, then the flow closes.
 * [Error.partialContent]/[partialReasoning] preserve everything received
 * before the failure — the UI renders it inline with a "retry" affordance.
 */
sealed interface StreamEvent {
    data class ReasoningDelta(val text: String) : StreamEvent
    data class ContentDelta(val text: String) : StreamEvent
    data class Citations(val citations: List<CitationItem>) : StreamEvent
    data class Usage(val usage: UsageInfo) : StreamEvent
    data object Done : StreamEvent
    data class Error(
        val throwable: Throwable,
        val partialContent: String,
        val partialReasoning: String
    ) : StreamEvent
}
```

## File 8: `network/sse/ManualSseReader.kt`

```kotlin
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
```

## File 9: `network/ZaiApiService.kt`

```kotlin
package com.zai.chat.network

import com.zai.chat.config.ZaiConfig
import com.zai.chat.network.model.ChatCompletionRequest
import com.zai.chat.network.model.ChatDetailResponse
import com.zai.chat.network.model.ChatListItemResponse
import com.zai.chat.network.model.FileUploadResponse
import com.zai.chat.network.model.ModelsListResponse
import com.zai.chat.network.sse.ManualSseReader
import com.zai.chat.network.sse.StreamEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ZaiApiService @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Fully cold and lazy: nothing hits the network until collected, and
     * everything (connect + stream) runs on Dispatchers.IO.
     * Cancellation aborts the socket instantly via call.cancel(), so the
     * Stop button takes effect even while blocked in a read.
     */
    fun streamChatCompletion(request: ChatCompletionRequest): Flow<StreamEvent> = flow {
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

    suspend fun getChats(page: Int): List<ChatListItemResponse> = withContext(Dispatchers.IO) {
        val http = Request.Builder()
            .url("${ZaiConfig.BASE_URL}${ZaiConfig.CHATS_PATH}/?page=$page")
            .get()
            .build()
        okHttpClient.newCall(http).execute().use { response ->
            if (!response.isSuccessful) throw IOException("getChats failed: HTTP ${response.code}")
            val body = response.body?.string() ?: return@use emptyList()
            json.decodeFromString(body)
        }
    }

    suspend fun getChatDetail(chatId: String): ChatDetailResponse = withContext(Dispatchers.IO) {
        val http = Request.Builder()
            .url("${ZaiConfig.BASE_URL}${ZaiConfig.CHATS_PATH}/$chatId")
            .get()
            .build()
        okHttpClient.newCall(http).execute().use { response ->
            if (!response.isSuccessful) throw IOException("getChatDetail failed: HTTP ${response.code}")
            val body = response.body?.string() ?: throw IOException("Empty chat detail body")
            json.decodeFromString(body)
        }
    }

    suspend fun createChat(title: String): ChatListItemResponse = withContext(Dispatchers.IO) {
        val payload = json.encodeToString(mapOf("title" to title))
        val http = Request.Builder()
            .url("${ZaiConfig.BASE_URL}${ZaiConfig.CHATS_PATH}/new")
            .post(payload.toRequestBody(jsonMediaType))
            .build()
        okHttpClient.newCall(http).execute().use { response ->
            if (!response.isSuccessful) throw IOException("createChat failed: HTTP ${response.code}")
            val body = response.body?.string() ?: throw IOException("Empty createChat body")
            json.decodeFromString(body)
        }
    }

    suspend fun deleteChat(chatId: String): Boolean = withContext(Dispatchers.IO) {
        val http = Request.Builder()
            .url("${ZaiConfig.BASE_URL}${ZaiConfig.CHATS_PATH}/$chatId")
            .delete()
            .build()
        okHttpClient.newCall(http).execute().use { it.isSuccessful }
    }

    /** Never throws — model list failure falls back to the config default. */
    suspend fun getAvailableModels(): List<String> = withContext(Dispatchers.IO) {
        val http = Request.Builder()
            .url(ZaiConfig.BASE_URL + ZaiConfig.MODELS_PATH)
            .get()
            .build()
        try {
            okHttpClient.newCall(http).execute().use { response ->
                if (!response.isSuccessful) return@withContext listOf(ZaiConfig.MODEL_DEFAULT)
                val body = response.body?.string()
                    ?: return@withContext listOf(ZaiConfig.MODEL_DEFAULT)
                json.decodeFromString<ModelsListResponse>(body).data.map { it.id }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            listOf(ZaiConfig.MODEL_DEFAULT)
        }
    }

    /** [RECON] Field name "file" per Open WebUI c


file10

package com.zai.chat.di

import com.zai.chat.config.ZaiConfig
import com.zai.chat.network.auth.AuthInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @OptIn(ExperimentalSerializationApi::class)
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true      // server adds fields → we never break
        isLenient = true              // tolerate loose JSON in SSE lines
        coerceInputValues = true      // nulls into defaults instead of crashes
        encodeDefaults = true         // stream:true etc. always on the wire
        explicitNulls = false         // never send "file_ids":null
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
        // BASIC level: logs request line + status only. It does NOT buffer
        // bodies, so SSE streaming is unaffected and chunks stay un-logged.
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(ZaiConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(ZaiConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)   // gap between bytes, fits SSE
            .writeTimeout(ZaiConfig.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)   // connect-phase retries; mid-stream failures surface as Error events
            .build()
    }
}

Three deliberate fixes over the PDF's version (worth knowing)
Lazy execution. The PDF's streamChatCompletion() ran call.execute() outside the flow builder — meaning the network call fired the moment the method was invoked, on the calling thread. Ours wraps everything in flow { } + flowOn(IO): nothing happens until collected, and nothing ever touches Main.
Instant Stop button. invokeOnCompletion { call.cancel() } aborts the socket the moment the collector cancels. Without this, a blocking readUtf8Line() would hang until the 120s read timeout after you hit stop — the PDF had this latency bug.
CancellationException passthrough. Both catch blocks rethrow it explicitly. Accidentally converting cancellation into an Error event is a classic coroutine bug that manifests as ghost "connection interrupted" banners — prevented at the source.
Intentionally deferred (with their landing phase): HMAC request signing (P12, SIGN_SALT extension point already reserved), cookie-string auth mode (P12), SSE fixture-based unit tests (P12 — needs the real captures to be worth writing).

🚀 Run it
bash

git add -A && git commit -m "P2: DTOs, auth interceptor, manual SSE engine, API service, network DI" && git push
gh run watch
Green → P2 done. gh run download -n debug-apk optional (nothing visible changed).
Red → gh run view --log-failed | tail -50, paste here; it'll almost certainly be a missing import or a stray character from nano.
Next: Phase 3 — Room persistence (entities, FTS4, DAOs, database, DI), which finally gives the app a local brain, and unblocks the repository layer where streaming meets storage.
