package com.luckyalanzhou.barcodegenerator.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** 正式版手动触发不得因并发组默认单个等待位而丢弃排队中的发布。 */
class OfficialReleaseWorkflowQueueContractTest {
    @Test
    fun officialReleaseChecksOutSourceBeforeInvokingReleaseScripts() {
        val workflow = File("../.github/workflows/build-android-official.yml").readText()
        val checkoutAction = "uses: actions/checkout@d23441a48e516b6c34aea4fa41551a30e30af803"
        val checkoutIndex = workflow.indexOf(checkoutAction)
        val versionScriptIndex = workflow.indexOf("python3 tools/official_release_version.py")

        assertTrue("正式版发布必须从仓库源码 checkout", checkoutIndex >= 0)
        assertTrue("源码 checkout 必须早于正式版仓库脚本", versionScriptIndex > checkoutIndex)
        assertTrue("正式版脚本必须在 Android 仓库目录执行", workflow.contains("working-directory: android"))

        val checkoutStepStart = workflow.lastIndexOf("      - name:", checkoutIndex)
        val checkoutStepEnd = workflow.indexOf("\n      - name:", checkoutIndex)
            .takeIf { it >= 0 } ?: workflow.length
        val checkoutStep = workflow.substring(checkoutStepStart, checkoutStepEnd)
        assertTrue("正式版 checkout 不应持久化 Git 凭据", checkoutStep.contains("persist-credentials: false"))
    }

    @Test
    fun officialReleaseWorkflowQueuesManualRunsInsteadOfReplacingPendingRun() {
        val workflow = File("../.github/workflows/build-android-official.yml").readText()
        val concurrency = workflowConcurrencyBlock(workflow)
            ?: throw AssertionError("正式版 workflow 必须声明顶层 concurrency 区域")

        assertTrue("正式版需保持固定并发组", concurrency.contains("group: android-official-release"))
        assertTrue("正式版须排队保留多个手动发布，不应由新 pending run 替换旧 run", concurrency.contains("queue: max"))
        assertTrue("排队期间不得取消正在执行的正式发布", concurrency.contains("cancel-in-progress: false"))
    }
}
