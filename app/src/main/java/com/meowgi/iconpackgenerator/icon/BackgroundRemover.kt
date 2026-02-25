package com.meowgi.iconpackgenerator.icon

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import java.nio.FloatBuffer

/**
 * Uses the U2-Net-P (portable) ONNX model for salient object detection.
 * Produces a per-pixel alpha mask that separates the icon subject from its background.
 *
 * The model is 4.4MB, runs entirely on-device with no network dependency.
 */
class BackgroundRemover(context: Context) {

    companion object {
        private const val MODEL_FILE = "u2netp.onnx"
        private const val MODEL_INPUT_SIZE = 320
    }

    private val ortEnv = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        val modelBytes = context.assets.open(MODEL_FILE).use { it.readBytes() }
        val opts = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(2)
        }
        session = ortEnv.createSession(modelBytes, opts)
    }

    /**
     * Returns a new Bitmap with the background removed (made transparent).
     * The input bitmap is not modified.
     */
    fun removeBackground(input: Bitmap): Bitmap {
        val w = input.width
        val h = input.height

        // Scale input to model size
        val scaled = Bitmap.createScaledBitmap(input, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, true)
        val inputTensor = bitmapToTensor(scaled)
        if (scaled !== input) scaled.recycle()

        // Run inference
        val results = session.run(mapOf(session.inputNames.first() to inputTensor))
        inputTensor.close()

        // The first output is the primary saliency map: shape [1, 1, 320, 320]
        val outputTensor = results.first().value as OnnxTensor
        val rawOutput = outputTensor.floatBuffer

        // Convert to alpha mask at model resolution
        val maskSize = MODEL_INPUT_SIZE * MODEL_INPUT_SIZE
        val mask = FloatArray(maskSize)
        rawOutput.rewind()
        for (i in 0 until maskSize) {
            mask[i] = rawOutput.get()
        }
        results.close()

        // Normalize to 0..1
        normalize(mask)

        // Scale mask back to original bitmap size and apply
        return applyMask(input, mask, w, h)
    }

    fun close() {
        session.close()
    }

    /**
     * Converts a Bitmap to a [1, 3, 320, 320] float tensor, normalized to [0, 1]
     * with ImageNet mean/std normalization as U2-Net expects.
     */
    private fun bitmapToTensor(bitmap: Bitmap): OnnxTensor {
        val s = MODEL_INPUT_SIZE
        val pixels = IntArray(s * s)
        bitmap.getPixels(pixels, 0, s, 0, 0, s, s)

        // U2-Net normalization: (pixel/255 - mean) / std per channel
        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)

        val buf = FloatBuffer.allocate(3 * s * s)
        // CHW order: all R, then all G, then all B
        for (c in 0..2) {
            for (i in pixels.indices) {
                val channel = when (c) {
                    0 -> Color.red(pixels[i])
                    1 -> Color.green(pixels[i])
                    else -> Color.blue(pixels[i])
                }
                buf.put((channel / 255f - mean[c]) / std[c])
            }
        }
        buf.rewind()

        val shape = longArrayOf(1, 3, s.toLong(), s.toLong())
        return OnnxTensor.createTensor(ortEnv, buf, shape)
    }

    /** Normalize values in-place to [0, 1] range via min-max. */
    private fun normalize(arr: FloatArray) {
        var min = Float.MAX_VALUE
        var max = Float.MIN_VALUE
        for (v in arr) {
            if (v < min) min = v
            if (v > max) max = v
        }
        val range = max - min
        if (range < 1e-6f) return
        for (i in arr.indices) {
            arr[i] = ((arr[i] - min) / range).coerceIn(0f, 1f)
        }
    }

    /**
     * Apply the 320x320 saliency mask to the original-size bitmap.
     * Bilinear interpolation scales the mask to match input dimensions.
     */
    private fun applyMask(original: Bitmap, mask: FloatArray, w: Int, h: Int): Bitmap {
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val srcPixels = IntArray(w * h)
        original.getPixels(srcPixels, 0, w, 0, 0, w, h)

        val ms = MODEL_INPUT_SIZE
        val outPixels = IntArray(w * h)

        for (y in 0 until h) {
            for (x in 0 until w) {
                // Map output coords to mask coords (bilinear)
                val mx = x.toFloat() * (ms - 1) / (w - 1).coerceAtLeast(1)
                val my = y.toFloat() * (ms - 1) / (h - 1).coerceAtLeast(1)

                val x0 = mx.toInt().coerceIn(0, ms - 1)
                val y0 = my.toInt().coerceIn(0, ms - 1)
                val x1 = (x0 + 1).coerceIn(0, ms - 1)
                val y1 = (y0 + 1).coerceIn(0, ms - 1)

                val fx = mx - x0
                val fy = my - y0

                val v = mask[y0 * ms + x0] * (1 - fx) * (1 - fy) +
                        mask[y0 * ms + x1] * fx * (1 - fy) +
                        mask[y1 * ms + x0] * (1 - fx) * fy +
                        mask[y1 * ms + x1] * fx * fy

                val srcPixel = srcPixels[y * w + x]
                val srcAlpha = Color.alpha(srcPixel)

                // Combine: mask confidence * original alpha
                val finalAlpha = (v * srcAlpha).toInt().coerceIn(0, 255)

                outPixels[y * w + x] = Color.argb(
                    finalAlpha,
                    Color.red(srcPixel),
                    Color.green(srcPixel),
                    Color.blue(srcPixel)
                )
            }
        }

        result.setPixels(outPixels, 0, w, 0, 0, w, h)
        return result
    }
}
