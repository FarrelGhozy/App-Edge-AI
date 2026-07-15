plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.facegate.kioskscanner"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.facegate.kioskscanner"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "API_BASE_URL", "\"https://facegate.utc.web.id\"")
        buildConfigField("String", "DEVICE_USERNAME", "\"kiosk-gate1\"")
        buildConfigField("String", "DEVICE_PASSWORD", "\"facegate-kiosk-2024\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"https://facegate.utc.web.id\"")
            buildConfigField("String", "DEVICE_USERNAME", "\"kiosk-gate1\"")
            buildConfigField("String", "DEVICE_PASSWORD", "\"facegate-kiosk-2024\"")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "API_BASE_URL", "\"https://facegate.utc.web.id\"")
            buildConfigField("String", "DEVICE_USERNAME", "\"kiosk-gate1\"")
            buildConfigField("String", "DEVICE_PASSWORD", "\"facegate-kiosk-2024\"")
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
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.foundation)

    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)

    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    implementation(libs.coil.compose)
    implementation(libs.material)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
}
