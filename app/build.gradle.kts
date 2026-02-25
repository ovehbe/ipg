import java.util.zip.ZipFile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.meowgi.iconpackgenerator"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.meowgi.iconpackgenerator"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    androidResources {
        noCompress += "onnx"
    }
}

// Extract packviewer DEX into assets so generated icon packs have a real viewer Activity
tasks.register("extractViewerDex") {
    dependsOn(":packviewer:assembleRelease")
    val packviewerApk = project(":packviewer").layout.buildDirectory
        .file("outputs/apk/release/packviewer-release-unsigned.apk")
    val outputDex = layout.projectDirectory.file("src/main/assets/viewer_classes.dex")

    inputs.file(packviewerApk)
    outputs.file(outputDex)

    doLast {
        val apkFile = packviewerApk.get().asFile
        if (!apkFile.exists()) {
            throw GradleException("Packviewer APK not found: $apkFile")
        }
        val zipFile = ZipFile(apkFile)
        val dexEntry = zipFile.getEntry("classes.dex")
            ?: throw GradleException("No classes.dex in packviewer APK")
        val dexBytes = zipFile.getInputStream(dexEntry).readBytes()
        outputDex.asFile.writeBytes(dexBytes)
        zipFile.close()
        println("Extracted viewer DEX: ${outputDex.asFile.length()} bytes")
    }
}

tasks.named("preBuild") {
    dependsOn("extractViewerDex")
}

dependencies {
    // ARSCLib - APK/resource creation (replaces aapt2)
    implementation("io.github.reandroid:ARSCLib:1.3.5")

    // APK signing (replaces apksigner + zipalign)
    implementation("com.android.tools.build:apksig:8.7.3")

    // ONNX Runtime for U2-Net background removal
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.23.0")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // AndroidX
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
