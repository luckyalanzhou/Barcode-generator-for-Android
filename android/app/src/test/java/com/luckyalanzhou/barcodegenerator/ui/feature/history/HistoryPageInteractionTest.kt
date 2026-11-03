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
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.GenerateBarcodesUseCase
import com.luckyalanzhou.barcodegenerator.ui.theme.AppTheme
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import android.content.Intent
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun separateGenerationBatchesWithTheSameMillisecondRemainSeparate() {
        val generator = GenerateBarcodesUseCase()
        val firstBatch = generator.execute(listOf("BATCH-A"), "Code 128-B", emptyList(), now = 42L).items
        val secondBatch = generator.execute(listOf("BATCH-B"), "Code 128-B", firstBatch, now = 42L).items
        val openedBatches = mutableListOf<List<String>>()

        compose.setContent {
            AppTheme("light") {
                HistoryScreen(
                    items = firstBatch + secondBatch,
                    refreshGeneration = 0L,
                    dark = false,
                    onOpen = { openedBatches += it.map(CodeItem::text) },
                    onEdit = {},
                    onDelete = {},
                )
            }
        }

        compose.onNodeWithText("BATCH-A", substring = false).performClick()
        compose.onNodeWithText("BATCH-B", substring = false).assertIsDisplayed()

        assertEquals(listOf(listOf("BATCH-A")), openedBatches)
    }

    @Test
    fun timeZoneChangeBroadcastRefreshesVisibleHistoryTime() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val originalZone = TimeZone.getDefault()
        val item = CodeItem(
            id = 1L,
            text = "TIMEZONE-ROW",
            format = "Code 128-B",
            createdAt = System.currentTimeMillis() - 12 * 60 * 60 * 1000L,
        )
        val oldTimeText = formatHistoryTime(item.createdAt)

        compose.setContent {
            AppTheme("light") {
                HistoryScreen(
                    items = listOf(item),
                    refreshGeneration = 0L,
                    dark = false,
                    onOpen = {},
                    onEdit = {},
                    onDelete = {},
                )
            }
        }

        try {
            compose.onNodeWithText(oldTimeText).assertIsDisplayed()
            compose.runOnIdle {
                TimeZone.setDefault(TimeZone.getTimeZone("GMT+10:00"))
                context.sendBroadcast(Intent(Intent.ACTION_TIMEZONE_CHANGED))
            }
            compose.waitUntil(5_000) {
                compose.onAllNodes(hasText(oldTimeText)).fetchSemanticsNodes().isEmpty()
            }
        } finally {
            compose.runOnIdle {
                TimeZone.setDefault(originalZone)
                context.sendBroadcast(Intent(Intent.ACTION_TIMEZONE_CHANGED))
            }
        }
    }

    @Test
    fun systemClockChangeBroadcastRefreshesVisibleHistoryTime() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val originalZone = TimeZone.getDefault()
        val originalTimeMillis = System.currentTimeMillis()
        val item = CodeItem(
            id = 1L,
            text = "CLOCK-ROW",
            format = "Code 128-B",
            createdAt = originalTimeMillis - 12 * 60 * 60 * 1000L,
        )
        val oldTimeText = formatHistoryTime(item.createdAt, originalTimeMillis)

        compose.setContent {
            AppTheme("light") {
                HistoryScreen(
                    items = listOf(item),
                    refreshGeneration = 0L,
                    dark = false,
                    onOpen = {},
                    onEdit = {},
                    onDelete = {},
                )
            }
        }

        try {
            compose.onNodeWithText(oldTimeText).assertIsDisplayed()
            compose.runOnIdle {
                // 模拟系统时间事件使本地日期/时间格式变化，但不触发时区广播。
                TimeZone.setDefault(TimeZone.getTimeZone("GMT+10:00"))
                context.sendBroadcast(Intent(Intent.ACTION_TIME_CHANGED))
            }
            compose.waitUntil(5_000) {
                compose.onAllNodes(hasText(oldTimeText)).fetchSemanticsNodes().isEmpty()
            }
        } finally {
            compose.runOnIdle {
                TimeZone.setDefault(originalZone)
                context.sendBroadcast(Intent(Intent.ACTION_TIME_CHANGED))
            }
        }
    }

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
            HistoryBatchEntry("batch-$index", index.toLong(), listOf(CodeItem(index.toLong(), "ROW-$index", "Code 128-B", index.toLong())))
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

    @Test
    fun visibleEditActionOpensTheWholeHistoryBatchForEditing() {
        val edited = mutableListOf<List<String>>()
        val batch = listOf(
            CodeItem(1, "FIRST", "Code 128-B", 1L),
            CodeItem(2, "SECOND", "Code 128-B", 1L),
        )
        compose.setContent {
            AppTheme("light") {
                HistoryPage(
                    batch = batch,
                    onOpen = {},
                    onEdit = { edited += it.map(CodeItem::text) },
                    onDelete = {},
                )
            }
        }

        compose.onNodeWithContentDescription("编辑这批条码").performClick()

        assertEquals(listOf(listOf("FIRST", "SECOND")), edited)
    }

    @Test
    fun editAndDeleteTouchTargetsDoNotOverlap() {
        val batch = listOf(CodeItem(1, "FIRST", "Code 128-B", 1L))
        compose.setContent {
            AppTheme("light") {
                HistoryPage(batch = batch, onOpen = {}, onEdit = {}, onDelete = {})
            }
        }

        val editBounds = compose.onNodeWithContentDescription("编辑这批条码")
            .fetchSemanticsNode().boundsInRoot
        val deleteBounds = compose.onNodeWithContentDescription("删除这条历史记录")
            .fetchSemanticsNode().boundsInRoot

        assertFalse("编辑和删除的触控区域不能重叠", editBounds.overlaps(deleteBounds))
    }

    @Test
    fun editAndDeleteIconsAreCloserThanDefaultCenteredSpacing() {
        val batch = listOf(CodeItem(1, "FIRST", "Code 128-B", 1L))
        compose.setContent {
            AppTheme("light") {
                HistoryPage(batch = batch, onOpen = {}, onEdit = {}, onDelete = {})
            }
        }

        val editBounds = compose.onNodeWithContentDescription("编辑这批条码", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val deleteBounds = compose.onNodeWithContentDescription("删除这条历史记录", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot

        assertTrue(
            "编辑图标与删除图标的可见间距应不超过图标宽度的 80%",
            deleteBounds.left - editBounds.right <= editBounds.width * 0.8f,
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
            entries = listOf(HistoryBatchEntry("test-batch", 1L, batch)),
            refreshGeneration = 0L,
            dark = false,
            onOpen = onOpen,
            onEdit = onEdit,
            onDelete = onDelete,
            timeText = { "12:00" },
        )
    }
}
