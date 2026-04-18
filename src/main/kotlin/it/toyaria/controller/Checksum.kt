package it.toyaria.controller

import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.readBytes

/** Utility functions for SHA-256 digest calculation. */
object Checksum {
    private const val ALGORITHM = "SHA-256"

    /**
     * Returns the SHA-256 digest for [bytes] as lowercase hexadecimal text.
     *
     * @param bytes input payload to hash.
     * @return 64-character lowercase hexadecimal SHA-256 digest.
     */
    fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance(ALGORITHM).digest(bytes)
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    /**
     * Reads the full file and returns its SHA-256 digest as lowercase hexadecimal text.
     *
     * @param path file to hash.
     * @return 64-character lowercase hexadecimal SHA-256 digest.
     */
    fun sha256(path: Path): String = sha256(path.readBytes())
}
