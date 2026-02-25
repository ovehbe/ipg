package com.meowgi.iconpackgenerator.scanner

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import com.meowgi.iconpackgenerator.domain.AppInfo

class AppScanner(private val context: Context) {

    fun scanInstalledApps(): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos: List<ResolveInfo> = context.packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_ALL)

        val ownPackage = context.packageName

        return resolveInfos
            .filter { it.activityInfo != null }
            .filter { it.activityInfo.packageName != ownPackage }
            .mapNotNull { resolveInfo ->
                try {
                    val activityInfo = resolveInfo.activityInfo
                    AppInfo(
                        packageName = activityInfo.packageName,
                        activityName = activityInfo.name,
                        label = resolveInfo.loadLabel(context.packageManager).toString()
                    )
                } catch (_: Exception) {
                    null
                }
            }
            .distinctBy { "${it.packageName}/${it.activityName}" }
            .sortedBy { it.label.lowercase() }
    }
}
