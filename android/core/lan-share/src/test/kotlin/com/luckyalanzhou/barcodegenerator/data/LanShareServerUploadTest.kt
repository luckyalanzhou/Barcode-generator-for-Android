package com.luckyalanzhou.barcodegenerator.data

import com.luckyalanzhou.barcodegenerator.data.network.protocol.LanShareLimits
import com.luckyalanzhou.barcodegenerator.data.network.server.LanShareServer
import com.luckyalanzhou.barcodegenerator.data.network.protocol.LAN_SHARE_SOCKET_READ_TIMEOUT_MS
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.LanShareRealtimeEvent
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LanShareServerUploadTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun failedUploadReleasesReservedRoomFileSlot() {
        val folder = temporaryFolder.newFolder()
        val roomFileLimit = LanShareLimits.MAX_ROOM_FILES
        repeat(roomFileLimit - 1) { index ->
            File(folder, "fixture-$index.bin").writeBytes(byteArrayOf(index.toByte()))
        }
        val failFirstCommit = AtomicBoolean(true)
        val server = LanShareServer(
            "127.0.0.1",
            0,
            folder,
            AppLogger { _, _, _ -> },
            beforeUploadCommit = {
                if (failFirstCommit.getAndSet(false)) throw IOException("synthetic commit failure")
            },
        )
        try {
            server.start(5_000, false)

            val failed = upload(server.listeningPort, server.sessionToken, byteArrayOf(1), "failed.bin", "app", false)
            assertEquals(500, failed.first)
            assertEquals(roomFileLimit - 1, server.filesSnapshot("peer").size)
            assertTrue(folder.listFiles().orEmpty().none { it.name.endsWith(".part") })

            val retry = upload(server.listeningPort, server.sessionToken, byteArrayOf(2), "retry.bin", "app", false)
            assertEquals(200, retry.first)
            assertEquals(roomFileLimit, server.filesSnapshot("peer").size)
        } finally {
            server.stop()
        }
    }

    @Test
    fun concurrentUploadsCannotExceedRoomFileCountAndExistingFilesRemainDownloadable() {
        val folder = temporaryFolder.newFolder()
        assertEquals(1_000, LanShareLimits.MAX_ROOM_FILES)
        val roomFileLimit = LanShareLimits.MAX_ROOM_FILES
        repeat(roomFileLimit - 1) { index ->
            File(folder, "fixture-$index.bin").writeBytes(byteArrayOf(index.toByte()))
        }

        val firstCommitReached = CountDownLatch(1)
        val allowFirstCommit = CountDownLatch(1)
        val commitHookCalls = AtomicInteger()
        val server = LanShareServer(
            "127.0.0.1",
            0,
            folder,
            AppLogger { _, _, _ -> },
            maxConcurrentUploads = 2,
            beforeUploadCommit = {
                if (commitHookCalls.getAndIncrement() == 0) {
                    firstCommitReached.countDown()
                    check(allowFirstCommit.await(5, TimeUnit.SECONDS)) {
                        "timed out waiting to release the first upload commit"
                    }
                }
            },
        )
        val firstResponse = AtomicReference<Pair<Int, String>?>(null)
        val firstFailure = AtomicReference<Throwable?>(null)
        var firstUploadThread: Thread? = null
        try {
            server.start(5_000, false)
            firstUploadThread = Thread {
                try {
                    firstResponse.set(
                        upload(server.listeningPort, server.sessionToken, byteArrayOf(1), "first.bin", "app", false),
                    )
                } catch (error: Throwable) {
                    firstFailure.set(error)
                }
            }.apply { start() }

            assertTrue("the first upload should reach its pre-commit barrier", firstCommitReached.await(5, TimeUnit.SECONDS))
            val competingResponse = upload(
                server.listeningPort,
                server.sessionToken,
                byteArrayOf(2),
                "competing.bin",
                "app",
                false,
            )
            assertEquals(400, competingResponse.first)
            assertTrue(competingResponse.second.contains("1,000"))

            allowFirstCommit.countDown()
            firstUploadThread.join(10_000)
            assertTrue("the first upload should finish after releasing the barrier", !firstUploadThread.isAlive)
            firstFailure.get()?.let { throw AssertionError("the first upload failed unexpectedly", it) }
            assertEquals(200, firstResponse.get()?.first)
            assertEquals(roomFileLimit, server.filesSnapshot("peer").size)

            val fullRoomResponse = upload(
                server.listeningPort,
                server.sessionToken,
                byteArrayOf(3),
                "after-limit.bin",
                "app",
                false,
            )
            assertEquals(400, fullRoomResponse.first)
            assertTrue(fullRoomResponse.second.contains("1,000"))
            assertEquals(roomFileLimit, server.filesSnapshot("peer").size)

            val fixture = File(folder, "fixture-0.bin")
            val encodedId = URLEncoder.encode(fixture.name, StandardCharsets.UTF_8.name())
            val download = URL("http://127.0.0.1:${server.listeningPort}/dl/$encodedId")
                .openConnection() as HttpURLConnection
            try {
                download.setRequestProperty("Authorization", "Bearer ${server.sessionToken}")
                assertEquals(200, download.responseCode)
                assertArrayEquals(byteArrayOf(0), download.inputStream.use { it.readBytes() })
            } finally {
                download.disconnect()
            }
        } finally {
            allowFirstCommit.countDown()
            firstUploadThread?.join(10_000)
            server.stop()
        }
    }

    @Test
    fun insufficientStorageRejectsBeforeCreatingPartialFile() {
        val folder = temporaryFolder.newFolder()
        val server = LanShareServer("127.0.0.1", 0, folder, AppLogger { _, _, _ -> }, availableUploadBytes = { 0L })
        try {
            server.start(5_000, false)
            val response = upload(server.listeningPort, server.sessionToken, byteArrayOf(1, 2, 3), "small.bin", "app", false)
            assertEquals(503, response.first)
            assertTrue(response.second.contains("存储空间不足"))
            assertTrue(folder.listFiles().orEmpty().isEmpty())
        } finally { server.stop() }
    }

    @Test
    fun busyUploadIsRejectedAndDisconnectReleasesSlotForRetry() {
        val folder = temporaryFolder.newFolder()
        val server = LanShareServer(
            "127.0.0.1",
            0,
            folder,
            AppLogger { _, message, _ ->
                if (message.startsWith("upload rejected")) throw IOException("diagnostic sink unavailable")
            },
            maxConcurrentUploads = 1,
        )
        var pending: Socket? = null
        try {
            server.start(5_000, false)
            pending = Socket("127.0.0.1", server.listeningPort)
            pending.getOutputStream().apply {
                write("PUT /upload?name=slow.bin&client=app HTTP/1.1\r\nHost: localhost\r\nAuthorization: Bearer ${server.sessionToken}\r\nContent-Length: 1048576\r\nConnection: close\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
                write(ByteArray(32 * 1024))
                flush()
            }
            val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
            while (folder.listFiles().orEmpty().none { it.name.endsWith(".part") } && System.nanoTime() < deadline) Thread.sleep(10)
            assertTrue(folder.listFiles().orEmpty().any { it.name.endsWith(".part") })
            assertEquals(503, upload(server.listeningPort, server.sessionToken, byteArrayOf(1, 2), "busy.bin", "app", false).first)
            pending.close()
            val retryDeadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
            var retry = 503
            while (retry == 503 && System.nanoTime() < retryDeadline) {
                Thread.sleep(10)
                retry = upload(server.listeningPort, server.sessionToken, byteArrayOf(1, 2), "retry.bin", "app", false).first
            }
            assertEquals(200, retry)
            assertTrue(folder.listFiles().orEmpty().none { it.name.endsWith(".part") })
        } finally { pending?.close(); server.stop() }
    }

    @Test
    fun slowDripExceedingTotalDeadlineReleasesUploadSlotAndStagingFile() {
        val folder = temporaryFolder.newFolder()
        val diagnostics = java.util.concurrent.CopyOnWriteArrayList<String>()
        val uploadFailed = CountDownLatch(1)
        val server = LanShareServer(
            "127.0.0.1",
            0,
            folder,
            AppLogger { _, message, error ->
                diagnostics += "$message $error"
                if (message.startsWith("upload failed")) uploadFailed.countDown()
            },
            maxConcurrentUploads = 1,
            uploadTimeoutMillisForBytes = { 500L },
        )
        var slowSocket: Socket? = null
        try {
            server.start(5_000, false)
            slowSocket = Socket("127.0.0.1", server.listeningPort).apply { tcpNoDelay = true }
            val output = slowSocket.getOutputStream()
            output.write(
                ("PUT /upload?name=slow.bin&client=app HTTP/1.1\r\n" +
                    "Host: localhost\r\n" +
                    "Authorization: Bearer ${server.sessionToken}\r\n" +
                    "Content-Length: 1048576\r\n" +
                    "Connection: close\r\n\r\n").toByteArray(StandardCharsets.US_ASCII),
            )
            output.flush()
            val stagingDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (folder.listFiles().orEmpty().none { it.name.endsWith(".part") } &&
                System.nanoTime() < stagingDeadline
            ) {
                Thread.sleep(10)
            }
            assertTrue("the upload should hold a staging file while receiving", folder.listFiles().orEmpty().any { it.name.endsWith(".part") })

            var sentBytes = 0
            val dripDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (System.nanoTime() < dripDeadline && uploadFailed.count > 0L) {
                try {
                    output.write(0x41)
                    output.flush()
                    sentBytes++
                } catch (_: IOException) {
                    break
                }
                Thread.sleep(25)
            }

            assertTrue("the sender must make progress while the total deadline expires", sentBytes >= 3)
            assertTrue("the server should time out the active slow-drip upload", uploadFailed.await(1, TimeUnit.SECONDS))
            slowSocket.close()

            val retryDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            var retryStatus = 503
            while (retryStatus == 503 && System.nanoTime() < retryDeadline) {
                retryStatus = upload(
                    server.listeningPort,
                    server.sessionToken,
                    byteArrayOf(1, 2),
                    "retry.bin",
                    "app",
                    chunked = false,
                ).first
                if (retryStatus == 503) Thread.sleep(20)
            }

            assertEquals("timed-out upload must release its concurrency slot", 200, retryStatus)
            assertTrue("timed-out upload must remove its staging file", folder.listFiles().orEmpty().none { it.name.endsWith(".part") })
            assertTrue(diagnostics.any { it.contains("elapsedMs=") })
        } finally {
            slowSocket?.close()
            server.stop()
        }
    }

    @Test
    fun stagingCleanupFailureStillReleasesUploadSlot() {
        val folder = temporaryFolder.newFolder()
        val cleanupFailure = AtomicBoolean(true)
        val diagnostics = java.util.concurrent.CopyOnWriteArrayList<String>()
        val uploadFailed = CountDownLatch(1)
        val server = LanShareServer(
            "127.0.0.1",
            0,
            folder,
            AppLogger { _, message, error ->
                diagnostics += "$message $error"
                if (message.startsWith("upload failed")) uploadFailed.countDown()
            },
            maxConcurrentUploads = 1,
            uploadTimeoutMillisForBytes = { 500L },
            deleteUploadStagingFile = { file ->
                if (cleanupFailure.getAndSet(false)) throw IOException("synthetic staging cleanup failure")
                file.delete()
            },
        )
        var interruptedSocket: Socket? = null
        try {
            server.start(5_000, false)
            interruptedSocket = Socket("127.0.0.1", server.listeningPort).apply { tcpNoDelay = true }
            interruptedSocket.getOutputStream().apply {
                write(
                    ("PUT /upload?name=interrupted.bin&client=app HTTP/1.1\r\n" +
                        "Host: localhost\r\n" +
                        "Authorization: Bearer ${server.sessionToken}\r\n" +
                        "Content-Length: 4096\r\n" +
                        "Connection: close\r\n\r\n").toByteArray(StandardCharsets.US_ASCII),
                )
                write(0x41)
                flush()
            }

            val stagingDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (folder.listFiles().orEmpty().none { it.name.endsWith(".part") } && System.nanoTime() < stagingDeadline) {
                Thread.sleep(10)
            }
            assertTrue("the interrupted upload should create a staging file", folder.listFiles().orEmpty().any { it.name.endsWith(".part") })
            assertTrue("the interrupted upload should fail its bounded read", uploadFailed.await(5, TimeUnit.SECONDS))
            interruptedSocket.close()

            val retryDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            var retryStatus = 503
            while (retryStatus == 503 && System.nanoTime() < retryDeadline) {
                retryStatus = upload(
                    server.listeningPort,
                    server.sessionToken,
                    byteArrayOf(2, 3),
                    "retry-after-cleanup.bin",
                    "app",
                    chunked = false,
                ).first
                if (retryStatus == 503) Thread.sleep(20)
            }

            assertEquals("cleanup failure must not permanently consume the upload slot", 200, retryStatus)
            assertTrue(diagnostics.any { it.contains("staging cleanup failed") })
            assertTrue("a failed filesystem delete may leave a staging file for next-room cleanup", folder.listFiles().orEmpty().any { it.name.endsWith(".part") })
            val visibleFiles = server.filesSnapshot("peer")
            assertEquals(1, visibleFiles.size)
            assertTrue(visibleFiles.single().name.endsWith("retry-after-cleanup.bin"))
        } finally {
            interruptedSocket?.close()
            server.stop()
        }
    }

    @Test
    fun uploadFinishingAfterTotalDeadlineIsNotCommitted() {
        val folder = temporaryFolder.newFolder()
        val uploadReadyToCommit = CountDownLatch(1)
        val continueCommit = CountDownLatch(1)
        val response = AtomicReference<Pair<Int, String>?>()
        val uploadFailure = AtomicReference<Throwable?>()
        val server = LanShareServer(
            "127.0.0.1",
            0,
            folder,
            AppLogger { _, _, _ -> },
            maxConcurrentUploads = 1,
            beforeUploadCommit = {
                uploadReadyToCommit.countDown()
                check(continueCommit.await(5, TimeUnit.SECONDS)) { "test timed out waiting to release upload commit" }
            },
            uploadTimeoutMillisForBytes = { TimeUnit.SECONDS.toMillis(2) },
        )
        var uploadThread: Thread? = null
        try {
            server.start(5_000, false)
            val requestThread = Thread {
                runCatching {
                    upload(server.listeningPort, server.sessionToken, byteArrayOf(1, 2, 3), "late.bin", "app", false)
                }.onSuccess(response::set).onFailure(uploadFailure::set)
            }.apply {
                name = "test-lan-share-late-upload"
            }
            uploadThread = requestThread
            requestThread.start()

            assertTrue("upload should reach the pre-commit boundary", uploadReadyToCommit.await(5, TimeUnit.SECONDS))
            Thread.sleep(TimeUnit.MILLISECONDS.toMillis(2_100))
            continueCommit.countDown()
            requestThread.join(5_000)

            assertTrue("upload handler should finish after the deadline", !requestThread.isAlive)
            assertTrue("upload client should receive the server response", uploadFailure.get() == null)
            assertEquals("expired upload must be rejected before publishing", 500, response.get()?.first ?: -1)
            assertTrue("expired upload must remove its staging file and keep target hidden", folder.listFiles().orEmpty().isEmpty())

            assertEquals(200, upload(server.listeningPort, server.sessionToken, byteArrayOf(4), "retry.bin", "app", false).first)
        } finally {
            continueCommit.countDown()
            uploadThread?.join(5_000)
            server.stop()
        }
    }

    @Test
    fun fixedLengthUploadWritesOriginalBytesToSingleCommittedFile() {
        val folder = temporaryFolder.newFolder()
        val payload = ByteArray(768 * 1024 + 37) { index -> (index * 31).toByte() }
        val diagnostics = java.util.concurrent.CopyOnWriteArrayList<String>()
        val server = newServer(folder, diagnostics)
        try {
            server.start(5_000, false)
            val result = upload(server.listeningPort, server.sessionToken, payload, "原图 % sample.jpg", "app", chunked = false)

            assertEquals(200, result.first)
            assertTrue(result.second.startsWith("app_"))
            assertArrayEquals(payload, File(folder, result.second).readBytes())
            assertTrue(folder.listFiles().orEmpty().none { it.name.startsWith(".lan-upload-") || it.name.endsWith(".part") })
            assertTrue(diagnostics.any { it.contains("listener receiveBufferDefaultBytes=") })
            assertTrue(diagnostics.any { it.contains("accepted socket receiveBufferBytes=") })
            assertTrue(diagnostics.any {
                it.contains("upload complete source=app framing=content-length") &&
                    it.contains("readMs=") && it.contains("writeMs=") && it.contains("flushMs=") &&
                    it.contains("bodyMbps=") && it.contains("readCalls=")
            })
        } finally {
            server.stop()
        }
    }

    @Test
    fun chunkedBrowserUploadUsesDeclaredSizeAndPreservesBytes() {
        val folder = temporaryFolder.newFolder()
        val payload = ByteArray(384 * 1024 + 19) { index -> (index xor (index ushr 8)).toByte() }
        val diagnostics = java.util.concurrent.CopyOnWriteArrayList<String>()
        val server = newServer(folder, diagnostics)
        try {
            server.start(5_000, false)
            val result = upload(server.listeningPort, server.sessionToken, payload, "browser.bin", "cbrowser123", chunked = true)

            assertEquals(200, result.first)
            assertTrue(result.second.startsWith("web_"))
            assertTrue(result.second.contains("_cbrowser123_"))
            assertArrayEquals(payload, File(folder, result.second).readBytes())
            assertTrue(diagnostics.any {
                it.contains("upload complete source=browser framing=chunked") && it.contains("httpChunks=")
            })
        } finally {
            server.stop()
        }
    }

    @Test
    fun chunkedUploadRejectsChunkLargerThanDeclaredSizeAndRemovesStagingFile() {
        assertRejectedChunkedUpload(
            expectedBytes = 3,
            payload = "ABCD",
        )
    }

    @Test
    fun chunkedUploadRejectsPrematureTerminatorAndRemovesStagingFile() {
        assertRejectedChunkedUpload(
            expectedBytes = 3,
            payload = "AB",
        )
    }

    @Test
    fun fixedLengthUploadSurvivesIdleGapLongerThanPreviousSocketTimeout() {
        val folder = temporaryFolder.newFolder()
        val firstPart = ByteArray(512 * 1024) { index -> (index * 13).toByte() }
        val secondPart = ByteArray(512 * 1024) { index -> (index * 29).toByte() }
        val server = newServer(folder)
        var connection: HttpURLConnection? = null
        try {
            server.start(LAN_SHARE_SOCKET_READ_TIMEOUT_MS, false)
            val uploadConnection = URL("http://127.0.0.1:${server.listeningPort}/upload?name=paused.bin&client=cbrowser123")
                .openConnection() as HttpURLConnection
            connection = uploadConnection
            uploadConnection.connectTimeout = 5_000
            uploadConnection.readTimeout = 20_000
            uploadConnection.requestMethod = "PUT"
            uploadConnection.setRequestProperty("Authorization", "Bearer ${server.sessionToken}")
            uploadConnection.setRequestProperty("X-File-Size", (firstPart.size + secondPart.size).toString())
            uploadConnection.setRequestProperty("Content-Type", "application/octet-stream")
            uploadConnection.doOutput = true
            uploadConnection.setFixedLengthStreamingMode(firstPart.size + secondPart.size)

            val output = uploadConnection.outputStream
            output.write(firstPart)
            output.flush()
            val stagingDeadline = System.nanoTime() + 5_000_000_000L
            while (folder.listFiles().orEmpty().none {
                    it.name.startsWith(".lan-upload-") && it.length() > 0L
                } && System.nanoTime() < stagingDeadline
            ) {
                Thread.sleep(10)
            }
            assertTrue(
                "server should persist upload bytes before the intentional idle gap",
                folder.listFiles().orEmpty().any { it.name.startsWith(".lan-upload-") && it.length() > 0L },
            )

            Thread.sleep(6_000)
            output.write(secondPart)
            output.close()

            val status = uploadConnection.responseCode
            val storedId = uploadConnection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText().trim() }
            assertEquals(200, status)
            assertArrayEquals(firstPart + secondPart, File(folder, storedId).readBytes())
        } finally {
            connection?.disconnect()
            server.stop()
        }
    }

    @Test
    fun abortedUploadRemovesTemporaryFileWithoutCommittingPartialBytes() {
        val folder = temporaryFolder.newFolder()
        val server = newServer(folder)
        var connection: Socket? = null
        try {
            server.start(5_000, false)
            val partialPayload = ByteArray(256 * 1024) { index -> index.toByte() }
            val uploadConnection = Socket("127.0.0.1", server.listeningPort)
            connection = uploadConnection
            val output = uploadConnection.getOutputStream()
            output.write((
                "PUT /upload?name=partial.bin&client=cbrowser123 HTTP/1.1\r\n" +
                    "Host: 127.0.0.1:${server.listeningPort}\r\n" +
                    "Content-Type: application/octet-stream\r\n" +
                    "Content-Length: ${2 * 1024 * 1024}\r\n" +
                    "Authorization: Bearer ${server.sessionToken}\r\n" +
                    "X-File-Size: ${2 * 1024 * 1024}\r\n\r\n"
                ).toByteArray(StandardCharsets.US_ASCII))
            output.write(partialPayload)
            output.flush()

            val stagedDeadline = System.nanoTime() + 5_000_000_000L
            while (folder.listFiles().orEmpty().none { it.name.startsWith(".lan-upload-") } && System.nanoTime() < stagedDeadline) {
                Thread.sleep(10)
            }
            assertTrue("server should stage the partial upload before it is cancelled", folder.listFiles().orEmpty().any {
                it.name.startsWith(".lan-upload-")
            })

            // 关闭真实的 TCP 流，不使用 HttpURLConnection 管理的连接。
            uploadConnection.close()

            val cleanupDeadline = System.nanoTime() + 10_000_000_000L
            while (folder.listFiles().orEmpty().isNotEmpty() && System.nanoTime() < cleanupDeadline) {
                Thread.sleep(10)
            }
            val remainingFiles = folder.listFiles().orEmpty()
            assertTrue(
                "cancelled upload must not leave staged or committed files; " +
                    "remaining=${remainingFiles.joinToString { "${it.name}:${it.length()}" }}",
                remainingFiles.isEmpty(),
            )
        } finally {
            connection?.close()
            server.stop()
        }
    }

    @Test
    fun stoppingServerPreventsAnAlreadyReceivedUploadFromCommittingAfterStop() {
        val folder = temporaryFolder.newFolder()
        val uploadReadyToCommit = CountDownLatch(1)
        val continueCommit = CountDownLatch(1)
        val uploadFailure = AtomicReference<Throwable?>()
        val server = LanShareServer(
            "127.0.0.1",
            0,
            folder,
            AppLogger { _, _, _ -> },
            beforeUploadCommit = {
                uploadReadyToCommit.countDown()
                check(continueCommit.await(5, TimeUnit.SECONDS)) { "test timed out waiting to release upload commit" }
            },
        )
        var uploadThread: Thread? = null
        try {
            server.start(5_000, false)
            uploadThread = Thread {
                runCatching {
                    upload(server.listeningPort, server.sessionToken, byteArrayOf(1, 2, 3), "late.bin", "app", false)
                }.onFailure(uploadFailure::set)
            }.apply {
                name = "test-lan-share-upload"
                isDaemon = true
                start()
            }

            assertTrue("upload should reach the commit boundary", uploadReadyToCommit.await(5, TimeUnit.SECONDS))
            server.stop()
            continueCommit.countDown()
            uploadThread.join(5_000)
            val cleanupDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (folder.listFiles().orEmpty().isNotEmpty() && System.nanoTime() < cleanupDeadline) {
                Thread.sleep(10)
            }

            assertTrue("upload handler should finish after the commit gate is released", !uploadThread.isAlive)
            assertTrue(
                "a stopped server must not publish an upload that was still pending commit; " +
                    "failure=${uploadFailure.get()?.javaClass?.simpleName}, files=${folder.listFiles().orEmpty().map { it.name }}",
                folder.listFiles().orEmpty().isEmpty(),
            )
        } finally {
            continueCommit.countDown()
            uploadThread?.join(5_000)
            server.stop()
        }
    }

    @Test
    fun downloadedFileUsesDlRouteAndPreservesOriginalBytes() {
        val folder = temporaryFolder.newFolder()
        val payload = "原始文件内容\r\nkey: value\n".toByteArray(StandardCharsets.UTF_8)
        val server = newServer(folder)
        try {
            server.start(5_000, false)
            val uploaded = upload(server.listeningPort, server.sessionToken, payload, "source.yaml", "cbrowser123", chunked = false)
            assertEquals(200, uploaded.first)

            val encodedId = URLEncoder.encode(uploaded.second, StandardCharsets.UTF_8.name())
            val connection = URL("http://127.0.0.1:${server.listeningPort}/dl/$encodedId")
                .openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                connection.setRequestProperty("Authorization", "Bearer ${server.sessionToken}")
                assertEquals(200, connection.responseCode)
                assertArrayEquals(payload, connection.inputStream.use { it.readBytes() })
            } finally {
                connection.disconnect()
            }
        } finally {
            server.stop()
        }
    }

    @Test
    fun uploadedSvgIsServedAsAttachmentWithNosniff() {
        val folder = temporaryFolder.newFolder()
        val payload = "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".toByteArray(StandardCharsets.UTF_8)
        val server = newServer(folder)
        try {
            server.start(5_000, false)
            val uploaded = upload(
                server.listeningPort,
                server.sessionToken,
                payload,
                "payload.svg",
                "cbrowser123",
                chunked = false,
                contentType = "image/svg+xml",
            )
            assertEquals(200, uploaded.first)

            val encodedId = URLEncoder.encode(uploaded.second, StandardCharsets.UTF_8.name())
            val connection = URL("http://127.0.0.1:${server.listeningPort}/dl/$encodedId")
                .openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                connection.setRequestProperty("Authorization", "Bearer ${server.sessionToken}")
                assertEquals(200, connection.responseCode)
                assertEquals("image/svg+xml", connection.contentType)
                assertTrue(
                    "download route must not render uploaded SVG as an inline document",
                    connection.getHeaderField("Content-Disposition")?.startsWith("attachment;") == true,
                )
                assertEquals("nosniff", connection.getHeaderField("X-Content-Type-Options"))
                assertArrayEquals(payload, connection.inputStream.use { it.readBytes() })
            } finally {
                connection.disconnect()
            }
        } finally {
            server.stop()
        }
    }

    @Test
    fun uploadedFileWithPartExtensionRemainsVisibleAndDownloadable() {
        val folder = temporaryFolder.newFolder()
        val payload = byteArrayOf(3, 1, 4, 1, 5)
        val server = newServer(folder)
        try {
            server.start(5_000, false)
            val uploaded = upload(server.listeningPort, server.sessionToken, payload, "report.part", "cbrowser123", false)
            assertEquals(200, uploaded.first)
            assertTrue(
                "committed .part files must be present in the shared file snapshot",
                server.filesSnapshot("peer").any { it.id == uploaded.second },
            )

            val encodedId = URLEncoder.encode(uploaded.second, StandardCharsets.UTF_8.name())
            val downloadConnection = URL("http://127.0.0.1:${server.listeningPort}/dl/$encodedId")
                .openConnection() as HttpURLConnection
            try {
                downloadConnection.setRequestProperty("Authorization", "Bearer ${server.sessionToken}")
                assertEquals(200, downloadConnection.responseCode)
                assertArrayEquals(payload, downloadConnection.inputStream.use { it.readBytes() })
            } finally {
                downloadConnection.disconnect()
            }
        } finally {
            server.stop()
        }
    }

    @Test
    fun uploadedPathSeparatorsCannotEscapeSharedFolder() {
        val folder = temporaryFolder.newFolder()
        val payload = byteArrayOf(8, 6, 7, 5, 3, 0, 9)
        val server = newServer(folder)
        try {
            server.start(5_000, false)
            val uploaded = upload(server.listeningPort, server.sessionToken, payload, "../../outside.bin", "cbrowser123", false)

            assertEquals(200, uploaded.first)
            val storedFile = File(folder, uploaded.second)
            assertEquals(folder.canonicalFile, storedFile.canonicalFile.parentFile)
            assertArrayEquals(payload, storedFile.readBytes())
        } finally {
            server.stop()
        }
    }

    @Test
    fun committedBrowserUploadPreservesDeclaredMimeTypeInRealtimeEvent() {
        val folder = temporaryFolder.newFolder()
        val events = mutableListOf<LanShareRealtimeEvent>()
        val server = LanShareServer(
            "127.0.0.1", 0, folder, AppLogger { _, _, _ -> },
            emitRealtimeEvent = { events += it },
        )
        try {
            server.start(5_000, false)
            val uploaded = upload(
                server.listeningPort,
                server.sessionToken,
                byteArrayOf(1, 2, 3),
                "camera-photo",
                "cbrowser123",
                chunked = false,
                contentType = "image/heic",
            )

            assertEquals(200, uploaded.first)
            val event = events.filterIsInstance<LanShareRealtimeEvent.FileAdded>().single()
            assertEquals(uploaded.second, event.file.id)
            assertEquals("browser:cbrowser123", event.file.sender)
            assertEquals("image/heic", event.file.mimeType)
        } finally {
            server.stop()
        }
    }

    @Test
    fun committedUploadRemainsSuccessfulWhenRealtimeNotificationThrows() {
        val folder = temporaryFolder.newFolder()
        val payload = byteArrayOf(4, 2, 7, 1)
        val diagnostics = mutableListOf<String>()
        val server = LanShareServer(
            "127.0.0.1", 0, folder,
            AppLogger { _, message, _ -> diagnostics += message },
            emitRealtimeEvent = { throw IOException("event sink unavailable") },
        )
        try {
            server.start(5_000, false)

            val response = upload(server.listeningPort, server.sessionToken, payload, "event-failure.bin", "app", false)

            val files = folder.listFiles().orEmpty().filter { !it.name.endsWith(".part") }
            assertEquals(1, files.size)
            assertArrayEquals(payload, files.single().readBytes())
            assertEquals(200, response.first)
            assertTrue(diagnostics.none { it.startsWith("upload failed") })
        } finally {
            server.stop()
        }
    }

    @Test
    fun committedUploadRemainsSuccessfulWhenCompletionLoggerThrows() {
        val folder = temporaryFolder.newFolder()
        val payload = byteArrayOf(3, 1, 4, 1, 5)
        val diagnostics = mutableListOf<String>()
        val server = LanShareServer(
            "127.0.0.1", 0, folder,
            AppLogger { _, message, _ ->
                if (message.startsWith("upload complete")) throw IOException("diagnostic sink unavailable")
                diagnostics += message
            },
        )
        try {
            server.start(5_000, false)

            val response = upload(server.listeningPort, server.sessionToken, payload, "logger-failure.bin", "app", false)

            val files = folder.listFiles().orEmpty().filter { !it.name.endsWith(".part") }
            assertEquals(1, files.size)
            assertArrayEquals(payload, files.single().readBytes())
            assertEquals(200, response.first)
            assertTrue(diagnostics.none { it.startsWith("upload failed") })
        } finally {
            server.stop()
        }
    }

    @Test
    fun mismatchedContentLengthAndDeclaredFileSizeAreRejectedBeforeSaving() {
        val folder = temporaryFolder.newFolder()
        val payload = byteArrayOf(1, 2, 3, 4)
        val server = newServer(folder)
        try {
            server.start(5_000, false)
            val result = upload(
                server.listeningPort,
                server.sessionToken,
                payload,
                "mismatch.bin",
                "cbrowser123",
                chunked = false,
                declaredSize = payload.size + 1L,
            )

            assertEquals(400, result.first)
            assertTrue(folder.listFiles().orEmpty().isEmpty())
        } finally {
            server.stop()
        }
    }

    private fun newServer(
        folder: File,
        diagnostics: MutableList<String> = mutableListOf(),
    ) = LanShareServer(
        "127.0.0.1",
        0,
        folder,
        AppLogger { _, message, _ -> diagnostics += message },
    )

    private fun assertRejectedChunkedUpload(expectedBytes: Long, payload: String) {
        val folder = temporaryFolder.newFolder()
        val diagnostics = mutableListOf<String>()
        val server = newServer(folder, diagnostics)
        try {
            server.start(5_000, false)
            val connection = URL("http://127.0.0.1:${server.listeningPort}/upload?name=malformed.bin&client=cbrowser123")
                .openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 5_000
                connection.readTimeout = 10_000
                connection.requestMethod = "PUT"
                connection.setRequestProperty("Authorization", "Bearer ${server.sessionToken}")
                connection.setRequestProperty("X-File-Size", expectedBytes.toString())
                connection.setRequestProperty("Content-Type", "application/octet-stream")
                connection.doOutput = true
                connection.setChunkedStreamingMode(8 * 1024)
                val response = runCatching {
                    connection.outputStream.use { it.write(payload.toByteArray(StandardCharsets.US_ASCII)) }
                    val status = connection.responseCode
                    val body = connection.errorStream
                        ?.bufferedReader(StandardCharsets.UTF_8)
                        ?.use { it.readText().trim() }
                        .orEmpty()
                    status to body
                }
                assertTrue(
                    "malformed chunked upload must return HTTP 500; response=${response.getOrNull()} " +
                        "socketError=${response.exceptionOrNull()} logs=$diagnostics",
                    response.getOrNull()?.first == 500,
                )
            } finally {
                connection.disconnect()
            }
            assertTrue("malformed upload must not publish or leave partial files", folder.listFiles().orEmpty().isEmpty())
        } finally {
            server.stop()
        }
    }

    private fun upload(
        port: Int,
        token: String,
        payload: ByteArray,
        fileName: String,
        clientId: String,
        chunked: Boolean,
        declaredSize: Long = payload.size.toLong(),
        contentType: String = "application/octet-stream",
    ): Pair<Int, String> {
        val encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name())
        val connection = URL("http://127.0.0.1:$port/upload?name=$encodedName&client=$clientId").openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 10_000
            connection.requestMethod = "PUT"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("X-File-Size", declaredSize.toString())
            connection.setRequestProperty("Content-Type", contentType)
            connection.doOutput = true
            if (chunked) connection.setChunkedStreamingMode(8 * 1024)
            else connection.setFixedLengthStreamingMode(payload.size)
            connection.outputStream.use { it.write(payload) }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText().trim() }.orEmpty()
            status to response
        } finally {
            connection.disconnect()
        }
    }
}
