package com.luckyalanzhou.barcodegenerator.presentation.lanshare

import com.luckyalanzhou.barcodegenerator.domain.LanShareGateway
import com.luckyalanzhou.barcodegenerator.domain.LanShareSession
import java.lang.reflect.Proxy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LanShareRefreshResultTest {
    @Test
    fun ordinaryNetworkExceptionBecomesRecoverableFailure() = runBlocking {
        val networkError = IllegalStateException("network unavailable")

        val result = loadLanShareRefreshData(gatewayFailingWith(networkError), session)

        assertTrue(result.isFailure)
        assertSame(networkError, result.exceptionOrNull())
    }

    @Test
    fun cancellationPropagatesInsteadOfBecomingNetworkFailure() = runBlocking {
        val cancellation = CancellationException("refresh cancelled")
        val thrown = try {
            loadLanShareRefreshData(gatewayFailingWith(cancellation), session)
            null
        } catch (error: Throwable) {
            error
        }

        assertSame(cancellation, thrown)
    }

    @Test
    fun fatalErrorPropagatesInsteadOfBecomingRecoverableRefreshResult() = runBlocking {
        val fatalError = AssertionError("fatal refresh error")
        val thrown = try {
            loadLanShareRefreshData(gatewayFailingWith(fatalError), session)
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
            if (method.name == "list") throw failure
            error("Unexpected gateway operation: ${method.name}")
        } as LanShareGateway

    private companion object {
        val session = LanShareSession("http://192.168.1.2:8080")
    }
}
