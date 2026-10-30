package com.luckyalanzhou.barcodegenerator.ui.feature.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 历史卡片打开、长按编辑与行内删除互不串触发。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HistoryPageInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun emptyHistoryIsNotShownUntilInitialLoadCompletes() {
        val isReady = mutableStateOf(false)

        compose.setContent {
            AppTheme("light") {
                HistoryComposePage(
                    entries = emptyList(),
                    refreshGeneration = 0L,
                    dark = false,
                    onOpen = {},
                    onEdit = {},
                    onDelete = {},
                    timeText = { "12:00" },
                    isReady = isReady.value,
                )
            }
        }

        compose.onNodeWithText("正在加载历史记录…").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("暂无历史记录").fetchSemanticsNodes().isEmpty())

        compose.runOnIdle { isReady.value = true }
        compose.onNodeWithText("暂无历史记录").assertIsDisplayed()
    }

    @Test
    fun failedHistoryLoadOffersRetryWithoutShowingEmptyState() {
        var retryCount = 0
        compose.setContent {
            AppTheme("light") {
                HistoryComposePage(
                    entries = emptyList(),
                    refreshGeneration = 0L,
                    dark = false,
                    onOpen = {},
                    onEdit = {},
                    onDelete = {},
                    timeText = { "12:00" },
                    isReady = false,
                    loadFailed = true,
                    onRetry = { retryCount++ },
                )
            }
        }

        compose.onNodeWithText("历史记录加载失败").assertIsDisplayed()
        compose.onNodeWithText("重试").performClick()
        assertTrue(compose.onAllNodesWithText("暂无历史记录").fetchSemanticsNodes().isEmpty())
        assertEquals(1, retryCount)
    }

    @Test
    fun refreshGenerationReturnsScrolledHistoryListToTheTop() {
        val refreshGeneration = mutableLongStateOf(0L)
        val entries = (1..40).map { index ->
            index.toLong() to listOf(CodeItem(index.toLong(), "ROW-$index", "Code 128-B", index.toLong()))
        }

        compose.setContent {
            AppTheme("light") {
                HistoryComposePage(
                    entries = entries,
                    refreshGeneration = refreshGeneration.longValue,
                    dark = false,
                    onOpen = {},
                    onEdit = {},
                    onDelete = {},
                    timeText = { "12:00" },
                )
            }
        }

        compose.onNodeWithText("ROW-1").assertIsDisplayed()
        repeat(5) { compose.onRoot().performTouchInput { swipeUp() } }
        assertTrue(compose.onAllNodesWithText("ROW-1").fetchSemanticsNodes().isEmpty())

        compose.runOnIdle { refreshGeneration.longValue++ }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("ROW-1").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("ROW-1").assertIsDisplayed()
    }

    @Test
    fun cardClickLongPressAndDeleteUseIndependentCallbacks() {
        val actions = mutableListOf<Pair<String, List<String>>>()
        val unsortedBatch = listOf(
            CodeItem(2, "SECOND", "Code 128-B", 2L),
            CodeItem(1, "FIRST", "Code 128-B", 1L),
        )

        compose.setContent {
            AppTheme("light") {
                HistoryPage(
                    batch = unsortedBatch,
                    onOpen = { actions += "open" to it.map(CodeItem::text) },
                    onEdit = { actions += "edit" to it.map(CodeItem::text) },
                    onDelete = { actions += "delete" to it.map(CodeItem::text) },
                )
            }
        }

        compose.onNodeWithText("FIRST…").performClick()
        assertEquals(listOf("open" to listOf("FIRST", "SECOND")), actions)

        compose.onNodeWithText("FIRST…").performTouchInput {
            down(center)
            advanceEventTime(1_000)
            up()
        }
        assertEquals(
            listOf(
                "open" to listOf("FIRST", "SECOND"),
                "edit" to listOf("FIRST", "SECOND"),
            ),
            actions,
        )

        compose.onNodeWithContentDescription("删除这条历史记录").performClick()
        assertEquals(
            listOf(
                "open" to listOf("FIRST", "SECOND"),
                "edit" to listOf("FIRST", "SECOND"),
                "delete" to listOf("FIRST", "SECOND"),
            ),
            actions,
        )
    }
}

@Composable
private fun HistoryPage(
    batch: List<CodeItem>,
    onOpen: (List<CodeItem>) -> Unit,
    onEdit: (List<CodeItem>) -> Unit,
    onDelete: (List<CodeItem>) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        HistoryComposePage(
            entries = listOf(1L to batch),
            refreshGeneration = 0L,
            dark = false,
            onOpen = onOpen,
            onEdit = onEdit,
            onDelete = onDelete,
            timeText = { "12:00" },
        )
    }
}
