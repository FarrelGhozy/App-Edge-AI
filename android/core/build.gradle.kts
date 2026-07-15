plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.facegate.core"
    compileSdk = 35

    defaultConfig {
        minSdk = 31
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // Don't compress TFLite models in APK (they're already compressed)
    androidResources {
        noCompress += listOf("tflite")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api(libs.androidx.core.ktx)
    api(libs.androidx.lifecycle.runtime.ktx)

    api(libs.hilt.android)
    ksp(libs.hilt.compiler)

    api(libs.room.runtime)
    api(libs.room.ktx)
    ksp(libs.room.compiler)

    api(libs.retrofit)
    api(libs.okhttp)
    api(libs.okhttp.logging)
    api(libs.kotlinx.serialization.json)
    api(libs.retrofit.kotlinx.serialization)

    api(libs.tensorflow.lite)
    api(libs.mlkit.facedetection)

    api(libs.workmanager)
    api(libs.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
}
