package com.luckyalanzhou.barcodegenerator.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** 正式版手动触发不得因并发组默认单个等待位而丢弃排队中的发布。 */
class OfficialReleaseWorkflowQueueContractTest {
    @Test
    fun officialReleaseWorkflowQueuesManualRunsInsteadOfReplacingPendingRun() {
        val workflow = File("../.github/workflows/build-android-official.yml").readText()
        val concurrency = workflow.substringAfter("\nconcurrency:\n").substringBefore("\njobs:")

        assertTrue("正式版需保持固定并发组", concurrency.contains("group: android-official-release"))
        assertTrue("正式版须排队保留多个手动发布，不应由新 pending run 替换旧 run", concurrency.contains("queue: max"))
        assertTrue("排队期间不得取消正在执行的正式发布", concurrency.contains("cancel-in-progress: false"))
    }
}
