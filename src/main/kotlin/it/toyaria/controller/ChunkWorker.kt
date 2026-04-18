package it.toyaria.controller

import it.toyaria.model.Chunk
import it.toyaria.model.ChunkStatus
import it.toyaria.model.DownloadConfig
import it.toyaria.model.DownloadEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay

private const val RETRY_INITIAL_DELAY_MS: Long = 500L
private const val RETRY_BACKOFF_FACTOR: Double = 2.0
private const val RETRY_ATTEMPTS: Int = 3

class ChunkWorker {
    suspend fun run(
        channel: ReceiveChannel<Chunk>,
        client: FileServerClient,
        assembler: FileAssembler,
        config: DownloadConfig,
        events: SendChannel<DownloadEvent>,
    ) {
        for (chunk in channel) {
            val bytes =
                retry(maxAttempts = RETRY_ATTEMPTS) {
                    client.downloadChunk(config.url, chunk.startByte, chunk.endByte)
                }
            val checksum = Checksum.sha256(bytes)
            assembler.write(chunk, bytes)
            val completed = chunk.copy(status = ChunkStatus.DONE, checksum = checksum)
            events.send(DownloadEvent.ChunkCompleted(completed))
        }
    }
}

suspend fun <T> retry(
    maxAttempts: Int = RETRY_ATTEMPTS,
    initialDelayMs: Long = RETRY_INITIAL_DELAY_MS,
    factor: Double = RETRY_BACKOFF_FACTOR,
    block: suspend () -> T,
): T {
    require(maxAttempts > 0) { "maxAttempts must be positive" }
    require(initialDelayMs >= 0) { "initialDelayMs must be non-negative" }
    require(factor >= 1.0) { "factor must be >= 1.0" }

    var delayMs = initialDelayMs
    repeat(maxAttempts - 1) {
        val result = runCatching { block() }
        result.onSuccess { value ->
            return value
        }
        val error = result.exceptionOrNull()
        if (error is CancellationException) {
            throw error
        }
        delay(delayMs)
        delayMs = (delayMs * factor).toLong()
    }
    return block()
}
