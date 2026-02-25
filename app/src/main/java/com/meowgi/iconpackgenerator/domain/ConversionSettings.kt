package com.meowgi.iconpackgenerator.domain

import android.content.Context

data class ConversionSettings(
    val bgKeepThreshold: Float = 0.15f,
    val bgCutThreshold: Float = 0.05f,
    val secondaryAlpha: Int = 80,
    val minLuminanceGap: Int = 30,
    val iconPadding: Float = 0.16f,
    val useFullIcon: Boolean = false
) {
    companion object {
        private const val PREFS_NAME = "conversion_settings"

        fun load(context: Context): ConversionSettings {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return ConversionSettings(
                bgKeepThreshold = prefs.getFloat("bg_keep", 0.15f),
                bgCutThreshold = prefs.getFloat("bg_cut", 0.05f),
                secondaryAlpha = prefs.getInt("secondary_alpha", 80),
                minLuminanceGap = prefs.getInt("min_lum_gap", 30),
                iconPadding = prefs.getFloat("icon_padding", 0.16f),
                useFullIcon = prefs.getBoolean("use_full_icon", false)
            )
        }

        fun save(context: Context, settings: ConversionSettings) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putFloat("bg_keep", settings.bgKeepThreshold)
                .putFloat("bg_cut", settings.bgCutThreshold)
                .putInt("secondary_alpha", settings.secondaryAlpha)
                .putInt("min_lum_gap", settings.minLuminanceGap)
                .putFloat("icon_padding", settings.iconPadding)
                .putBoolean("use_full_icon", settings.useFullIcon)
                .apply()
        }
    }
}
