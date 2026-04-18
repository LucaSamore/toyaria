package it.toyaria.controller

import it.toyaria.model.Chunk
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

/**
 * Writes downloaded chunks directly into their final offsets in the destination file.
 *
 * The file is pre-allocated in the constructor so worker writes are positional overwrites instead
 * of append operations.
 */
class FileAssembler(destination: Path, fileSize: Long) : Closeable {
    private val channel: FileChannel

    init {
        require(fileSize > 0) { "fileSize must be positive" }
        val parent = destination.parent
        if (parent != null && !parent.exists()) {
            parent.createDirectories()
        }
        channel =
            FileChannel.open(
                destination,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.READ,
            )
        channel.write(ByteBuffer.allocate(1), fileSize - 1)
    }

    /** Writes chunk payload bytes at [Chunk.startByte]. */
    fun write(chunk: Chunk, bytes: ByteArray) {
        check(bytes.size.toLong() == chunk.size) {
            "Chunk ${chunk.index} has invalid payload size ${bytes.size}; expected ${chunk.size}"
        }
        channel.write(ByteBuffer.wrap(bytes), chunk.startByte)
    }

    /** Closes the underlying file channel. */
    override fun close() {
        channel.close()
    }
}
