package com.meowgi.iconpackgenerator.icon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.meowgi.iconpackgenerator.domain.AppInfo

class IconExtractor(private val context: Context) {

    companion object {
        const val ICON_SIZE = 192
    }

    var useFullIcon = false

    fun extractIcon(appInfo: AppInfo): Bitmap {
        return try {
            val drawable = loadBestDrawable(appInfo)
            renderToBitmap(drawable)
        } catch (_: Exception) {
            createFallbackIcon()
        }
    }

    private fun loadBestDrawable(appInfo: AppInfo): Drawable {
        val pm = context.packageManager

        // Try activity-specific icon first
        try {
            val activityInfo = pm.getActivityInfo(
                ComponentName(appInfo.packageName, appInfo.activityName), 0
            )
            if (activityInfo.icon != 0) {
                val drawable = activityInfo.loadIcon(pm)
                if (drawable != null) return drawable
            }
        } catch (_: PackageManager.NameNotFoundException) {
            // fall through
        }

        // Fall back to application icon
        try {
            val appIcon = pm.getApplicationIcon(appInfo.packageName)
            return appIcon
        } catch (_: PackageManager.NameNotFoundException) {
            // fall through
        }

        return context.packageManager.defaultActivityIcon
    }

    private fun renderToBitmap(drawable: Drawable): Bitmap {
        if (drawable is AdaptiveIconDrawable && !useFullIcon) {
            return renderAdaptiveIcon(drawable)
        }

        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            val src = drawable.bitmap
            if (src.width == ICON_SIZE && src.height == ICON_SIZE) {
                return src.copy(Bitmap.Config.ARGB_8888, false)
            }
            return Bitmap.createScaledBitmap(src, ICON_SIZE, ICON_SIZE, true)
        }

        // VectorDrawable or other drawable types
        return renderGenericDrawable(drawable)
    }

    private fun renderAdaptiveIcon(drawable: AdaptiveIconDrawable): Bitmap {
        val foreground = drawable.foreground ?: return renderGenericDrawable(drawable)

        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Adaptive icons have 72dp content in 108dp total (inner 66.7%)
        // Render foreground centered with appropriate inset
        val inset = (ICON_SIZE * 0.167f).toInt() // ~18% on each side
        foreground.setBounds(
            -inset, -inset,
            ICON_SIZE + inset, ICON_SIZE + inset
        )
        foreground.draw(canvas)

        return bitmap
    }

    private fun renderGenericDrawable(drawable: Drawable): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, ICON_SIZE, ICON_SIZE)
        drawable.draw(canvas)
        return bitmap
    }

    private fun createFallbackIcon(): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.GRAY
            style = android.graphics.Paint.Style.FILL
        }
        val cx = ICON_SIZE / 2f
        canvas.drawCircle(cx, cx, cx * 0.7f, paint)
        return bitmap
    }
}
