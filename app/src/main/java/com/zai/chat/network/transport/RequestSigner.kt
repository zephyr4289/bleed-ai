package com.zai.chat.network.transport

import android.util.Base64
import com.zai.chat.config.ZaiConfig
import java.net.URLEncoder
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

data class SignedRequestParams(
    val signature: String,
    val timestamp: String,
    val requestId: String,
    val queryParams: String
)

@Singleton
class RequestSigner @Inject constructor() {

    fun sign(
        prompt: String,
        userId: String,
        token: String,
        timestamp: Long = System.currentTimeMillis(),
        requestId: String = UUID.randomUUID().toString(),
        salt: String = ZaiConfig.SIGN_SALT ?: "key-@@@@)))()((9))-xxxx&&&%%%%%"
    ): SignedRequestParams {
        val tsStr = timestamp.toString()
        val window = timestamp / (5 * 60 * 1000)

        // 1. Compute round 1 key = HMAC_SHA256(salt, window)
        val mac1 = Mac.getInstance("HmacSHA256")
        mac1.init(SecretKeySpec(salt.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val round1Key = mac1.doFinal(window.toString().toByteArray(Charsets.UTF_8))

        // 2. Build sorted payload from { requestId, timestamp, user_id }
        val sortedPayload = "requestId,$requestId,timestamp,$tsStr,user_id,$userId"

        // 3. Base64 encode the trimmed prompt
        val base64Prompt = Base64.encodeToString(prompt.trim().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

        // 4. Build combined signature payload h = sortedPayload|base64Prompt|timestamp
        val h = "$sortedPayload|$base64Prompt|$tsStr"

        // 5. Final signature = HMAC_SHA256(round1Key, h) in lowercase hex
        val mac2 = Mac.getInstance("HmacSHA256")
        mac2.init(SecretKeySpec(round1Key, "HmacSHA256"))
        val sigBytes = mac2.doFinal(h.toByteArray(Charsets.UTF_8))
        val signature = sigBytes.joinToString("") { "%02x".format(it) }

        // 6. Build urlParams fingerprint query string
        val paramsMap = linkedMapOf(
            "timestamp" to tsStr,
            "requestId" to requestId,
            "user_id" to userId,
            "version" to "0.0.1",
            "platform" to "web",
            "token" to token,
            "user_agent" to ZaiConfig.USER_AGENT,
            "language" to "en-US",
            "languages" to "en-US,en",
            "timezone" to "Asia/Calcutta",
            "cookie_enabled" to "true",
            "screen_width" to "1920",
            "screen_height" to "1080",
            "screen_resolution" to "1920x1080",
            "viewport_height" to "900",
            "viewport_width" to "1440",
            "viewport_size" to "1440x900",
            "color_depth" to "24",
            "pixel_ratio" to "1",
            "current_url" to "${ZaiConfig.BASE_URL}/",
            "pathname" to "/",
            "search" to "",
            "hash" to "",
            "host" to "chat.z.ai",
            "hostname" to "chat.z.ai",
            "protocol" to "https:",
            "referrer" to "",
            "title" to "Z.AI - Free AI Chat",
            "timezone_offset" to "-330",
            "local_time" to java.time.Instant.ofEpochMilli(timestamp).toString(),
            "utc_time" to java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(
                java.time.ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(timestamp), java.time.ZoneId.of("UTC"))
            ),
            "is_mobile" to "false",
            "is_touch" to "false",
            "max_touch_points" to "0",
            "browser_name" to "Chrome",
            "os_name" to "Windows"
        )

        val queryParams = paramsMap.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }

        return SignedRequestParams(
            signature = signature,
            timestamp = tsStr,
            requestId = requestId,
            queryParams = queryParams
        )
    }
}
