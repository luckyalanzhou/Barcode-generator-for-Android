plugins {
    id("com.android.test")
}

android {
    namespace = "com.luckyalanzhou.barcodegenerator.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        missingDimensionStrategy("channel", "beta")
    }

    targetProjectPath = ":app"

}

dependencies {
    implementation(libs.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
}
