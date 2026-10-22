package com.luckyalanzhou.barcodegenerator.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 确保 PR 门禁执行发布前编译与 Lint 检查，但不打包 APK。 */
class PullRequestReleaseValidationContractTest {
    @Test
    fun pullRequestRunsFullLocalVerificationWithoutPackagingApk() {
        val workflow = File("../.github/workflows/architecture-checks.yml").readText()
        val verificationStep = workflow.substringAfter("run: >-").substringBefore("\n      - name:")
        val localVerification = File("build.gradle.kts").readText()
            .substringAfter("tasks.register(\"verifyLocal\")")
            .substringBefore("\n}")

        assertTrue("PR 门禁必须调用统一本地验证入口", verificationStep.contains("verifyLocal"))
        assertTrue("PR 门禁必须显式启用应用测试变体", verificationStep.contains("-PenableAppUnitTests=true"))
        listOf(
            ":app:compileBetaReleaseKotlin",
            ":app:compileOfficialReleaseKotlin",
            ":app:lintBetaRelease",
            ":app:lintOfficialRelease",
        ).forEach { task ->
            assertTrue("verifyLocal 缺少 $task", localVerification.contains(task))
        }
        assertFalse("PR 门禁不得组装或发布 APK", verificationStep.contains("assemble"))
        assertFalse("统一本地验证任务不得组装或发布 APK", localVerification.contains("assemble"))
    }
}
