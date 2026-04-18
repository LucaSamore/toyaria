package it.toyaria

import it.toyaria.controller.ResumeStateStore
import it.toyaria.model.DownloadState
import it.toyaria.model.SerializableChunk
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ResumeStateStoreTest {
    @Test
    fun `state round trips and can be deleted`() {
        val destination = Files.createTempFile("toyaria-resume", ".bin")
        val store = ResumeStateStore(destination)
        val state =
            DownloadState(
                url = "https://example.com/file.bin",
                fileSize = 100L,
                chunkSize = 10L,
                chunks =
                    listOf(
                        SerializableChunk(
                            index = 0,
                            startByte = 0L,
                            endByte = 9L,
                            status = "DONE",
                            checksum = "abc",
                        )
                    ),
            )

        store.save(state)
        assertEquals(state, store.load())

        store.delete()
        assertEquals(null, store.load())

        val stateFile = destination.resolveSibling(".${destination.fileName}.toyaria-state.json")
        assertFalse(stateFile.exists())
        Files.deleteIfExists(destination)
    }
}
