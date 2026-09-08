package com.zai.chat.config

/**
 * Centralized network and operational configuration for chat.z.ai (Open WebUI fork).
 *
 * ── SINGLE SOURCE OF TRUTH ──────────────────────────────────────────────
 * EVERY network constant lives here. When live recon (Phase 12) reveals
 * real values, ONLY this file changes. Nothing else in the app is touched.
 *
 * Placeholder values below are best-guess from the Open WebUI API surface.
 * They are marked [RECON] and MUST be replaced after live verification.
 * The app compiles and runs with placeholders; network calls will simply
 * fail until P12 swaps in verified values.
 * ────────────────────────────────────────────────────────────────────────
 */
object ZaiConfig {

    // ── Endpoints ────────────────────────────────────────────────────
    const val BASE_URL: String = "https://chat.z.ai"

    /** [RECON] Open WebUI fork default. Verify actual path in P12. */
    const val COMPLETIONS_PATH: String = "/api/chat/completions"

    /** [RECON] Open WebUI standard. High confidence. */
    const val CHATS_PATH: String = "/api/v1/chats"

    /** [RECON] Open WebUI standard. High confidence. */
    const val MODELS_PATH: String = "/api/models"

    /** [RECON] Open WebUI standard. High confidence. */
    const val UPLOAD_FILE_PATH: String = "/api/v1/files/"

    // ── Request identity headers ─────────────────────────────────────
    /** [RECON] Desktop Chrome UA. Copy the EXACT string your browser sends. */
    const val USER_AGENT: String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    const val ORIGIN: String = "https://chat.z.ai"
    const val REFERER: String = "https://chat.z.ai/"

    /**
     * Frontend version header captured from live bundle assets.
     */
    const val FE_VERSION_HEADER_KEY: String = "X-FE-Version"
    const val FE_VERSION_HEADER_VALUE: String = "prod-fe-1.1.93"

    /**
     * HMAC salt deobfuscated from live frontend bundle.
     */
    val SIGN_SALT: String? = "key-@@@@)))()((9))-xxxx&&&%%%%%"

    /**
     * Default model identifier from live frontend.
     */
    const val MODEL_DEFAULT: String = "GLM-5.3-Flash"

    // ── Timeouts ─────────────────────────────────────────────────────
    const val CONNECT_TIMEOUT_SECONDS: Long = 30L
    const val READ_TIMEOUT_SECONDS: Long = 120L   // long: SSE streams stay open
    const val WRITE_TIMEOUT_SECONDS: Long = 60L
}
