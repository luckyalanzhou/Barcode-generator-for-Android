package com.luckyalanzhou.barcodegenerator.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** 确保 Beta 重跑可将当前 Actions run 身份交给版本分配器。 */
class BetaWorkflowVersionResumeContractTest {
    @Test
    fun betaReleaseLookupPassesCurrentWorkflowRunIdToVersionAllocator() {
        val workflow = File("../.github/workflows/build-android-testing.yml").readText()
        val releaseLookup = workflow.substringAfter("python3 tools/beta_release_version.py --properties")
            .substringBefore("version_name=")

        assertTrue(
            "查询已有 Beta Release 时必须传入 GITHUB_RUN_ID，以便同 run 重用已发布的 versionCode",
            releaseLookup.contains("--run-id \"\$GITHUB_RUN_ID\""),
        )
    }
}
