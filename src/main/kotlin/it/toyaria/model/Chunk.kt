package it.toyaria.model

import kotlinx.serialization.Serializable

/** Immutable description of a byte range handled as a single download unit. */
data class Chunk(
    val index: Int,
    val startByte: Long,
    val endByte: Long,
    val status: ChunkStatus = ChunkStatus.PENDING,
    val checksum: String? = null,
) {
    /** Inclusive chunk length in bytes. */
    val size: Long
        get() = endByte - startByte + 1
}

/** Lifecycle state for a chunk during download execution and resume persistence. */
enum class ChunkStatus {
    PENDING,
    IN_PROGRESS,
    DONE,
    FAILED,
}

/** JSON-friendly chunk snapshot used in [DownloadState] files. */
@Serializable
data class SerializableChunk(
    val index: Int,
    val startByte: Long,
    val endByte: Long,
    val status: String,
    val checksum: String? = null,
)
