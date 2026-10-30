package com.luckyalanzhou.barcodegenerator.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Beta 稳定 Release 只保留最新等待构建，避免旧提交稍后覆盖新版本。 */
class BetaReleaseWorkflowQueueContractTest {
    @Test
    fun betaReleaseWorkflowKeepsOnlyTheLatestPendingRun() {
        val workflow = File("../.github/workflows/build-android-testing.yml").readText()
        val concurrency = workflowConcurrencyBlock(workflow)
            ?: throw AssertionError("Beta workflow 必须声明顶层 concurrency 区域")

        assertTrue("Beta workflow 需使用固定分支并发组", concurrency.contains("group: android-testing-\${{ github.ref }}"))
        assertTrue("Beta 稳定 Release 只保留最新等待构建", concurrency.contains("queue: single"))
        assertTrue("Beta 当前发布完成前不得取消运行中的构建", concurrency.contains("cancel-in-progress: false"))
    }
}
