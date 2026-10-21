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
    // Konsist's project scope must start at the Android Gradle root so it sees every production module.
    workingDir(rootProject.projectDir)
    // Konsist reads production source directly from the repository; declare those files as inputs
    // so Gradle reruns architecture checks after app or core source changes instead of reusing stale results.
    inputs.files(rootProject.fileTree(rootProject.projectDir) {
        include(
            "app/src/main/**/*.kt",
            "core/domain/src/main/**/*.kt",
            "core/data/src/main/**/*.kt",
            "core/lan-share/src/main/**/*.kt",
        )
    })
}
