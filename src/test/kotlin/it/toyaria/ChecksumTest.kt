package it.toyaria

import it.toyaria.controller.Checksum
import kotlin.test.Test
import kotlin.test.assertEquals

class ChecksumTest {
    @Test
    fun `sha256 matches known vector`() {
        val digest = Checksum.sha256("abc".encodeToByteArray())

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", digest)
    }
}
