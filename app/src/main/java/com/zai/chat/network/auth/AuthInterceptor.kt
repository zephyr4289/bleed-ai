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

        builder.header("Accept-Language", "en-US")

        // P1 design: empty key = header disabled
        if (ZaiConfig.FE_VERSION_HEADER_KEY.isNotEmpty()) {
            builder.header(
                ZaiConfig.FE_VERSION_HEADER_KEY,
                ZaiConfig.FE_VERSION_HEADER_VALUE
            )
        }

        ZaiConfig.SIGN_SALT?.let { salt ->
            val timestamp = System.currentTimeMillis()
            val window = timestamp / (5 * 60 * 1000)
            try {
                val mac1 = javax.crypto.Mac.getInstance("HmacSHA256")
                mac1.init(javax.crypto.spec.SecretKeySpec(salt.toByteArray(Charsets.UTF_8), "HmacSHA256"))
                val round1Key = mac1.doFinal(window.toString().toByteArray(Charsets.UTF_8))

                val mac2 = javax.crypto.Mac.getInstance("HmacSHA256")
                mac2.init(javax.crypto.spec.SecretKeySpec(round1Key, "HmacSHA256"))
                val signatureBytes = mac2.doFinal(timestamp.toString().toByteArray(Charsets.UTF_8))
                val signature = signatureBytes.joinToString("") { "%02x".format(it) }
                builder.header("X-Signature", signature)
            } catch (_: Exception) {
                // Fallback gracefully if crypto provider error occurs
            }
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
