package it.toyaria

import it.toyaria.controller.ChecksumMismatchException
import it.toyaria.controller.ChunkDownloadException
import it.toyaria.controller.ServerDoesNotSupportRangesException
import it.toyaria.model.Chunk
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertSame

class DownloadExceptionsTest {
    @Test
    fun `ServerDoesNotSupportRangesException contains URL in message`() {
        val url = "http://localhost:8080/file.bin"

        val error = ServerDoesNotSupportRangesException(url)

        assertContains(error.message.orEmpty(), url)
    }

    @Test
    fun `ChunkDownloadException exposes chunk and cause`() {
        val chunk = Chunk(index = 2, startByte = 100L, endByte = 199L)
        val cause = IllegalStateException("boom")

        val error = ChunkDownloadException(chunk, cause)

        assertSame(chunk, error.chunk)
        assertSame(cause, error.cause)
        assertContains(error.message.orEmpty(), "2")
        assertContains(error.message.orEmpty(), "100-199")
    }

    @Test
    fun `ChecksumMismatchException reports expected and actual checksums`() {
        val expected = "abc123"
        val actual = "def456"

        val error = ChecksumMismatchException(expected, actual)

        assertContains(error.message.orEmpty(), expected)
        assertContains(error.message.orEmpty(), actual)
        assertEquals("Checksum mismatch. Expected: $expected, actual: $actual", error.message)
    }
}
