package it.toyaria.controller

import it.toyaria.model.Chunk
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

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

    fun write(chunk: Chunk, bytes: ByteArray) {
        check(bytes.size.toLong() == chunk.size) {
            "Chunk ${chunk.index} has invalid payload size ${bytes.size}; expected ${chunk.size}"
        }
        channel.write(ByteBuffer.wrap(bytes), chunk.startByte)
    }

    override fun close() {
        channel.close()
    }
}
