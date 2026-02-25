package com.meowgi.iconpackgenerator.domain

import android.graphics.Color

enum class IconPackStyle {
    WHITE,
    BLACK,
    CUSTOM
}

data class IconPackConfig(
    val style: IconPackStyle,
    val color: Int = Color.WHITE,
    val dryRun: Boolean = false
) {
    val packageName: String
        get() = when (style) {
            IconPackStyle.WHITE -> "com.meowgi.ipg.white"
            IconPackStyle.BLACK -> "com.meowgi.ipg.black"
            IconPackStyle.CUSTOM -> {
                val hex = String.format("%06x", color and 0xFFFFFF)
                "com.meowgi.ipg.c_$hex"
            }
        }

    val packLabel: String
        get() = when (style) {
            IconPackStyle.WHITE -> "IPG White Icons"
            IconPackStyle.BLACK -> "IPG Black Icons"
            IconPackStyle.CUSTOM -> {
                val hex = String.format("#%06X", color and 0xFFFFFF)
                "IPG Custom ($hex)"
            }
        }
}
