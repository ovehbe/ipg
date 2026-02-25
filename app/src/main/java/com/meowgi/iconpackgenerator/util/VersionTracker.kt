package com.meowgi.iconpackgenerator.util

import android.content.Context

class VersionTracker(context: Context) {

    private val prefs = context.getSharedPreferences("ipg_versions", Context.MODE_PRIVATE)

    fun getAndIncrement(packageName: String): Int {
        val key = "version_$packageName"
        val current = prefs.getInt(key, 0) + 1
        prefs.edit().putInt(key, current).apply()
        return current
    }

    fun currentVersion(packageName: String): Int {
        return prefs.getInt("version_$packageName", 0)
    }
}
