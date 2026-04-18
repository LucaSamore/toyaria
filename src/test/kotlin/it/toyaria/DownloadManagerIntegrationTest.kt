package it.toyaria

import it.toyaria.controller.ChecksumMismatchException
import it.toyaria.controller.DownloadManager
import it.toyaria.controller.FileServerClient
import it.toyaria.controller.ResumeStateStore
import it.toyaria.model.DownloadConfig
import it.toyaria.model.DownloadEvent
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking

class DownloadManagerIntegrationTest {
    @Test
    fun `download resumes after failure and completes`(): Unit = runBlocking {
        val payload = ByteArray(64) { index -> index.toByte() }
        val destination = Files.createTempFile("toyaria-int", ".bin")
        val config =
            DownloadConfig(
                url = "https://example.com/file.bin",
                destination = destination,
                chunkSize = 16L,
                maxWorkers = 1,
                resume = true,
            )

        val firstClient =
            FailingFileServerClient(
                payload = payload,
                failChunkIndex = 2,
                failAttempts = 3,
                chunkSize = 16L,
            )
        val firstManager = DownloadManager(firstClient)
        assertFailsWith<SimulatedChunkFailureException> { firstManager.download(config).collect() }

        val store = ResumeStateStore(destination)
        val partialState = store.load()
        assertTrue(partialState != null)
        assertTrue(partialState.chunks.any { it.status == "DONE" })

        val secondManager =
            DownloadManager(
                FailingFileServerClient(payload = payload, failChunkIndex = null, chunkSize = 16L)
            )
        secondManager.download(config).collect()

        assertContentEquals(payload, destination.readBytes())
        val stateFile = destination.resolveSibling(".${destination.fileName}.toyaria-state.json")
        assertTrue(!stateFile.exists())
        Files.deleteIfExists(destination)
    }

    @Test
    fun `download fails when expected checksum does not match`(): Unit = runBlocking {
        val payload = ByteArray(32) { index -> (index + 1).toByte() }
        val destination = Files.createTempFile("toyaria-checksum", ".bin")
        val config =
            DownloadConfig(
                url = "https://example.com/file.bin",
                destination = destination,
                chunkSize = 8L,
                maxWorkers = 1,
                resume = false,
                expectedChecksum = "deadbeef",
            )

        val manager =
            DownloadManager(
                FailingFileServerClient(payload = payload, failChunkIndex = null, chunkSize = 8L)
            )
        assertFailsWith<ChecksumMismatchException> {
            manager.download(config).collect { event ->
                if (event is DownloadEvent.Failed) {
                    throw event.cause
                }
            }
        }
        Files.deleteIfExists(destination)
    }
}

private class FailingFileServerClient(
    private val payload: ByteArray,
    private val failChunkIndex: Int?,
    private val failAttempts: Int = 1,
    private val chunkSize: Long,
) : FileServerClient {
    private var failures = 0

    override suspend fun getFileSize(url: String): Long = payload.size.toLong()

    override suspend fun downloadChunk(url: String, startByte: Long, endByte: Long): ByteArray {
        val chunkIndex = (startByte / chunkSize).toInt()
        if (failChunkIndex != null && chunkIndex == failChunkIndex && failures < failAttempts) {
            failures += 1
            throw SimulatedChunkFailureException()
        }
        return payload.copyOfRange(startByte.toInt(), endByte.toInt() + 1)
    }

    override fun close() {
        // no-op
    }
}

private class SimulatedChunkFailureException : IllegalStateException("simulated transient failure")
