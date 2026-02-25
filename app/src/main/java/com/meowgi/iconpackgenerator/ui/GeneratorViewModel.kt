package com.meowgi.iconpackgenerator.ui

import android.app.Application
import android.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.meowgi.iconpackgenerator.domain.GenerationProgress
import com.meowgi.iconpackgenerator.domain.IconPackConfig
import com.meowgi.iconpackgenerator.domain.IconPackStyle
import com.meowgi.iconpackgenerator.installer.PackageInstallerHelper
import com.meowgi.iconpackgenerator.worker.IconPackWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class UiState(
    val isGenerating: Boolean = false,
    val progress: GenerationProgress = GenerationProgress(GenerationProgress.Phase.IDLE),
    val lastResult: ResultInfo? = null,
    val canInstall: Boolean = true,
    val logs: List<String> = emptyList()
)

data class ResultInfo(
    val success: Boolean,
    val apkPath: String = "",
    val packageName: String = "",
    val iconCount: Int = 0,
    val failedCount: Int = 0,
    val errorMessage: String = "",
    val isDryRun: Boolean = false,
    val previewDir: String = ""
)

class GeneratorViewModel(application: Application) : AndroidViewModel(application) {

    private val workManager = WorkManager.getInstance(application)
    private val installer = PackageInstallerHelper(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    companion object {
        private const val WORK_NAME = "icon_pack_generation"
    }

    fun generate(style: IconPackStyle, customColor: Int = Color.WHITE, dryRun: Boolean = false) {
        val config = IconPackConfig(
            style = style,
            color = when (style) {
                IconPackStyle.WHITE -> Color.WHITE
                IconPackStyle.BLACK -> Color.BLACK
                IconPackStyle.CUSTOM -> customColor
            },
            dryRun = dryRun
        )

        val workRequest = OneTimeWorkRequestBuilder<IconPackWorker>()
            .setInputData(IconPackWorker.buildInputData(config))
            .build()

        _uiState.value = _uiState.value.copy(
            isGenerating = true,
            progress = GenerationProgress(GenerationProgress.Phase.SCANNING, message = "Starting..."),
            lastResult = null,
            logs = emptyList()
        )

        workManager.enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )

        workManager.getWorkInfoByIdLiveData(workRequest.id).observeForever { workInfo ->
            if (workInfo == null) return@observeForever

            when (workInfo.state) {
                WorkInfo.State.RUNNING -> {
                    val data = workInfo.progress
                    val phaseName = data.getString(IconPackWorker.KEY_PROGRESS_PHASE)
                    if (phaseName != null) {
                        _uiState.value = _uiState.value.copy(
                            progress = GenerationProgress(
                                phase = GenerationProgress.Phase.valueOf(phaseName),
                                current = data.getInt(IconPackWorker.KEY_PROGRESS_CURRENT, 0),
                                total = data.getInt(IconPackWorker.KEY_PROGRESS_TOTAL, 0),
                                message = data.getString(IconPackWorker.KEY_PROGRESS_MESSAGE) ?: ""
                            )
                        )
                    }
                }
                WorkInfo.State.SUCCEEDED -> {
                    val data = workInfo.outputData
                    val logs = data.getString(IconPackWorker.KEY_RESULT_LOGS)
                        ?.split("\n")
                        ?.filter { it.isNotBlank() }
                        ?: emptyList()

                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        progress = GenerationProgress(GenerationProgress.Phase.DONE, message = "Done!"),
                        lastResult = ResultInfo(
                            success = true,
                            apkPath = data.getString(IconPackWorker.KEY_RESULT_APK_PATH) ?: "",
                            packageName = data.getString(IconPackWorker.KEY_RESULT_PACKAGE) ?: "",
                            iconCount = data.getInt(IconPackWorker.KEY_RESULT_ICON_COUNT, 0),
                            failedCount = data.getInt(IconPackWorker.KEY_RESULT_FAILED_COUNT, 0),
                            isDryRun = data.getBoolean(IconPackWorker.KEY_RESULT_DRY_RUN, false),
                            previewDir = data.getString(IconPackWorker.KEY_RESULT_PREVIEW_DIR) ?: ""
                        ),
                        logs = logs
                    )
                }
                WorkInfo.State.FAILED -> {
                    val data = workInfo.outputData
                    val logs = data.getString(IconPackWorker.KEY_RESULT_LOGS)
                        ?.split("\n")
                        ?.filter { it.isNotBlank() }
                        ?: emptyList()

                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        progress = GenerationProgress(GenerationProgress.Phase.ERROR),
                        lastResult = ResultInfo(
                            success = false,
                            errorMessage = data.getString(IconPackWorker.KEY_RESULT_ERROR) ?: "Unknown error"
                        ),
                        logs = logs
                    )
                }
                WorkInfo.State.CANCELLED -> {
                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        progress = GenerationProgress(GenerationProgress.Phase.IDLE),
                        lastResult = ResultInfo(success = false, errorMessage = "Cancelled")
                    )
                }
                else -> {}
            }
        }
    }

    fun cancelGeneration() {
        workManager.cancelUniqueWork(WORK_NAME)
    }

    fun installLastApk() {
        val result = _uiState.value.lastResult ?: return
        if (!result.success || result.apkPath.isBlank()) return

        val apkFile = File(result.apkPath)
        if (!apkFile.exists()) return

        if (!installer.canInstallPackages()) {
            _uiState.value = _uiState.value.copy(canInstall = false)
            return
        }

        _uiState.value = _uiState.value.copy(
            progress = GenerationProgress(
                GenerationProgress.Phase.INSTALLING,
                message = "Installing..."
            )
        )

        installer.installApk(apkFile)
    }

    fun checkInstallPermission(): Boolean {
        val canInstall = installer.canInstallPackages()
        _uiState.value = _uiState.value.copy(canInstall = canInstall)
        return canInstall
    }

    fun getInstallPermissionIntent() = installer.getInstallPermissionIntent()
}
