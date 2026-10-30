package com.luckyalanzhou.barcodegenerator.architecture

import org.junit.Assert.assertNull
import org.junit.Test

internal fun workflowConcurrencyBlock(workflow: String): String? {
    val header = Regex("(?m)^concurrency:\\r?$").find(workflow) ?: return null
    val jobs = Regex("(?m)^jobs:\\r?$").find(workflow, header.range.last + 1) ?: return null
    return workflow.substring(header.range.last + 1, jobs.range.first)
}

class WorkflowConcurrencyBlockTest {
    @Test
    fun commentedConcurrencyValuesCannotReplaceMissingTopLevelSection() {
        val workflow = listOf(
            "name: CI",
            "# concurrency:",
            "#   group: android-testing-\${{ github.ref }}",
            "#   queue: single",
            "#   cancel-in-progress: false",
            "jobs:",
            "  build:",
            "    steps: []",
        ).joinToString("\n")

        assertNull(workflowConcurrencyBlock(workflow))
    }
}
