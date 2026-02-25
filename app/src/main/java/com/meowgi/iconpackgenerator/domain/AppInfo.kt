package com.meowgi.iconpackgenerator.domain

import android.content.ComponentName

data class AppInfo(
    val packageName: String,
    val activityName: String,
    val label: String
) {
    val componentName: ComponentName
        get() = ComponentName(packageName, activityName)

    val componentInfoString: String
        get() = "ComponentInfo{$packageName/$activityName}"
}
