package com.luckyalanzhou.barcodegenerator.presentation.lanshare

import com.luckyalanzhou.barcodegenerator.domain.LanShareGateway
import com.luckyalanzhou.barcodegenerator.domain.LanShareSession
import java.io.File
import java.lang.reflect.Proxy
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LanSharePreviewDownloadResultTest {
    @Test
    fun ordinaryPreviewFailureCanBeIgnoredByTheCaller() {
        val networkError = IllegalStateException("preview unavailable")

        val result = downloadLanSharePreviewResult(
            gatewayFailingWith(networkError), session, "image-id", File("preview.jpg"), 1024L,
        )

        assertTrue(result.isFailure)
        assertSame(networkError, result.exceptionOrNull())
    }

    @Test
    fun cancellationPropagatesInsteadOfBecomingMissingPreview() {
        val cancellation = CancellationException("preview cancelled")
        val thrown = try {
            downloadLanSharePreviewResult(
                gatewayFailingWith(cancellation), session, "image-id", File("preview.jpg"), 1024L,
            )
            null
        } catch (error: Throwable) {
            error
        }

        assertSame(cancellation, thrown)
    }

    @Test
    fun fatalErrorPropagatesInsteadOfBecomingMissingPreview() {
        val fatalError = AssertionError("fatal preview error")
        val thrown = try {
            downloadLanSharePreviewResult(
                gatewayFailingWith(fatalError), session, "image-id", File("preview.jpg"), 1024L,
            )
            null
        } catch (error: Throwable) {
            error
        }

        assertSame(fatalError, thrown)
    }

    private fun gatewayFailingWith(failure: Throwable): LanShareGateway =
        Proxy.newProxyInstance(
            LanShareGateway::class.java.classLoader,
            arrayOf(LanShareGateway::class.java),
        ) { _, method, _ ->
            if (method.name == "downloadPreview") throw failure
            error("Unexpected gateway operation: ${method.name}")
        } as LanShareGateway

    private companion object {
        val session = LanShareSession("http://192.168.1.2:8080")
    }
}
