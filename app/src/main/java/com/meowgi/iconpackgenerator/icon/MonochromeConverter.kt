package com.meowgi.iconpackgenerator.icon

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect

/**
 * Converts a background-removed icon into a two-tone monochrome silhouette.
 *
 * Pipeline:
 * 1. All non-transparent pixels form the foreground (bg already removed by U2-Net)
 * 2. Otsu's method splits foreground into two luminance groups
 *    - Primary (larger group) → full opacity target color
 *    - Secondary (smaller group) → reduced opacity for subtle detail
 * 3. Result: clean two-tone silhouette with visible internal structure
 */
class MonochromeConverter {

    companion object {
        const val OUTPUT_SIZE = 192
        private const val PADDING_FRACTION = 0.16f
        private const val ALPHA_THRESHOLD = 25

        // Opacity for the secondary (lighter) foreground region.
        private const val SECONDARY_ALPHA = 80

        // If the two luminance groups are closer than this, skip the split
        // and render everything at full opacity (icon is essentially one color).
        private const val MIN_LUMINANCE_GAP = 30
    }

    fun convert(source: Bitmap, targetColor: Int): Bitmap {
        val output = Bitmap.createBitmap(OUTPUT_SIZE, OUTPUT_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val padding = (OUTPUT_SIZE * PADDING_FRACTION).toInt()
        val innerSize = OUTPUT_SIZE - padding * 2

        val scaled = Bitmap.createScaledBitmap(source, innerSize, innerSize, true)
        val pixels = IntArray(innerSize * innerSize)
        scaled.getPixels(pixels, 0, innerSize, 0, 0, innerSize, innerSize)

        val alphaMap = buildAlphaMap(pixels)

        val tR = Color.red(targetColor)
        val tG = Color.green(targetColor)
        val tB = Color.blue(targetColor)

        for (i in pixels.indices) {
            val a = alphaMap[i]
            pixels[i] = if (a > 0) Color.argb(a, tR, tG, tB) else Color.TRANSPARENT
        }

        scaled.setPixels(pixels, 0, innerSize, 0, 0, innerSize, innerSize)
        canvas.drawBitmap(
            scaled,
            Rect(0, 0, innerSize, innerSize),
            Rect(padding, padding, padding + innerSize, padding + innerSize),
            Paint(Paint.FILTER_BITMAP_FLAG)
        )

        if (scaled !== source) scaled.recycle()
        return output
    }

    private fun buildAlphaMap(pixels: IntArray): IntArray {
        val alphaMap = IntArray(pixels.size)

        val fgIndices = mutableListOf<Int>()
        val fgLuminance = mutableListOf<Int>()

        for (i in pixels.indices) {
            if (Color.alpha(pixels[i]) > ALPHA_THRESHOLD) {
                fgIndices.add(i)
                val r = Color.red(pixels[i])
                val g = Color.green(pixels[i])
                val b = Color.blue(pixels[i])
                fgLuminance.add((0.299 * r + 0.587 * g + 0.114 * b).toInt())
            }
        }

        if (fgIndices.isEmpty()) return alphaMap

        val threshold = otsuThreshold(fgLuminance)

        var sumLow = 0L; var countLow = 0
        var sumHigh = 0L; var countHigh = 0
        for (lum in fgLuminance) {
            if (lum <= threshold) { sumLow += lum; countLow++ }
            else { sumHigh += lum; countHigh++ }
        }

        val meanLow = if (countLow > 0) (sumLow / countLow).toInt() else 0
        val meanHigh = if (countHigh > 0) (sumHigh / countHigh).toInt() else 255
        val gap = meanHigh - meanLow

        if (gap < MIN_LUMINANCE_GAP || countLow == 0 || countHigh == 0) {
            for (idx in fgIndices) alphaMap[idx] = 255
            return alphaMap
        }

        val primaryIsLow = countLow >= countHigh

        for (j in fgIndices.indices) {
            val idx = fgIndices[j]
            val lum = fgLuminance[j]
            val isLowGroup = lum <= threshold

            // Respect original alpha for antialiased edges
            val srcAlpha = Color.alpha(pixels[idx])
            val baseAlpha = if (isLowGroup == primaryIsLow) 255 else SECONDARY_ALPHA
            alphaMap[idx] = (baseAlpha * srcAlpha / 255).coerceIn(0, 255)
        }

        return alphaMap
    }

    private fun otsuThreshold(luminances: List<Int>): Int {
        val histogram = IntArray(256)
        for (lum in luminances) histogram[lum.coerceIn(0, 255)]++

        val total = luminances.size
        var sumAll = 0L
        for (i in 0..255) sumAll += i.toLong() * histogram[i]

        var sumBg = 0L
        var weightBg = 0
        var bestThreshold = 0
        var bestVariance = -1.0

        for (t in 0..255) {
            weightBg += histogram[t]
            if (weightBg == 0) continue
            val weightFg = total - weightBg
            if (weightFg == 0) break

            sumBg += t.toLong() * histogram[t]
            val meanBg = sumBg.toDouble() / weightBg
            val meanFg = (sumAll - sumBg).toDouble() / weightFg

            val variance = weightBg.toDouble() * weightFg * (meanBg - meanFg) * (meanBg - meanFg)
            if (variance > bestVariance) {
                bestVariance = variance
                bestThreshold = t
            }
        }

        return bestThreshold
    }
}
