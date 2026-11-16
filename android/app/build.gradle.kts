import java.io.File
import java.util.Properties

val suppliedVersionCode = providers.gradleProperty("versionCode").orNull?.toIntOrNull()
val suppliedVersionName = providers.gradleProperty("versionName").orNull
val enableAppUnitTests = providers.gradleProperty("enableAppUnitTests")
    .map(String::toBoolean)
    .getOrElse(false)
val betaVersionProperties = Properties().apply {
    val versionFile = rootProject.file("beta-version.properties")
    if (versionFile.isFile) versionFile.inputStream().use(::load)
}
val localBetaVersionCode = betaVersionProperties.getProperty("versionCode")?.toIntOrNull()?.takeIf { it > 0 }
val localBetaVersionName = betaVersionProperties.getProperty("versionName")

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("com.google.android.gms.oss-licenses-plugin")
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.luckyalanzhou.barcodegenerator"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.luckyalanzhou.barcodegenerator"
        minSdk = 26
        targetSdk = 37
        versionCode = suppliedVersionCode ?: 11
        versionName = suppliedVersionName ?: "1.0.10"    }

    flavorDimensions += "channel"
    productFlavors {
        create("official") {
            dimension = "channel"
            applicationId = "com.luckyalanzhou.barcodegenerator"
            manifestPlaceholders["appLabel"] = "@string/app_name_release"
            buildConfigField("String", "UPDATE_TAG_PREFIX", "\"android-v\"")
            buildConfigField("String", "APK_FILE_PREFIX", "\"BarcodeGeneratorOfficial\"")
            buildConfigField("String", "BACKUP_FILE_NAME", "\"barcode-generator-backup-official.zip\"")
            buildConfigField("Boolean", "DEBUG_LOG_EXPORT", "false")
        }
        create("beta") {
            dimension = "channel"
            // 本地 beta 构建也以 beta-version.properties 为版本基线；CI 传入参数时保持由 CI 控制。
            if (suppliedVersionCode == null) versionCode = localBetaVersionCode
            if (suppliedVersionName == null) versionName = localBetaVersionName
            applicationId = "com.luckyalanzhou.barcodegenerator.test"
            manifestPlaceholders["appLabel"] = "@string/app_name_beta"
            buildConfigField("String", "UPDATE_TAG_PREFIX", "\"android-test-v\"")
            buildConfigField("String", "APK_FILE_PREFIX", "\"BarcodeGeneratorBeta\"")
            buildConfigField("String", "BACKUP_FILE_NAME", "\"barcode-generator-backup-beta.zip\"")
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
            manifestPlaceholders["appLabel"] = "@string/app_name_debug"
            // Temporary local profiling build: remove debugger overhead while retaining Debug identity.
            isDebuggable = false
            buildConfigField("Boolean", "PERFETTO_DRAW_TRACE", "true")
        }
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = false
            buildConfigField("Boolean", "PERFETTO_DRAW_TRACE", "false")
        }
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperties["robolectric.pixelCopyRenderMode"] = "hardware"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents {
    onVariants { variant ->
        val apkName = when (variant.name) {
            "officialRelease" -> "BarcodeGenerator-Official.apk"
            "betaRelease" -> "BarcodeGenerator-Beta.apk"
            "betaDebug" -> "BarcodeGenerator-Debug.apk"
            else -> null
        }
        if (apkName != null) {
            variant.outputs.forEach { output ->
                output.outputFileName.set(apkName)
            }
        }

        if (variant.name == "betaDebug") {
            // betaDebug 独立于 beta Release 安装，方便同时保留 Beta 与 Debug 应用。
            variant.applicationId.set("com.luckyalanzhou.barcodegenerator.debug")
        }
    }

    beforeVariants { variantBuilder ->
        if (variantBuilder.buildType == "debug" && !enableAppUnitTests) {
            // 默认不生成 officialDebug/betaDebug；显式开启应用单测时才创建测试所需的 debug 变体。
            variantBuilder.enable = false
        }
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:lan-share"))
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.compose.ui)
    implementation(libs.compose.animation.core)
    implementation(libs.compose.material3)
    implementation(libs.kyant.backdrop)
    implementation(libs.kyant.shapes)
    implementation(libs.play.services.oss.licenses)
    testImplementation(libs.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    "debugImplementation"(libs.compose.ui.test.manifest)
    "debugImplementation"(libs.compose.ui.tooling)
    "debugImplementation"(libs.compose.ui.tooling.preview)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.compose.preview.scanner.support)
    testImplementation(libs.compose.preview.scanner.android)
    implementation(libs.zxing.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.activity.ktx)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}

@OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
roborazzi {
    // 保留截图基准和差异图在本机专用目录，避免把本机截图推到远端。
    outputDir.set(rootProject.file("../.local-validation/ui-previews/roborazzi"))
    compare {
        outputDir.set(rootProject.file("../.local-validation/ui-previews/compare"))
    }
    generateComposePreviewRobolectricTests {
        enable = true
        packages = listOf("com.luckyalanzhou.barcodegenerator.ui.preview")
        robolectricConfig = mapOf("sdk" to "[35]")
        includePrivatePreviews = true
    }
}
