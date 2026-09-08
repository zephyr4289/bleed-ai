package com.zai.chat.network.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import com.zai.chat.config.ZaiConfig

object CustomTabAuthHelper {
    fun launch(context: Context) {
        val builder = CustomTabsIntent.Builder()
        // Match the application's true black OLED design system
        val colorScheme = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(0xFF000000.toInt())
            .setNavigationBarColor(0xFF000000.toInt())
            .build()
        builder.setDefaultColorSchemeParams(colorScheme)
        builder.setShowTitle(true)
        builder.setShareState(CustomTabsIntent.SHARE_STATE_OFF)

        val customTabsIntent = builder.build()
        customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            customTabsIntent.launchUrl(context, Uri.parse(ZaiConfig.BASE_URL))
        } catch (_: Exception) {
            // Direct browser fallback if no Custom Tabs provider is available
            val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(ZaiConfig.BASE_URL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }
}
