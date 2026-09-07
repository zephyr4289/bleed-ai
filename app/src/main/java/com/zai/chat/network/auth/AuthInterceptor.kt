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
