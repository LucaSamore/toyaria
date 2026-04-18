package it.toyaria

import it.toyaria.controller.FileAssembler
import it.toyaria.model.Chunk
import java.nio.file.Files
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals

class FileAssemblerTest {
    @Test
    fun `writes chunks at expected offsets`() {
        val destination = Files.createTempFile("toyaria-assembler", ".bin")

        FileAssembler(destination = destination, fileSize = 11L).use { assembler ->
            assembler.write(
                Chunk(index = 0, startByte = 0L, endByte = 4L),
                "hello".encodeToByteArray(),
            )
            assembler.write(
                Chunk(index = 1, startByte = 5L, endByte = 10L),
                " world".encodeToByteArray(),
            )
        }

        assertContentEquals("hello world".encodeToByteArray(), destination.readBytes())
        Files.deleteIfExists(destination)
    }
}
