package com.meowgi.iconpackgenerator.builder

import android.content.Context
import android.graphics.Bitmap
import android.os.StatFs
import com.meowgi.iconpackgenerator.domain.AppInfo
import com.meowgi.iconpackgenerator.domain.GenerationProgress
import com.meowgi.iconpackgenerator.domain.GenerationProgress.Phase
import com.meowgi.iconpackgenerator.domain.GenerationResult
import com.meowgi.iconpackgenerator.domain.IconPackConfig
import com.meowgi.iconpackgenerator.icon.IconExtractor
import com.meowgi.iconpackgenerator.icon.MonochromeConverter
import com.meowgi.iconpackgenerator.scanner.AppScanner
import com.meowgi.iconpackgenerator.util.ResourceNameSanitizer
import com.meowgi.iconpackgenerator.util.VersionTracker
import java.io.ByteArrayOutputStream
import java.io.File

class IconPackBuilder(private val context: Context) {

    private val scanner = AppScanner(context)
    private val extractor = IconExtractor(context)
    private val converter = MonochromeConverter()
    private val assembler = ApkAssembler()
    private val signer = ApkSigner(KeyStoreManager(context.filesDir))
    private val versionTracker = VersionTracker(context)

    companion object {
        private const val MIN_FREE_BYTES = 50L * 1024 * 1024 // 50 MB
        private const val BATCH_SIZE = 50
    }

    fun build(
        config: IconPackConfig,
        onProgress: (GenerationProgress) -> Unit,
        isCancelled: () -> Boolean
    ): GenerationResult {
        val logs = mutableListOf<String>()

        try {
            // Check storage
            val stat = StatFs(context.cacheDir.absolutePath)
            val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
            if (freeBytes < MIN_FREE_BYTES) {
                return GenerationResult(
                    success = false,
                    errorMessage = "Insufficient storage: ${freeBytes / 1024 / 1024}MB free, need at least 50MB"
                )
            }

            // Phase: Scanning
            onProgress(GenerationProgress(Phase.SCANNING, message = "Scanning installed apps..."))
            val apps = scanner.scanInstalledApps()
            logs.add("Found ${apps.size} launcher apps")

            if (apps.isEmpty()) {
                return GenerationResult(
                    success = false,
                    errorMessage = "No launcher apps found",
                    logs = logs
                )
            }

            // Prepare output directories
            val workDir = File(context.cacheDir, "ipg_work").apply {
                deleteRecursively()
                mkdirs()
            }
            val iconsDir = File(workDir, "icons").apply { mkdirs() }

            // Phase: Extracting + Converting
            val iconEntries = mutableListOf<ApkAssembler.IconEntry>()
            val mappings = mutableListOf<Pair<AppInfo, String>>()
            var failedCount = 0

            val rawNames = apps.map { ResourceNameSanitizer.sanitize(it.packageName, it.activityName) }
            val resourceNames = ResourceNameSanitizer.deduplicateNames(rawNames)

            for ((index, app) in apps.withIndex()) {
                if (isCancelled()) {
                    logs.add("Cancelled by user")
                    return GenerationResult(success = false, errorMessage = "Cancelled", logs = logs)
                }

                val resourceName = resourceNames[index]

                onProgress(
                    GenerationProgress(
                        Phase.EXTRACTING,
                        current = index + 1,
                        total = apps.size,
                        message = "Processing: ${app.label}"
                    )
                )

                try {
                    val rawBitmap = extractor.extractIcon(app)
                    val monoBitmap = converter.convert(rawBitmap, config.color)

                    val pngBytes = bitmapToPng(monoBitmap)
                    monoBitmap.recycle()
                    if (rawBitmap !== monoBitmap) rawBitmap.recycle()

                    if (config.dryRun) {
                        // Save to disk for preview
                        File(iconsDir, "$resourceName.png").writeBytes(pngBytes)
                    }

                    iconEntries.add(
                        ApkAssembler.IconEntry(
                            resourceName = resourceName,
                            pngBytes = pngBytes,
                            componentInfo = app.componentInfoString
                        )
                    )
                    mappings.add(app to resourceName)
                } catch (e: Exception) {
                    failedCount++
                    logs.add("Failed to process ${app.packageName}: ${e.message}")
                }

                // Yield periodically to allow GC
                if (index % BATCH_SIZE == 0) {
                    System.gc()
                }
            }

            logs.add("Processed ${iconEntries.size} icons, $failedCount failures")

            if (config.dryRun) {
                return GenerationResult(
                    success = true,
                    iconCount = iconEntries.size,
                    failedCount = failedCount,
                    logs = logs
                )
            }

            // Phase: Building APK
            onProgress(GenerationProgress(Phase.BUILDING_APK, message = "Building icon pack APK..."))

            val appFilterXml = AppFilterBuilder.buildXml(mappings)
            val drawableXml = AppFilterBuilder.buildDrawableXml(mappings)

            val versionCode = versionTracker.getAndIncrement(config.packageName)
            val assemblyConfig = ApkAssembler.AssemblyConfig(
                packageName = config.packageName,
                packLabel = config.packLabel,
                versionCode = versionCode,
                versionName = "1.0.$versionCode"
            )

            val unsignedApk = File(workDir, "unsigned.apk")
            assembler.assemble(assemblyConfig, iconEntries, appFilterXml, drawableXml, unsignedApk)
            logs.add("APK assembled: ${unsignedApk.length() / 1024}KB")

            // Phase: Signing
            onProgress(GenerationProgress(Phase.SIGNING, message = "Signing APK..."))

            val apksDir = File(context.cacheDir, "generated_apks").apply { mkdirs() }
            val signedApk = File(apksDir, "${config.packageName}.apk")
            signer.sign(unsignedApk, signedApk)
            logs.add("APK signed: ${signedApk.length() / 1024}KB")

            // Cleanup work dir
            unsignedApk.delete()

            onProgress(GenerationProgress(Phase.DONE, message = "Icon pack ready!"))

            return GenerationResult(
                success = true,
                apkFile = signedApk,
                packageName = config.packageName,
                iconCount = iconEntries.size,
                failedCount = failedCount,
                logs = logs
            )
        } catch (e: Exception) {
            logs.add("Fatal error: ${e.message}")
            return GenerationResult(
                success = false,
                errorMessage = e.message ?: "Unknown error",
                logs = logs
            )
        }
    }

    private fun bitmapToPng(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }
}
