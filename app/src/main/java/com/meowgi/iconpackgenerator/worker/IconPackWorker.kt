package com.meowgi.iconpackgenerator.worker

import android.content.Context
import android.graphics.Color
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.meowgi.iconpackgenerator.builder.IconPackBuilder
import com.meowgi.iconpackgenerator.domain.IconPackConfig
import com.meowgi.iconpackgenerator.domain.IconPackStyle
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class IconPackWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_STYLE = "style"
        const val KEY_COLOR = "color"
        const val KEY_DRY_RUN = "dry_run"

        const val KEY_PROGRESS_PHASE = "progress_phase"
        const val KEY_PROGRESS_CURRENT = "progress_current"
        const val KEY_PROGRESS_TOTAL = "progress_total"
        const val KEY_PROGRESS_MESSAGE = "progress_message"

        const val KEY_RESULT_SUCCESS = "result_success"
        const val KEY_RESULT_APK_PATH = "result_apk_path"
        const val KEY_RESULT_PACKAGE = "result_package"
        const val KEY_RESULT_ICON_COUNT = "result_icon_count"
        const val KEY_RESULT_FAILED_COUNT = "result_failed_count"
        const val KEY_RESULT_ERROR = "result_error"
        const val KEY_RESULT_LOGS = "result_logs"
        const val KEY_RESULT_DRY_RUN = "result_dry_run"
        const val KEY_RESULT_PREVIEW_DIR = "result_preview_dir"

        fun buildInputData(config: IconPackConfig): Data {
            return workDataOf(
                KEY_STYLE to config.style.name,
                KEY_COLOR to config.color,
                KEY_DRY_RUN to config.dryRun
            )
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val styleName = inputData.getString(KEY_STYLE) ?: return@withContext Result.failure()
        val color = inputData.getInt(KEY_COLOR, Color.WHITE)
        val dryRun = inputData.getBoolean(KEY_DRY_RUN, false)

        val config = IconPackConfig(
            style = IconPackStyle.valueOf(styleName),
            color = color,
            dryRun = dryRun
        )

        val builder = IconPackBuilder(applicationContext)

        val result = builder.build(
            config = config,
            onProgress = { progress ->
                setProgressAsync(
                    workDataOf(
                        KEY_PROGRESS_PHASE to progress.phase.name,
                        KEY_PROGRESS_CURRENT to progress.current,
                        KEY_PROGRESS_TOTAL to progress.total,
                        KEY_PROGRESS_MESSAGE to progress.message
                    )
                )
            },
            isCancelled = { isStopped }
        )

        if (result.success) {
            val previewDir = if (config.dryRun) {
                File(applicationContext.cacheDir, "ipg_work/icons").absolutePath
            } else ""

            val outputData = workDataOf(
                KEY_RESULT_SUCCESS to true,
                KEY_RESULT_APK_PATH to (result.apkFile?.absolutePath ?: ""),
                KEY_RESULT_PACKAGE to result.packageName,
                KEY_RESULT_ICON_COUNT to result.iconCount,
                KEY_RESULT_FAILED_COUNT to result.failedCount,
                KEY_RESULT_LOGS to result.logs.joinToString("\n"),
                KEY_RESULT_DRY_RUN to config.dryRun,
                KEY_RESULT_PREVIEW_DIR to previewDir
            )
            Result.success(outputData)
        } else {
            val outputData = workDataOf(
                KEY_RESULT_SUCCESS to false,
                KEY_RESULT_ERROR to (result.errorMessage ?: "Unknown error"),
                KEY_RESULT_LOGS to result.logs.joinToString("\n")
            )
            Result.failure(outputData)
        }
    }
}
