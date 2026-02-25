plugins {
    id("com.android.application")
}

android {
    namespace = "com.meowgi.ipg.viewer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.meowgi.ipg.viewer"
        minSdk = 26
        targetSdk = 35
    }

    lint {
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // No dependencies -- pure Android framework APIs only
}
