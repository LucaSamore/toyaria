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

/** Persists and loads download progress snapshots used to resume interrupted transfers. */
class ResumeStateStore(destination: Path) {
    private val json = Json { prettyPrint = true }
    private val stateFile: Path =
        destination.resolveSibling(".${destination.fileName}.toyaria-state.json")

    /** Serializes [state] to the sidecar JSON state file. */
    fun save(state: DownloadState) {
        stateFile.parent?.createDirectories()
        stateFile.writeText(json.encodeToString(DownloadState.serializer(), state))
    }

    /**
     * Loads a previously saved state file, or returns `null` when no state exists.
     *
     * @return persisted [DownloadState], or `null` if the state file is absent.
     */
    fun load(): DownloadState? {
        if (!stateFile.exists()) {
            return null
        }
        return json.decodeFromString(DownloadState.serializer(), stateFile.readText())
    }

    /** Deletes the persisted state file if present. */
    fun delete() {
        stateFile.deleteIfExists()
    }
}

/**
 * Converts a runtime [Chunk] into its JSON-friendly representation.
 *
 * @return serialized chunk payload suitable for persistence.
 */
fun Chunk.toSerializable(): SerializableChunk =
    SerializableChunk(
        index = index,
        startByte = startByte,
        endByte = endByte,
        status = status.name,
        checksum = checksum,
    )

/**
 * Converts a persisted chunk payload back into a runtime [Chunk].
 *
 * @return runtime chunk reconstructed from persisted values.
 */
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
