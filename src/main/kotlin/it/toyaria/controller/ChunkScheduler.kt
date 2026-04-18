package it.toyaria.controller

import it.toyaria.model.Chunk
import it.toyaria.model.ChunkStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel

class ChunkScheduler {
    fun partition(fileSize: Long, chunkSize: Long): List<Chunk> {
        require(chunkSize > 0) { "chunkSize must be positive" }
        require(fileSize > 0) { "fileSize must be positive" }
        val count = (fileSize + chunkSize - 1) / chunkSize
        return (0 until count).map { index ->
            val start = index * chunkSize
            val end = minOf(start + chunkSize - 1, fileSize - 1)
            Chunk(index = index.toInt(), startByte = start, endByte = end)
        }
    }

    suspend fun openQueue(chunks: List<Chunk>): ReceiveChannel<Chunk> {
        val channel = Channel<Chunk>(Channel.UNLIMITED)
        chunks.filter { it.status == ChunkStatus.PENDING }.forEach { chunk -> channel.send(chunk) }
        channel.close()
        return channel
    }
}
