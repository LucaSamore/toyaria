package it.toyaria.controller

import it.toyaria.model.Chunk
import it.toyaria.model.ChunkStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel

/** Creates deterministic chunk layouts and worker queues. */
class ChunkScheduler {
    /**
     * Splits a file into contiguous non-overlapping chunks.
     *
     * The last chunk may be smaller than [chunkSize] when the file size is not a multiple of the
     * chunk size.
     *
     * @param fileSize full file size in bytes.
     * @param chunkSize preferred chunk size in bytes.
     * @return ordered chunks covering the full range `[0, fileSize - 1]`.
     */
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

    /**
     * Enqueues only chunks still marked as [ChunkStatus.PENDING].
     *
     * @param chunks full chunk set (including already completed ones).
     * @return closed receive channel containing pending chunks in input order.
     */
    suspend fun openQueue(chunks: List<Chunk>): ReceiveChannel<Chunk> {
        val channel = Channel<Chunk>(Channel.UNLIMITED)
        chunks.filter { it.status == ChunkStatus.PENDING }.forEach { chunk -> channel.send(chunk) }
        channel.close()
        return channel
    }
}
