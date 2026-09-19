import java.io.File
import java.util.Properties

val suppliedVersionCode = providers.gradleProperty("versionCode").orNull?.toIntOrNull()
val suppliedVersionName = providers.gradleProperty("versionName").orNull
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
}

android {
    namespace = "com.luckyalanzhou.barcodegenerator"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.luckyalanzhou.barcodegenerator"
        minSdk = 26
        targetSdk = 37
        versionCode = suppliedVersionCode ?: 11
        versionName = suppliedVersionName ?: "1.0.10"
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
            // 本地 beta 构建也以 beta-version.properties 为版本基线；CI 传入参数时保持由 CI 控制。
            if (suppliedVersionCode == null) versionCode = localBetaVersionCode
            if (suppliedVersionName == null) versionName = localBetaVersionName
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
            // Only local installs opt into a separate package; CI and released builds keep the original ID.
            if (providers.gradleProperty("localDebugPackageSuffix").orNull == "true") {
                applicationIdSuffix = ".debug"
            }
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
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
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
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.dynamicanimation)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.activity.ktx)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
