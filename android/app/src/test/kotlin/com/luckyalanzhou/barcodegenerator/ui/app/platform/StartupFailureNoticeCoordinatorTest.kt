package com.luckyalanzhou.barcodegenerator.ui.app.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch

class StartupFailureNoticeCoordinatorTest {
    @Test
    fun settingsFailureIsReportedAsSettingsFailureAfterLibraryLoadCompletes() {
        val coordinator = StartupFailureNoticeCoordinator()

        assertNull(coordinator.reportSettingsLoad(failed = true))
        assertEquals(
            "设置加载失败，已使用默认设置启动",
            coordinator.reportLibraryLoad(failed = false),
        )
    }

    @Test
    fun libraryFailureIsReportedEvenWhenSettingsLoadSucceeded() {
        val coordinator = StartupFailureNoticeCoordinator()

        assertNull(coordinator.reportSettingsLoad(failed = false))
        assertEquals(
            "收藏和历史数据加载失败，已使用默认内容启动",
            coordinator.reportLibraryLoad(failed = true),
        )
    }

    @Test
    fun concurrentStartupFailuresAreCombinedIntoOneNotice() {
        val coordinator = StartupFailureNoticeCoordinator()

        assertNull(coordinator.reportSettingsLoad(failed = true))
        assertEquals(
            "设置、收藏和历史数据加载失败，已使用默认设置和默认内容启动",
            coordinator.reportLibraryLoad(failed = true),
        )
    }

    @Test
    fun resultOrderDoesNotChangeNoticeAndNoticeIsEmittedOnlyOnce() {
        val coordinator = StartupFailureNoticeCoordinator()

        assertNull(coordinator.reportLibraryLoad(failed = true))
        assertEquals(
            "收藏和历史数据加载失败，已使用默认内容启动",
            coordinator.reportSettingsLoad(failed = false),
        )
        assertNull(coordinator.reportSettingsLoad(failed = true))
    }

    @Test
    fun concurrentStartupResultsEmitAtMostOneNotice() {
        val coordinator = StartupFailureNoticeCoordinator()
        val start = CountDownLatch(1)
        val notices = Collections.synchronizedList(mutableListOf<String>())
        val settingsThread = Thread {
            start.await()
            coordinator.reportSettingsLoad(failed = true)?.let(notices::add)
        }
        val libraryThread = Thread {
            start.await()
            coordinator.reportLibraryLoad(failed = true)?.let(notices::add)
        }

        settingsThread.start()
        libraryThread.start()
        start.countDown()
        settingsThread.join()
        libraryThread.join()

        assertEquals(1, notices.size)
        assertEquals("设置、收藏和历史数据加载失败，已使用默认设置和默认内容启动", notices.single())
    }
}
