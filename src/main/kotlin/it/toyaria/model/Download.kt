package it.toyaria.model

import java.nio.file.Path
import kotlinx.serialization.Serializable

data class DownloadConfig(
    val url: String,
    val destination: Path,
    val chunkSize: Long = DEFAULT_CHUNK_SIZE,
    val maxWorkers: Int = DEFAULT_WORKERS,
    val resume: Boolean = true,
    val expectedChecksum: String? = null,
) {
    companion object {
        const val DEFAULT_CHUNK_SIZE: Long = 8L * 1024L * 1024L
        const val DEFAULT_WORKERS: Int = 4
    }
}

sealed class DownloadEvent {
    data class Progress(val bytesDownloaded: Long, val totalBytes: Long, val bytesPerSecond: Long) :
        DownloadEvent()

    data class ChunkCompleted(val chunk: Chunk) : DownloadEvent()

    data object Completed : DownloadEvent()

    data class Failed(val cause: Throwable) : DownloadEvent()
}

@Serializable
data class DownloadState(
    val url: String,
    val fileSize: Long,
    val chunkSize: Long,
    val chunks: List<SerializableChunk>,
)
