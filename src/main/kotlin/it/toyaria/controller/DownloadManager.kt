package it.toyaria.controller

import it.toyaria.model.Chunk
import it.toyaria.model.ChunkStatus
import it.toyaria.model.DownloadConfig
import it.toyaria.model.DownloadEvent
import it.toyaria.model.DownloadState
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

private const val MILLIS_PER_SECOND: Long = 1000L

class DownloadManager(
    private val client: FileServerClient,
    private val scheduler: ChunkScheduler = ChunkScheduler(),
    private val worker: ChunkWorker = ChunkWorker(),
    private val storeFactory: (Path) -> ResumeStateStore = ::ResumeStateStore,
) {
    fun download(config: DownloadConfig): Flow<DownloadEvent> = flow {
        validateConfig(config)
        val fileSize = client.getFileSize(config.url)
        val store = storeFactory(config.destination)
        val chunks = resolveChunks(config = config, store = store, fileSize = fileSize)
        store.save(chunks.toDownloadState(config.url, fileSize, config.chunkSize))

        runCatching {
                runDownload(config = config, fileSize = fileSize, chunks = chunks, store = store)
                verifyChecksum(config)
                store.delete()
                emit(DownloadEvent.Completed)
            }
            .onFailure { cause -> emit(DownloadEvent.Failed(cause)) }
            .getOrThrow()
    }

    private fun validateConfig(config: DownloadConfig) {
        require(config.maxWorkers > 0) { "maxWorkers must be positive" }
        require(config.chunkSize > 0) { "chunkSize must be positive" }
    }

    private fun resolveChunks(
        config: DownloadConfig,
        store: ResumeStateStore,
        fileSize: Long,
    ): List<Chunk> {
        if (!config.resume) {
            return scheduler.partition(fileSize = fileSize, chunkSize = config.chunkSize)
        }
        return store.load()?.chunks?.map { serializedChunk -> serializedChunk.toChunk() }
            ?: scheduler.partition(fileSize = fileSize, chunkSize = config.chunkSize)
    }

    private suspend fun FlowCollector<DownloadEvent>.runDownload(
        config: DownloadConfig,
        fileSize: Long,
        chunks: List<Chunk>,
        store: ResumeStateStore,
    ) {
        val chunkState = chunks.associateBy { chunk -> chunk.index }.toMutableMap()
        val downloadedBytes =
            AtomicLong(chunks.filter { it.status == ChunkStatus.DONE }.sumOf { it.size })
        val startedAtMs = System.currentTimeMillis()

        emitProgress(downloadedBytes.get(), fileSize, startedAtMs)
        FileAssembler(config.destination, fileSize).use { assembler ->
            val queue = scheduler.openQueue(chunks)
            val eventChannel = Channel<DownloadEvent>(Channel.UNLIMITED)
            coroutineScope {
                val workers =
                    List(config.maxWorkers) {
                        launch {
                            worker.run(
                                channel = queue,
                                client = client,
                                assembler = assembler,
                                config = config,
                                events = eventChannel,
                            )
                        }
                    }
                launch {
                    workers.joinAll()
                    eventChannel.close()
                }
                collectWorkerEvents(
                    eventChannel = eventChannel,
                    chunkState = chunkState,
                    downloadedBytes = downloadedBytes,
                    fileSize = fileSize,
                    startedAtMs = startedAtMs,
                    url = config.url,
                    chunkSize = config.chunkSize,
                    store = store,
                )
            }
        }
    }

    private suspend fun FlowCollector<DownloadEvent>.collectWorkerEvents(
        eventChannel: Channel<DownloadEvent>,
        chunkState: MutableMap<Int, Chunk>,
        downloadedBytes: AtomicLong,
        fileSize: Long,
        startedAtMs: Long,
        url: String,
        chunkSize: Long,
        store: ResumeStateStore,
    ) {
        for (event in eventChannel) {
            if (event is DownloadEvent.ChunkCompleted) {
                chunkState[event.chunk.index] = event.chunk
                val current = downloadedBytes.addAndGet(event.chunk.size)
                val orderedChunks = chunkState.values.sortedBy { it.index }
                store.save(orderedChunks.toDownloadState(url, fileSize, chunkSize))
                emit(event)
                emitProgress(current, fileSize, startedAtMs)
            }
        }
    }

    private suspend fun FlowCollector<DownloadEvent>.emitProgress(
        bytesDownloaded: Long,
        totalBytes: Long,
        startedAtMs: Long,
    ) {
        val elapsedMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(1L)
        val bytesPerSecond = bytesDownloaded * MILLIS_PER_SECOND / elapsedMs
        emit(
            DownloadEvent.Progress(
                bytesDownloaded = bytesDownloaded,
                totalBytes = totalBytes,
                bytesPerSecond = bytesPerSecond,
            )
        )
    }

    private fun verifyChecksum(config: DownloadConfig) {
        val expected = config.expectedChecksum ?: return
        val actual = Checksum.sha256(config.destination)
        if (!expected.equals(actual, ignoreCase = true)) {
            throw ChecksumMismatchException(expected = expected, actual = actual)
        }
    }
}

private fun List<Chunk>.toDownloadState(
    url: String,
    fileSize: Long,
    chunkSize: Long,
): DownloadState =
    DownloadState(
        url = url,
        fileSize = fileSize,
        chunkSize = chunkSize,
        chunks = map { chunk -> chunk.toSerializable() },
    )
