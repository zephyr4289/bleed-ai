package com.zai.chat.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted, reactive storage for the session JWT.
 *
 * - Disk: AES256-GCM via Android Keystore master key. Token never exists
 *   in plaintext on disk.
 * - Memory: StateFlow lets any layer observe auth state changes live
 *   (e.g., UI showing "reconnecting..." when token becomes null).
 *
 * Token acquisition order:
 *   1. P5: WebView reconnect dialog saves here
 *   2. P12: manual paste fallback (debug)
 */
@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "zai_secure_token_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _tokenFlow = MutableStateFlow<String?>(getStoredToken())
    val tokenFlow: StateFlow<String?> = _tokenFlow.asStateFlow()

    fun getStoredToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun saveToken(token: String) {
        val trimmed = token.trim().trim('"', '\'')
        prefs.edit().putString(KEY_TOKEN, trimmed).apply()
        _tokenFlow.value = trimmed
    }

    fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
        _tokenFlow.value = null
    }

    companion object {
        private const val KEY_TOKEN = "jwt_bearer_token"
    }
}
