package it.toyaria.controller

import it.toyaria.model.Chunk
import it.toyaria.model.ChunkStatus
import it.toyaria.model.DownloadState
import it.toyaria.model.SerializableChunk
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.json.Json

class ResumeStateStore(private val destination: Path) {
    private val json = Json { prettyPrint = true }
    private val stateFile: Path =
        destination.resolveSibling(".${destination.fileName}.toyaria-state.json")

    fun save(state: DownloadState) {
        stateFile.parent?.createDirectories()
        stateFile.writeText(json.encodeToString(DownloadState.serializer(), state))
    }

    fun load(): DownloadState? {
        if (!stateFile.exists()) {
            return null
        }
        return json.decodeFromString(DownloadState.serializer(), stateFile.readText())
    }

    fun delete() {
        stateFile.deleteIfExists()
    }
}

fun Chunk.toSerializable(): SerializableChunk =
    SerializableChunk(
        index = index,
        startByte = startByte,
        endByte = endByte,
        status = status.name,
        checksum = checksum,
    )

fun SerializableChunk.toChunk(): Chunk =
    Chunk(
        index = index,
        startByte = startByte,
        endByte = endByte,
        status = status.toChunkStatus(),
        checksum = checksum,
    )

private fun String.toChunkStatus(): ChunkStatus =
    ChunkStatus.entries.find { status -> status.name == this }
        ?: error("Unsupported chunk status value in persisted state: $this")
