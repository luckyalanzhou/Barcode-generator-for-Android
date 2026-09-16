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
    // Compose 迁移基础：阶段一只启用编译能力，现有 XML 页面保持不变。
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.dynamicanimation:dynamicanimation:1.1.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("com.google.dagger:hilt-android:2.57.1")
    ksp("com.google.dagger:hilt-android-compiler:2.57.1")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("org.nanohttpd:nanohttpd-websocket:2.3.1")
    ksp("androidx.room:room-compiler:2.6.1")
}
