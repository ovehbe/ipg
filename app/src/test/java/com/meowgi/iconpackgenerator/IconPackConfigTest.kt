package com.meowgi.iconpackgenerator

import android.graphics.Color
import com.meowgi.iconpackgenerator.domain.IconPackConfig
import com.meowgi.iconpackgenerator.domain.IconPackStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class IconPackConfigTest {

    @Test
    fun `white style produces correct package name`() {
        val config = IconPackConfig(IconPackStyle.WHITE)
        assertEquals("com.meowgi.ipg.white", config.packageName)
    }

    @Test
    fun `black style produces correct package name`() {
        val config = IconPackConfig(IconPackStyle.BLACK)
        assertEquals("com.meowgi.ipg.black", config.packageName)
    }

    @Test
    fun `custom color produces hex package name`() {
        val config = IconPackConfig(
            IconPackStyle.CUSTOM,
            color = 0xFFFFCC00.toInt()
        )
        assertEquals("com.meowgi.ipg.c_ffcc00", config.packageName)
    }

    @Test
    fun `custom red produces correct package name`() {
        val config = IconPackConfig(
            IconPackStyle.CUSTOM,
            color = 0xFFFF0000.toInt()
        )
        assertEquals("com.meowgi.ipg.c_ff0000", config.packageName)
    }

    @Test
    fun `pack labels are human-readable`() {
        assertEquals("IPG White Icons", IconPackConfig(IconPackStyle.WHITE).packLabel)
        assertEquals("IPG Black Icons", IconPackConfig(IconPackStyle.BLACK).packLabel)
    }
}
