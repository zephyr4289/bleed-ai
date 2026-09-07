package com.zai.chat.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zai.chat.config.ZaiConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

/**
 * Typed key-value app settings (theme, font, composer behavior).
 * Non-sensitive data only (tokens live in TokenManager).
 */
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode") // "OLED", "DARK", "LIGHT", "SYSTEM"
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val DEFAULT_MODEL = stringPreferencesKey("default_model")
        val ENTER_IS_SEND = booleanPreferencesKey("enter_is_send")
        val STREAMING_ENABLED = booleanPreferencesKey("streaming_enabled")
        val DEEP_THINKING = booleanPreferencesKey("deep_thinking_default")
    }

    val themeMode: Flow<String> = context.dataStore.data.map { it[Keys.THEME_MODE] ?: "OLED" }
    val fontScale: Flow<Float> = context.dataStore.data.map { it[Keys.FONT_SCALE] ?: 1.0f }
    val defaultModel: Flow<String> = context.dataStore.data.map { it[Keys.DEFAULT_MODEL] ?: ZaiConfig.MODEL_DEFAULT }
    val enterIsSend: Flow<Boolean> = context.dataStore.data.map { it[Keys.ENTER_IS_SEND] ?: false }
    val streamingEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.STREAMING_ENABLED] ?: true }
    val deepThinking: Flow<Boolean> = context.dataStore.data.map { it[Keys.DEEP_THINKING] ?: true }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    suspend fun setFontScale(scale: Float) {
        context.dataStore.edit { it[Keys.FONT_SCALE] = scale }
    }

    suspend fun setDefaultModel(model: String) {
        context.dataStore.edit { it[Keys.DEFAULT_MODEL] = model }
    }

    suspend fun setEnterIsSend(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ENTER_IS_SEND] = enabled }
    }

    suspend fun setStreamingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.STREAMING_ENABLED] = enabled }
    }

    suspend fun setDeepThinking(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DEEP_THINKING] = enabled }
    }
}
