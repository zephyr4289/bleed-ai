package com.zai.chat.network.transport

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Component 3: Speculative Pre-Warming Token Buffer.
 * Maintains an immediate-dispatch ticket pool by speculating on user typing behavior.
 * Enforces a strict 75-second TTL threshold to avoid backend token invalidation (Code F019).
 */
@Singleton
class CaptchaTokenPool @Inject constructor(
    private val signer: WebViewSigner
) {
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private data class CachedTicket(val token: String, val timestampMs: Long)

    private val poolMutex = Mutex()
    private var warmTicket: CachedTicket? = null

    // 75-second hard cap (Aliyun V3 invalidates on backend at 90s)
    private val TTL_MS = 75_000L

    fun onUserComposing() {
        scope.launch {
            prefetch()
        }
    }

    private suspend fun prefetch() = poolMutex.withLock {
        val now = System.currentTimeMillis()
        if (warmTicket != null && (now - warmTicket!!.timestampMs) < TTL_MS) {
            return@withLock
        }
        Log.d("BleedAI-TokenPool", "Speculatively minting ticket during composition...")
        val fresh = signer.mint()
        if (fresh.isNotEmpty()) {
            warmTicket = CachedTicket(fresh, now)
            Log.d("BleedAI-TokenPool", "Speculative ticket ready: ${fresh.take(16)}...")
        }
    }

    suspend fun acquireToken(): String = poolMutex.withLock {
        val now = System.currentTimeMillis()
        val current = warmTicket
        if (current != null && (now - current.timestampMs) < TTL_MS) {
            Log.i("BleedAI-TokenPool", "Cache Hit. Consuming pre-minted ticket (Age: ${now - current.timestampMs}ms)")
            warmTicket = null // Consume single-use ticket
            // Speculatively replenish for multi-turn conversations
            scope.launch { prefetch() }
            return current.token
        }

        Log.w("BleedAI-TokenPool", "Cache Miss or Expired. Performing on-demand mint...")
        return signer.mint()
    }
}
