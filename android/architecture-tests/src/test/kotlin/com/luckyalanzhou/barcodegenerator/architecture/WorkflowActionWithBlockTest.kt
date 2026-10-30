package com.luckyalanzhou.barcodegenerator.architecture

import org.junit.Assert.assertNull
import org.junit.Test

class WorkflowActionWithBlockTest {
    @Test
    fun missingActionCannotBorrowAnUnrelatedWithBlock() {
        val workflow = listOf(
            "name: regression",
            "jobs:",
            "  build:",
            "    steps:",
            "      - name: unrelated step",
            "        uses: actions/checkout@v4",
            "        with:",
            "          ref: \${{ github.run_number }}",
            "          overwrite: true",
            "      - name: finish",
            "        run: echo done",
        ).joinToString("\n")

        assertNull(workflowActionWithBlock(workflow, "actions/upload-artifact"))
    }
}
