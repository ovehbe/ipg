package com.meowgi.iconpackgenerator.icon

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect

class MonochromeConverter {

    companion object {
        const val OUTPUT_SIZE = 192
        private const val PADDING_FRACTION = 0.16f
        private const val ALPHA_THRESHOLD = 20
    }

    fun convert(source: Bitmap, targetColor: Int): Bitmap {
        val output = Bitmap.createBitmap(OUTPUT_SIZE, OUTPUT_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val padding = (OUTPUT_SIZE * PADDING_FRACTION).toInt()
        val innerSize = OUTPUT_SIZE - padding * 2

        // Scale source into the inner region
        val scaled = Bitmap.createScaledBitmap(source, innerSize, innerSize, true)

        // Extract target RGB components
        val tR = Color.red(targetColor)
        val tG = Color.green(targetColor)
        val tB = Color.blue(targetColor)

        val pixels = IntArray(innerSize * innerSize)
        scaled.getPixels(pixels, 0, innerSize, 0, 0, innerSize, innerSize)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val alpha = Color.alpha(pixel)

            if (alpha > ALPHA_THRESHOLD) {
                // Use luminance to modulate alpha for more detail
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()

                // Darker source pixels -> more opaque output
                // Lighter source pixels -> less opaque output
                // But always capped by original alpha
                val maskAlpha = ((255 - luminance) * alpha / 255).coerceIn(0, 255)

                pixels[i] = if (maskAlpha > ALPHA_THRESHOLD) {
                    Color.argb(maskAlpha, tR, tG, tB)
                } else {
                    Color.TRANSPARENT
                }
            } else {
                pixels[i] = Color.TRANSPARENT
            }
        }

        scaled.setPixels(pixels, 0, innerSize, 0, 0, innerSize, innerSize)

        val destRect = Rect(padding, padding, padding + innerSize, padding + innerSize)
        val srcRect = Rect(0, 0, innerSize, innerSize)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(scaled, srcRect, destRect, paint)

        if (scaled !== source) {
            scaled.recycle()
        }

        return output
    }
}
