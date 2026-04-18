package it.toyaria

import it.toyaria.controller.ChunkScheduler
import it.toyaria.model.Chunk
import it.toyaria.model.ChunkStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class ChunkSchedulerTest {
    private val scheduler = ChunkScheduler()

    @Test
    fun `partition computes correct boundaries`() {
        val chunks = scheduler.partition(fileSize = 10L, chunkSize = 4L)

        assertEquals(3, chunks.size)
        assertEquals(Chunk(index = 0, startByte = 0L, endByte = 3L), chunks[0])
        assertEquals(Chunk(index = 1, startByte = 4L, endByte = 7L), chunks[1])
        assertEquals(Chunk(index = 2, startByte = 8L, endByte = 9L), chunks[2])
    }

    @Test
    fun `openQueue enqueues only pending chunks`() = runTest {
        val chunks =
            listOf(
                Chunk(index = 0, startByte = 0L, endByte = 3L, status = ChunkStatus.DONE),
                Chunk(index = 1, startByte = 4L, endByte = 7L, status = ChunkStatus.PENDING),
                Chunk(index = 2, startByte = 8L, endByte = 9L, status = ChunkStatus.PENDING),
            )

        val queue = scheduler.openQueue(chunks)
        val dequeued = mutableListOf<Chunk>()
        for (chunk in queue) {
            dequeued += chunk
        }

        assertEquals(listOf(1, 2), dequeued.map { it.index })
    }
}
