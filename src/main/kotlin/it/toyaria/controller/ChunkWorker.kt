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

/**
 * Worker that consumes chunks from a shared queue and downloads them.
 *
 * For each chunk, the worker retries transient failures, computes a per-chunk digest, writes bytes
 * at the target offset, and publishes a completion event.
 */
class ChunkWorker {
    /**
     * Runs the worker loop until the input channel is closed.
     *
     * @param channel shared chunk queue.
     * @param client HTTP range client used to fetch chunk bytes.
     * @param assembler destination writer for positional chunk writes.
     * @param config immutable download settings.
     * @param events output channel for worker events.
     */
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

/**
 * Retries [block] with exponential backoff.
 *
 * Cancellation is propagated immediately; non-cancellation failures are retried up to [maxAttempts]
 * times.
 *
 * @param maxAttempts total attempt count, including the final attempt.
 * @param initialDelayMs initial backoff delay in milliseconds.
 * @param factor delay multiplier applied after each failed attempt.
 * @param block operation to execute.
 * @return successful result produced by [block].
 * @throws CancellationException when coroutine cancellation is detected.
 */
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
