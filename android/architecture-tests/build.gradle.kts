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
}
