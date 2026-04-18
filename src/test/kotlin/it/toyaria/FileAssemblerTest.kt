package it.toyaria

import it.toyaria.controller.FileAssembler
import it.toyaria.model.Chunk
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

    @Test
    fun `preallocates destination file to requested size`() {
        val destination = Files.createTempFile("toyaria-prealloc", ".bin")

        FileAssembler(destination = destination, fileSize = 128L).use {}

        assertEquals(128L, destination.fileSize())
        Files.deleteIfExists(destination)
    }

    @Test
    fun `creates parent directories when missing`() {
        val parent = Files.createTempDirectory("toyaria-assembler-parent")
        val nested = parent.resolve("a/b/c")
        val destination = nested.resolve("file.bin")

        FileAssembler(destination = destination, fileSize = 16L).use {}

        assertEquals(true, nested.exists())
        assertEquals(true, destination.exists())
        destination.toFile().delete()
        nested.toFile().deleteRecursively()
        parent.toFile().deleteRecursively()
    }

    @Test
    fun `fails when file size is not positive`() {
        val destination = Files.createTempFile("toyaria-invalid-size", ".bin")

        assertFailsWith<IllegalArgumentException> {
            FileAssembler(destination = destination, fileSize = 0L)
        }

        Files.deleteIfExists(destination)
    }

    @Test
    fun `fails when chunk payload size does not match chunk size`() {
        val destination = Files.createTempFile("toyaria-invalid-payload", ".bin")

        FileAssembler(destination = destination, fileSize = 8L).use { assembler ->
            val error =
                assertFailsWith<IllegalStateException> {
                    assembler.write(
                        Chunk(index = 0, startByte = 0L, endByte = 3L),
                        "abc".encodeToByteArray(),
                    )
                }
            assertEquals("Chunk 0 has invalid payload size 3; expected 4", error.message)
        }

        Files.deleteIfExists(destination)
    }
}
