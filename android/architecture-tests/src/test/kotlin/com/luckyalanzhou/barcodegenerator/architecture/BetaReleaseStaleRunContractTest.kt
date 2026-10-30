package com.luckyalanzhou.barcodegenerator.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** 防止旧 Beta 工作流重跑覆盖当前 beta 分支对应的固定 Release。 */
class BetaReleaseStaleRunContractTest {
    @Test
    fun buildRejectsStaleCommitBeforeAllocatingVersion() {
        val workflow = File("../.github/workflows/build-android-testing.yml").readText()
        val guard = stepContaining(workflow, "refusing to build stale code")
        val versionStep = workflow.indexOf("python3 tools/beta_release_version.py --properties")

        assertTrue("过期提交校验必须早于版本分配", versionStep > guard.first)
        assertRejectsStaleCommit(guard.second)
    }

    @Test
    fun publishRejectsStaleCommitAfterArtifactDownloadAndBeforeReleaseMutation() {
        val workflow = File("../.github/workflows/build-android-testing.yml").readText()
        val downloadConfig = workflowActionWithBlock(workflow, "actions/download-artifact")
            ?: throw AssertionError("Beta 发布 job 必须包含真实的制品下载步骤")
        val download = workflow.indexOf("uses: actions/download-artifact@")
        val guard = stepContaining(workflow, "refusing to publish stale code")
        val publish = workflow.indexOf("python3 android/tools/beta_release_transaction.py")

        assertTrue("下载步骤必须选择 Beta 构建产物", downloadConfig.contains("BarcodeGeneratorBeta"))
        assertTrue("Beta 发布前校验依赖的制品下载 Action 必须存在", download >= 0)
        assertTrue("发布前校验必须在制品下载后", guard.first > download)
        assertTrue("发布前校验必须在修改固定 Release 前", publish > guard.first)
        assertRejectsStaleCommit(guard.second)
    }

    private fun assertRejectsStaleCommit(step: String) {
        assertTrue("必须读取 beta 分支当前提交", step.contains("/git/ref/heads/beta"))
        assertTrue("必须将远端提交与本次 GITHUB_SHA 比较", step.contains("!= \"\$GITHUB_SHA\""))
        assertTrue("发现旧提交时必须让工作流失败", step.contains("exit 1"))
    }

    private fun stepContaining(workflow: String, marker: String): Pair<Int, String> {
        val contentIndex = workflow.indexOf(marker)
        assertTrue("缺少包含契约内容的工作流步骤：$marker", contentIndex >= 0)
        val start = workflow.lastIndexOf("      - name:", contentIndex)
        assertTrue("契约内容必须位于具名工作流步骤内：$marker", start >= 0)
        val end = workflow.indexOf("\n      - name:", contentIndex)
            .takeIf { it >= 0 } ?: workflow.length
        return start to workflow.substring(start, end)
    }
}
