package it.toyaria.view

import it.toyaria.model.DownloadEvent
import java.io.PrintStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val PROGRESS_TICK_MS: Long = 200L
private const val BAR_WIDTH: Int = 24
private const val ETA_UNAVAILABLE = "--:--"
private const val RATIO_MIN = 0.0
private const val RATIO_MAX = 1.0
private const val PERCENT_SCALE = 100.0
private const val SECONDS_PER_MINUTE = 60L

class ProgressTracker(private val stderr: PrintStream = System.err) {
    suspend fun track(events: Flow<DownloadEvent>) {
        val bytesDownloaded = AtomicLong(0L)
        val totalBytes = AtomicLong(0L)
        val bytesPerSecond = AtomicLong(0L)
        val completed = AtomicBoolean(false)
        var failure: Throwable? = null

        coroutineScope {
            val renderer = launch {
                while (isActive && !completed.get()) {
                    renderProgress(
                        bytesDownloaded = bytesDownloaded.get(),
                        totalBytes = totalBytes.get(),
                        bytesPerSecond = bytesPerSecond.get(),
                    )
                    delay(PROGRESS_TICK_MS)
                }
            }

            try {
                events.collect { event ->
                    when (event) {
                        is DownloadEvent.Progress -> {
                            bytesDownloaded.set(event.bytesDownloaded)
                            totalBytes.set(event.totalBytes)
                            bytesPerSecond.set(event.bytesPerSecond)
                        }
                        is DownloadEvent.ChunkCompleted -> Unit
                        is DownloadEvent.Completed -> {
                            completed.set(true)
                            renderProgress(
                                bytesDownloaded = bytesDownloaded.get(),
                                totalBytes = totalBytes.get(),
                                bytesPerSecond = bytesPerSecond.get(),
                            )
                            stderr.println()
                        }
                        is DownloadEvent.Failed -> {
                            completed.set(true)
                            stderr.println()
                            failure = event.cause
                        }
                    }
                }
            } finally {
                completed.set(true)
                renderer.cancelAndJoin()
            }
        }

        failure?.let { error -> throw error }
    }

    private fun renderProgress(bytesDownloaded: Long, totalBytes: Long, bytesPerSecond: Long) {
        if (totalBytes <= 0L) {
            stderr.print(
                "\rtoyaria  [${" ".repeat(BAR_WIDTH)}]   0.0%   0 B/s   ETA $ETA_UNAVAILABLE"
            )
            return
        }
        val ratio = bytesDownloaded.toDouble() / totalBytes.toDouble()
        val clampedRatio = ratio.coerceIn(RATIO_MIN, RATIO_MAX)
        val filled = (clampedRatio * BAR_WIDTH).toInt().coerceIn(0, BAR_WIDTH)
        val bar = "█".repeat(filled) + "░".repeat(BAR_WIDTH - filled)
        val percent = clampedRatio * PERCENT_SCALE
        val eta = estimateEta(totalBytes - bytesDownloaded, bytesPerSecond)
        val speed = "${formatBytes(bytesPerSecond)}/s"
        stderr.print("\rtoyaria  [$bar]  ${"%.1f".format(percent)}%   $speed   ETA $eta")
    }

    private fun estimateEta(remainingBytes: Long, bytesPerSecond: Long): String {
        if (bytesPerSecond <= 0L || remainingBytes <= 0L) {
            return ETA_UNAVAILABLE
        }
        val seconds = (remainingBytes / bytesPerSecond).coerceAtLeast(0L)
        val minutes = seconds / SECONDS_PER_MINUTE
        val remainder = seconds % SECONDS_PER_MINUTE
        return "%02d:%02d".format(minutes, remainder)
    }

    private fun formatBytes(value: Long): String {
        val units = listOf("B", "KB", "MB", "GB", "TB")
        var size = value.toDouble().coerceAtLeast(0.0)
        var index = 0
        while (size >= KIBI && index < units.lastIndex) {
            size /= KIBI
            index += 1
        }
        return "%.1f %s".format(size, units[index])
    }

    companion object {
        private const val KIBI = 1024.0
    }
}
