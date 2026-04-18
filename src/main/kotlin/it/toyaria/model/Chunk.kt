package it.toyaria.model

import kotlinx.serialization.Serializable

data class Chunk(
    val index: Int,
    val startByte: Long,
    val endByte: Long,
    val status: ChunkStatus = ChunkStatus.PENDING,
    val checksum: String? = null,
) {
    val size: Long
        get() = endByte - startByte + 1
}

enum class ChunkStatus {
    PENDING,
    IN_PROGRESS,
    DONE,
    FAILED,
}

@Serializable
data class SerializableChunk(
    val index: Int,
    val startByte: Long,
    val endByte: Long,
    val status: String,
    val checksum: String? = null,
)
