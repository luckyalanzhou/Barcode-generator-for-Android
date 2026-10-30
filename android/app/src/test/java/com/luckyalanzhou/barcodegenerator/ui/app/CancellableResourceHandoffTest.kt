package com.luckyalanzhou.barcodegenerator.ui.app

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Test

class CancellableResourceHandoffTest {
    @Test
    fun cancellationAfterBackgroundPreparationReleasesUnclaimedResource() = runBlocking {
        val preparationStarted = CountDownLatch(1)
        val finishPreparation = CountDownLatch(1)
        val resourceRef = AtomicReference<TestResource?>()

        val preparation = launch(start = CoroutineStart.UNDISPATCHED) {
            handoffCancellableResource(
                prepare = {
                    withContext(Dispatchers.Default) {
                        preparationStarted.countDown()
                        check(finishPreparation.await(5, TimeUnit.SECONDS))
                        TestResource().also(resourceRef::set)
                    }
                },
                release = TestResource::release,
            )
        }

        try {
            assertTrue("background preparation should start", preparationStarted.await(5, TimeUnit.SECONDS))
            preparation.cancel()
        } finally {
            finishPreparation.countDown()
        }
        preparation.join()

        assertTrue(
            "a resource rejected by lifecycle cancellation must be released",
            requireNotNull(resourceRef.get()).released.get(),
        )
    }

    private class TestResource {
        val released = AtomicBoolean(false)

        fun release() {
            released.set(true)
        }
    }
}
