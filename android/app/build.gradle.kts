import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.luckyalanzhou.barcodegenerator"
    compileSdk = 35
    val buildVersionCode = providers.gradleProperty("versionCode").orNull?.toIntOrNull() ?: 6
    val buildVersionName = providers.gradleProperty("versionName").orNull ?: "1.0.5"
    defaultConfig {
        applicationId = "com.luckyalanzhou.barcodegenerator"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "1.0.10"
        if (providers.gradleProperty("versionCode").isPresent) versionCode = buildVersionCode
        if (providers.gradleProperty("versionName").isPresent) versionName = buildVersionName
    }

    flavorDimensions += "channel"
    productFlavors {
        create("official") {
            dimension = "channel"
            applicationId = "com.luckyalanzhou.barcodegenerator"
            manifestPlaceholders["appLabel"] = "@string/app_name_release"
            buildConfigField("String", "UPDATE_TAG_PREFIX", "\"android-v\"")
            buildConfigField("String", "APK_FILE_PREFIX", "\"BarcodeGenerator\"")
            buildConfigField("Boolean", "DEBUG_LOG_EXPORT", "false")
        }
        create("beta") {
            dimension = "channel"
            applicationId = "com.luckyalanzhou.barcodegenerator.test"
            manifestPlaceholders["appLabel"] = "@string/app_name_beta"
            buildConfigField("String", "UPDATE_TAG_PREFIX", "\"android-test-v\"")
            buildConfigField("String", "APK_FILE_PREFIX", "\"BarcodeGeneratorTest\"")
            buildConfigField("Boolean", "DEBUG_LOG_EXPORT", "true")
        }
    }

    signingConfigs {
        val keystoreFile = System.getenv("KEYSTORE_FILE")
        val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
        val keyAliasValue = System.getenv("KEY_ALIAS")
        val keyPasswordValue = System.getenv("KEY_PASSWORD")
        if (!keystoreFile.isNullOrBlank() && !keystorePassword.isNullOrBlank() && !keyAliasValue.isNullOrBlank() && !keyPasswordValue.isNullOrBlank()) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = keystorePassword
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        debug {
        }
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = false
        }
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    // Compose 迁移基础：阶段一只启用编译能力，现有 XML 页面保持不变。
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    implementation(libs.zxing.core)
    implementation(libs.mlkit.text.recognition.chinese)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.dynamicanimation)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.activity.ktx)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.nanohttpd)
    implementation(libs.nanohttpd.websocket)
}
