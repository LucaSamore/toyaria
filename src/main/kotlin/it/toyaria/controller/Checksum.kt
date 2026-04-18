package it.toyaria.controller

import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.readBytes

object Checksum {
    private const val ALGORITHM = "SHA-256"

    fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance(ALGORITHM).digest(bytes)
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    fun sha256(path: Path): String = sha256(path.readBytes())
}
