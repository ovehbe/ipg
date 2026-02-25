package com.meowgi.iconpackgenerator.builder

import com.reandroid.apk.AndroidFrameworks
import com.reandroid.apk.ApkModule
import com.reandroid.archive.ByteInputSource
import com.reandroid.arsc.chunk.PackageBlock
import com.reandroid.arsc.chunk.TableBlock
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import com.reandroid.arsc.value.ValueType
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.Adler32
import java.util.zip.ZipEntry

class ApkAssembler {

    data class IconEntry(
        val resourceName: String,
        val pngBytes: ByteArray,
        val componentInfo: String
    )

    data class AssemblyConfig(
        val packageName: String,
        val packLabel: String,
        val versionCode: Int,
        val versionName: String
    )

    fun assemble(
        config: AssemblyConfig,
        icons: List<IconEntry>,
        appFilterXml: String,
        drawableXml: String,
        packIconPng: ByteArray?,
        viewerDex: ByteArray?,
        outputFile: File
    ) {
        val apkModule = ApkModule()

        val tableBlock = TableBlock()
        val manifest = AndroidManifestBlock()

        apkModule.setTableBlock(tableBlock)
        apkModule.setManifest(manifest)

        val framework = apkModule.initializeAndroidFramework(
            AndroidFrameworks.getLatest().versionCode
        )

        val pkg = tableBlock.newPackage(0x7f, config.packageName)

        addDrawableResources(apkModule, pkg, icons)

        // Add pack icon as a drawable resource
        var iconResId = 0
        if (packIconPng != null) {
            val iconPath = "res/drawable-nodpi-v4/ic_pack_icon.png"
            val iconEntry = pkg.getOrCreate("nodpi-v4", "drawable", "ic_pack_icon")
            iconEntry.setValueAsString(iconPath)
            apkModule.add(ByteInputSource(packIconPng, iconPath))
            iconResId = iconEntry.resourceId
        }

        configureManifest(manifest, config, framework, iconResId, viewerDex != null)

        // appfilter.xml in assets/ (plain text, widest launcher compatibility)
        apkModule.add(
            ByteInputSource(
                appFilterXml.toByteArray(Charsets.UTF_8),
                "assets/appfilter.xml"
            )
        )

        // drawable.xml in assets/ for icon picker
        apkModule.add(
            ByteInputSource(
                drawableXml.toByteArray(Charsets.UTF_8),
                "assets/drawable.xml"
            )
        )

        // Use viewer DEX (real Activity) if available, otherwise minimal DEX
        val dexBytes = viewerDex ?: buildMinimalDex()
        val dexSource = ByteInputSource(dexBytes, "classes.dex")
        dexSource.setMethod(ZipEntry.STORED)
        apkModule.add(dexSource)

        apkModule.writeApk(outputFile)
    }

    private fun addDrawableResources(
        apkModule: ApkModule,
        pkg: PackageBlock,
        icons: List<IconEntry>
    ) {
        for (entry in icons) {
            val filePath = "res/drawable-nodpi-v4/${entry.resourceName}.png"

            val resEntry = pkg.getOrCreate("nodpi-v4", "drawable", entry.resourceName)
            resEntry.setValueAsString(filePath)

            apkModule.add(ByteInputSource(entry.pngBytes, filePath))
        }
    }

    private fun configureManifest(
        manifest: AndroidManifestBlock,
        config: AssemblyConfig,
        framework: com.reandroid.apk.FrameworkApk?,
        iconResId: Int,
        hasViewerDex: Boolean
    ) {
        manifest.packageName = config.packageName
        manifest.setVersionCode(config.versionCode)
        manifest.versionName = config.versionName
        manifest.setMinSdkVersion(26)
        manifest.setTargetSdkVersion(28)

        if (framework != null) {
            manifest.setCompileSdkVersion(framework.versionCode)
            manifest.setCompileSdkVersionCodename(framework.versionName)
            manifest.setPlatformBuildVersionCode(framework.versionCode)
            manifest.setPlatformBuildVersionName(framework.versionName)
        }

        manifest.setApplicationLabel(config.packLabel)

        if (iconResId != 0) {
            manifest.setIconResourceId(iconResId)
        }

        val activityClass = if (hasViewerDex) {
            "com.meowgi.ipg.viewer.PackViewerActivity"
        } else {
            "android.app.Activity"
        }
        val activity = manifest.getOrCreateMainActivity(activityClass)
        activity.getOrCreateAndroidAttribute(
            "label", AndroidManifestBlock.ID_label
        ).setValueAsString(config.packLabel)

        activity.getOrCreateAndroidAttribute(
            "exported", AndroidManifestBlock.ID_exported
        ).setValueAsBoolean(true)

        // Add ADW theme intent-filter (standard icon pack detection)
        val adwFilter = activity.createChildElement("intent-filter")
        adwFilter.createChildElement("action")
            .getOrCreateAndroidAttribute("name", AndroidManifestBlock.ID_name)
            .setValueAsString("org.adw.ActivityStarter.THEMES")
        adwFilter.createChildElement("category")
            .getOrCreateAndroidAttribute("name", AndroidManifestBlock.ID_name)
            .setValueAsString("android.intent.category.DEFAULT")

        // Add Nova Launcher theme intent-filter
        val novaFilter = activity.createChildElement("intent-filter")
        novaFilter.createChildElement("action")
            .getOrCreateAndroidAttribute("name", AndroidManifestBlock.ID_name)
            .setValueAsString("com.novalauncher.THEME")
        novaFilter.createChildElement("category")
            .getOrCreateAndroidAttribute("name", AndroidManifestBlock.ID_name)
            .setValueAsString("android.intent.category.DEFAULT")

        // Add additional theme actions for broader launcher support
        val extraFilter = activity.createChildElement("intent-filter")
        for (action in EXTRA_THEME_ACTIONS) {
            extraFilter.createChildElement("action")
                .getOrCreateAndroidAttribute("name", AndroidManifestBlock.ID_name)
                .setValueAsString(action)
        }
        extraFilter.createChildElement("category")
            .getOrCreateAndroidAttribute("name", AndroidManifestBlock.ID_name)
            .setValueAsString("android.intent.category.DEFAULT")
    }

    companion object {
        private val EXTRA_THEME_ACTIONS = listOf(
            "com.anddoes.launcher.THEME",         // Apex Launcher
            "com.teslacoilsw.launcher.THEME",      // Nova (legacy)
            "org.adw.launcher.THEMES",             // ADW
            "com.gau.go.launcherex.theme",         // GO Launcher
        )

        /**
         * Builds a minimal valid DEX file (header-only, no classes).
         * The file has proper magic, checksum, and SHA-1 signature.
         */
        fun buildMinimalDex(): ByteArray {
            val headerSize = 112
            val buf = ByteBuffer.allocate(headerSize).order(ByteOrder.LITTLE_ENDIAN)

            // Magic: "dex\n035\0"
            buf.put(byteArrayOf(0x64, 0x65, 0x78, 0x0A, 0x30, 0x33, 0x35, 0x00))
            // Placeholder for checksum (4 bytes at offset 8)
            buf.putInt(0)
            // Placeholder for SHA-1 signature (20 bytes at offset 12)
            buf.put(ByteArray(20))
            // file_size
            buf.putInt(headerSize)
            // header_size
            buf.putInt(headerSize)
            // endian_tag
            buf.putInt(0x12345678)
            // link_size, link_off
            buf.putInt(0)
            buf.putInt(0)
            // map_off
            buf.putInt(0)
            // string_ids_size, string_ids_off
            buf.putInt(0)
            buf.putInt(0)
            // type_ids_size, type_ids_off
            buf.putInt(0)
            buf.putInt(0)
            // proto_ids_size, proto_ids_off
            buf.putInt(0)
            buf.putInt(0)
            // field_ids_size, field_ids_off
            buf.putInt(0)
            buf.putInt(0)
            // method_ids_size, method_ids_off
            buf.putInt(0)
            buf.putInt(0)
            // class_defs_size, class_defs_off
            buf.putInt(0)
            buf.putInt(0)
            // data_size, data_off
            buf.putInt(0)
            buf.putInt(0)

            val bytes = buf.array()

            // Compute SHA-1 over bytes 32..end
            val sha1 = MessageDigest.getInstance("SHA-1")
            sha1.update(bytes, 32, bytes.size - 32)
            val signature = sha1.digest()
            System.arraycopy(signature, 0, bytes, 12, 20)

            // Compute Adler32 over bytes 12..end
            val adler = Adler32()
            adler.update(bytes, 12, bytes.size - 12)
            val checksum = adler.value.toInt()
            bytes[8] = (checksum and 0xFF).toByte()
            bytes[9] = ((checksum shr 8) and 0xFF).toByte()
            bytes[10] = ((checksum shr 16) and 0xFF).toByte()
            bytes[11] = ((checksum shr 24) and 0xFF).toByte()

            return bytes
        }
    }
}
