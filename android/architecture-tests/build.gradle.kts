plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.konsist)
}

tasks.test {
    useJUnit()
    // Konsist 从 Android Gradle 根目录启动，才能扫描全部生产模块。
    workingDir(rootProject.projectDir)
    // 静态契约会读取主源码及渠道专属源码；全部声明为输入，避免变体源码变化时复用旧结果。
    inputs.files(rootProject.fileTree(rootProject.projectDir) {
        include(
            "app/src/main/**/*.kt",
            "app/src/beta/**/*.kt",
            "app/src/official/**/*.kt",
            "core/domain/src/main/**/*.kt",
            "core/data/src/main/**/*.kt",
            "core/lan-share/src/main/**/*.kt",
        )
    })
    inputs.files(
        rootProject.file("../.github/workflows/build-android-official.yml"),
        rootProject.file("../.github/workflows/build-android-testing.yml"),
        rootProject.file("gradle/wrapper/gradle-wrapper.jar"),
        rootProject.file("gradle/wrapper/gradle-wrapper.properties"),
    )
}
