package com.meowgi.iconpackgenerator.domain

import java.io.File

data class GenerationProgress(
    val phase: Phase,
    val current: Int = 0,
    val total: Int = 0,
    val message: String = ""
) {
    enum class Phase {
        IDLE,
        SCANNING,
        EXTRACTING,
        CONVERTING,
        BUILDING_APK,
        SIGNING,
        INSTALLING,
        DONE,
        ERROR
    }

    val fraction: Float
        get() = if (total > 0) current.toFloat() / total else 0f
}

data class GenerationResult(
    val success: Boolean,
    val apkFile: File? = null,
    val packageName: String = "",
    val iconCount: Int = 0,
    val failedCount: Int = 0,
    val errorMessage: String? = null,
    val logs: List<String> = emptyList()
)
