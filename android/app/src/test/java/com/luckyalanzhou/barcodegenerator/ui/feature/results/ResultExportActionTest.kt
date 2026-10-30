package com.luckyalanzhou.barcodegenerator.ui.feature.results

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultExportActionTest {
    @Test
    fun asynchronousSaveBlocksOtherExportsUntilCompletion() {
        var active: ResultExportAction? = null
        var finishSave: (() -> Unit)? = null
        var shareStarts = 0

        assertTrue(
            startExclusiveResultExport(active, ResultExportAction.Save, { active = it }) { finish ->
                finishSave = finish
            },
        )
        assertEquals(ResultExportAction.Save, active)

        assertFalse(
            startExclusiveResultExport(active, ResultExportAction.Share, { active = it }) {
                shareStarts += 1
            },
        )
        assertEquals(0, shareStarts)

        val finish = requireNotNull(finishSave)
        finish()
        assertNull(active)

        assertTrue(
            startExclusiveResultExport(active, ResultExportAction.Share, { active = it }) {
                shareStarts += 1
                it()
            },
        )
        assertEquals(1, shareStarts)
        assertNull(active)
    }

    @Test
    fun repeatedCompletionDoesNotClearTheNextExport() {
        var active: ResultExportAction? = null
        var finishFirst: (() -> Unit)? = null

        assertTrue(
            startExclusiveResultExport(active, ResultExportAction.Save, { active = it }) { finish ->
                finishFirst = finish
            },
        )
        val staleFinish = requireNotNull(finishFirst)
        staleFinish()

        assertTrue(
            startExclusiveResultExport(active, ResultExportAction.Share, { active = it }) { finish ->
                active = ResultExportAction.Share
                staleFinish()
                assertEquals(ResultExportAction.Share, active)
                finish()
            },
        )
        assertNull(active)
    }

    @Test
    fun synchronousLaunchFailureReleasesTheBusyStateAndPropagates() {
        var active: ResultExportAction? = null

        assertThrows(IllegalStateException::class.java) {
            startExclusiveResultExport(active, ResultExportAction.Save, { active = it }) {
                error("launch failed")
            }
        }

        assertNull(active)
    }
}
