package com.luckyalanzhou.barcodegenerator.data.network.server

import fi.iki.elonen.NanoHTTPD
import java.util.concurrent.atomic.AtomicLong

/** 限制 NanoHTTPD 同时持有的客户端线程，避免每个入站连接都无上限地创建线程。 */
internal class BoundedNanoHttpdAsyncRunner(
    private val maxConcurrentConnections: Int,
) : NanoHTTPD.AsyncRunner {
    private val lock = Any()
    private val handlers = linkedSetOf<NanoHTTPD.ClientHandler>()
    private val threadIds = AtomicLong()
    private var stopped = false

    init {
        require(maxConcurrentConnections > 0)
    }

    internal val activeHandlerCount: Int
        get() = synchronized(lock) { handlers.size }

    override fun exec(clientHandler: NanoHTTPD.ClientHandler) {
        val accepted = synchronized(lock) {
            if (stopped || handlers.size >= maxConcurrentConnections) {
                false
            } else {
                handlers.add(clientHandler)
                true
            }
        }
        if (!accepted) {
            clientHandler.close()
            return
        }

        val worker = Thread({
            try {
                clientHandler.run()
            } finally {
                closed(clientHandler)
            }
        }, "LanShare Request Processor (${threadIds.incrementAndGet()})").apply {
            isDaemon = true
        }
        try {
            worker.start()
        } catch (error: Throwable) {
            closed(clientHandler)
            runCatching { clientHandler.close() }
            throw error
        }
    }

    override fun closed(clientHandler: NanoHTTPD.ClientHandler) {
        synchronized(lock) { handlers.remove(clientHandler) }
    }

    override fun closeAll() {
        val activeHandlers = synchronized(lock) {
            stopped = true
            handlers.toList()
        }
        activeHandlers.forEach(NanoHTTPD.ClientHandler::close)
    }
}
