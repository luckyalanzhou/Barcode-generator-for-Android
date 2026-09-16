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
    // 本地构建可通过 LOCAL_AUTO_VERSION=true 启用独立版本计数器。
    // 计数文件在 D 盘，GitHub Actions 不设置该变量，因此不会影响远程版本号。
    val localAutoVersionEnabled = System.getenv("LOCAL_AUTO_VERSION") == "true"
    val localVersionFilePath = System.getenv("LOCAL_VERSION_FILE")
    val localBuildTask = gradle.startParameter.taskNames.any { task ->
        task.contains("assemble", ignoreCase = true) || task.contains("bundle", ignoreCase = true)
    }
    val localVersionCode = if (localAutoVersionEnabled && localBuildTask && !providers.gradleProperty("versionCode").isPresent) {
        val versionFile = localVersionFilePath?.let { path -> File(path) }
        if (versionFile != null) {
            val fallback = System.getenv("LOCAL_VERSION_BASE")?.toIntOrNull() ?: 33
            val current = versionFile.takeIf { it.isFile }?.readText()?.trim()?.toIntOrNull() ?: fallback
            val next = current + 1
            versionFile.parentFile?.mkdirs()
            versionFile.writeText(next.toString())
            next
        } else {
            null
        }
    } else {
        null
    }
    val buildVersionCode = providers.gradleProperty("versionCode").orNull?.toIntOrNull() ?: localVersionCode ?: 6
    val buildVersionName = providers.gradleProperty("versionName").orNull ?: localVersionCode?.let { "1.0.${it}-local" } ?: "1.0.5"

    defaultConfig {
        applicationId = "com.luckyalanzhou.barcodegenerator"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "1.0.10"
        if (providers.gradleProperty("versionCode").isPresent || localVersionCode != null) versionCode = buildVersionCode
        if (providers.gradleProperty("versionName").isPresent || localVersionCode != null) versionName = buildVersionName
    }

    flavorDimensions += "channel"
    productFlavors {
        create("local") {
            dimension = "channel"
            applicationId = "com.luckyalanzhou.barcodegenerator.debug"
            manifestPlaceholders["appLabel"] = "@string/app_name_debug"
            // 本地包不参与 GitHub 更新通道，避免误匹配 Beta/Release 发布记录。
            buildConfigField("String", "UPDATE_TAG_PREFIX", "\"__local__\"")
            buildConfigField("String", "APK_FILE_PREFIX", "\"BarcodeGeneratorDebug\"")
            buildConfigField("Boolean", "DEBUG_LOG_EXPORT", "false")
        }
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
            // localDebug 使用 Android 默认 Debug 签名，便于电脑本地快速安装调试。
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
