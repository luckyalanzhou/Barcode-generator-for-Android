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
            "Wrapper 必须固定到项目约定的 Gradle 9.8.2 binary distribution",
            wrapper.contains("distributionUrl=https\\://services.gradle.org/distributions/gradle-9.8.2-bin.zip"),
        )
        assertTrue(
            "Gradle 9.8.2 binary distribution 必须匹配官方 SHA-256",
            wrapper.contains("distributionSha256Sum=21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607"),
        )

        val wrapperJarDigest = MessageDigest.getInstance("SHA-256")
            .digest(File("gradle/wrapper/gradle-wrapper.jar").readBytes())
            .joinToString("") { byte -> "%02x".format(byte) }
        assertTrue(
            "Wrapper JAR 必须与配置的 Gradle 9.8.2 版本一致",
            wrapperJarDigest == "3b8a25775a69158b5ad2b1d17a80a88dc7b40a352a73eb27d06c612a7ce68e98",
        )
    }
}
