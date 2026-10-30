package com.luckyalanzhou.barcodegenerator.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** 确保同一 Actions run 的失败恢复可重新上传固定名称的临时构建产物。 */
class ReleaseWorkflowArtifactRetryContractTest {
    @Test
    fun releaseWorkflowsAllowReplacingArtifactsWhenRerunningTheSameRun() {
        val workflows = listOf(
            "../.github/workflows/build-android-official.yml",
            "../.github/workflows/build-android-testing.yml",
        )

        workflows.forEach { path ->
            val workflow = File(path).readText()
            val uploadStep = workflowActionWithBlock(workflow, "actions/upload-artifact")
                ?: throw AssertionError("$path 必须包含 actions/upload-artifact 步骤")

            assertTrue("$path 的 artifact 名称需按 workflow run 区分", uploadStep.contains("github.run_number"))
            assertTrue("$path 重跑同一 run 时必须能替换已存在 artifact", uploadStep.contains("overwrite: true"))
        }
    }
}
