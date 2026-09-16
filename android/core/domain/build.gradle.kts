plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation("com.google.zxing:core:3.5.3")
    testImplementation("junit:junit:4.13.2")
}
