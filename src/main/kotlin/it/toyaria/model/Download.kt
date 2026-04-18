package it.toyaria.model

import java.nio.file.Path
import kotlinx.serialization.Serializable

/** Immutable input configuration for a single download session. */
data class DownloadConfig(
    val url: String,
    val destination: Path,
    val chunkSize: Long = DEFAULT_CHUNK_SIZE,
    val maxWorkers: Int = DEFAULT_WORKERS,
    val resume: Boolean = true,
    val expectedChecksum: String? = null,
) {
    companion object {
        /** Default chunk size: 8 MiB. */
        const val DEFAULT_CHUNK_SIZE: Long = 8L * 1024L * 1024L

        /** Default number of parallel workers. */
        const val DEFAULT_WORKERS: Int = 4
    }
}

/** Event stream produced by the controller layer and consumed by view renderers. */
sealed class DownloadEvent {
    /** Periodic transfer snapshot with cumulative bytes and estimated throughput. */
    data class Progress(val bytesDownloaded: Long, val totalBytes: Long, val bytesPerSecond: Long) :
        DownloadEvent()

    /** Emitted when one chunk has been fully downloaded and written to disk. */
    data class ChunkCompleted(val chunk: Chunk) : DownloadEvent()

    /** Emitted after all chunks are completed and optional verification succeeds. */
    data object Completed : DownloadEvent()

    /** Emitted when the download terminates due to an unrecoverable failure. */
    data class Failed(val cause: Throwable) : DownloadEvent()
}

/** Serializable resume snapshot written next to the destination file. */
@Serializable
data class DownloadState(
    val url: String,
    val fileSize: Long,
    val chunkSize: Long,
    val chunks: List<SerializableChunk>,
)
