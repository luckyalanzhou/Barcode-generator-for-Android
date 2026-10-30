package com.luckyalanzhou.barcodegenerator.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 确保完整验证门禁由本地运行，远端发布流程只负责构建和发布校验。 */
class LocalVerificationContractTest {
    @Test
    fun `verifyLocal covers release validation without packaging apk`() {
        val buildScript = File("build.gradle.kts").readText()
        val taskDeclaration = "tasks.register(\"verifyLocal\") {"
        val taskStart = buildScript.indexOf(taskDeclaration)
        assertTrue("根构建脚本必须声明 verifyLocal 任务", taskStart >= 0)
        assertTrue(
            "根构建脚本不能重复声明 verifyLocal 任务",
            buildScript.indexOf(taskDeclaration, taskStart + taskDeclaration.length) < 0,
        )
        val taskEnd = buildScript.indexOf("\n}", taskStart + taskDeclaration.length)
        assertTrue("verifyLocal 任务必须有完整结束边界", taskEnd >= 0)
        val localVerification = buildScript.substring(taskStart, taskEnd)

        listOf(
            ":architecture-tests:test",
            ":core:domain:test",
            ":core:data:testDebugUnitTest",
            ":core:lan-share:testDebugUnitTest",
            ":app:testBetaDebugUnitTest",
            ":app:compileBetaReleaseKotlin",
            ":app:compileOfficialReleaseKotlin",
            ":app:lintBetaRelease",
            ":app:lintOfficialRelease",
        ).forEach { task ->
            assertTrue("verifyLocal 缺少 $task", localVerification.contains(task))
        }
        assertFalse("本地验证任务不得组装或发布 APK", localVerification.contains("assemble"))
    }

    @Test
    fun `release workflows do not rerun the complete local gate`() {
        listOf(
            "../.github/workflows/build-android-testing.yml",
            "../.github/workflows/build-android-official.yml",
        ).forEach { path ->
            assertFalse(
                "$path 不应重复运行本地 verifyLocal 门禁",
                File(path).readText().contains("verifyLocal"),
            )
        }
    }

    @Test
    fun `files read by the contract are declared as test inputs`() {
        val testBuild = File("architecture-tests/build.gradle.kts").readText()
        listOf(
            "rootProject.file(\"build.gradle.kts\")",
            "rootProject.file(\"../.github/workflows/build-android-official.yml\")",
            "rootProject.file(\"../.github/workflows/build-android-testing.yml\")",
        ).forEach { inputDeclaration ->
            assertTrue(
                "架构契约读取的文件必须声明为测试输入：$inputDeclaration",
                testBuild.contains(inputDeclaration),
            )
        }
    }
}
