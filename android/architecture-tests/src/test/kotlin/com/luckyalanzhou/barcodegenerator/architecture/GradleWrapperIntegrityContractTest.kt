package com.luckyalanzhou.barcodegenerator.architecture

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gradle Wrapper 下载的固定发行包必须使用 Gradle 官方 SHA-256 校验。 */
class GradleWrapperIntegrityContractTest {
    @Test
    fun wrapperPinsOfficialChecksumForConfiguredDistribution() {
        val wrapper = File("gradle/wrapper/gradle-wrapper.properties").readText()

        assertTrue(
            "Wrapper 必须固定到项目约定的 Gradle 9.5.0 binary distribution",
            wrapper.contains("distributionUrl=https\\://services.gradle.org/distributions/gradle-9.5.0-bin.zip"),
        )
        assertTrue(
            "Gradle 9.5.0 binary distribution 必须匹配官方 SHA-256",
            wrapper.contains("distributionSha256Sum=553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746"),
        )

        val wrapperJarDigest = MessageDigest.getInstance("SHA-256")
            .digest(File("gradle/wrapper/gradle-wrapper.jar").readBytes())
            .joinToString("") { byte -> "%02x".format(byte) }
        assertTrue(
            "Wrapper JAR 必须与配置的 Gradle 9.5.0 版本一致",
            wrapperJarDigest == "497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7",
        )
    }
}
