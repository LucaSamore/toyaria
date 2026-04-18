package it.toyaria.view

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import it.toyaria.controller.DownloadManager
import it.toyaria.controller.KtorFileServerClient
import it.toyaria.model.DownloadConfig
import java.net.URI
import java.nio.file.Paths
import kotlinx.coroutines.runBlocking

private const val DEFAULT_CHUNK_SIZE_TEXT = "8MB"
private const val DEFAULT_OUTPUT_FILENAME = "download.bin"

class ToyAria : CliktCommand(name = "toyaria") {
    private val url by argument(name = "URL").help("URL of the file to download")
    private val outputPath by option("-o", "--output").help("Destination file path").path()
    private val workers by
        option("-n", "--workers")
            .help("Number of parallel workers")
            .int()
            .default(DownloadConfig.DEFAULT_WORKERS)
    private val chunkSizeText by
        option("-s", "--chunk-size")
            .help("Chunk size, e.g. 4MB, 16MB")
            .default(DEFAULT_CHUNK_SIZE_TEXT)
    private val noResume by option("--no-resume").help("Disable resume").flag(default = false)
    private val expectedChecksum by option("--checksum").help("Expected SHA-256 hex digest")

    init {
        versionOption("1.0-SNAPSHOT", names = setOf("--version"))
    }

    override fun run() {
        val destination = outputPath ?: Paths.get(inferFilename(url))
        val config =
            DownloadConfig(
                url = url,
                destination = destination,
                chunkSize = parseChunkSize(chunkSizeText),
                maxWorkers = workers,
                resume = !noResume,
                expectedChecksum = expectedChecksum,
            )

        runCatching {
                runBlocking {
                    KtorFileServerClient().use { client ->
                        val manager = DownloadManager(client)
                        val tracker = ProgressTracker()
                        tracker.track(manager.download(config))
                    }
                }
            }
            .getOrElse { error ->
                echo("Download failed: ${error.message}", err = true)
                throw ProgramResult(statusCode = 1)
            }
    }

    private fun inferFilename(rawUrl: String): String {
        val path = URI(rawUrl).path.orEmpty()
        val candidate = path.substringAfterLast('/').trim()
        return candidate.ifBlank { DEFAULT_OUTPUT_FILENAME }
    }

    private fun parseChunkSize(input: String): Long {
        val normalized = input.trim().lowercase()
        val regex = Regex("^(\\d+)(b|kb|mb|gb)?$")
        val match =
            checkNotNull(regex.matchEntire(normalized)) {
                "Invalid chunk size '$input'. Use forms like 4MB, 16MB or 1048576"
            }
        val amount =
            checkNotNull(match.groupValues[1].toLongOrNull()) {
                "Chunk size amount is not a valid number: $input"
            }
        val unit = match.groupValues[2].ifBlank { "b" }
        val multiplier =
            when (unit) {
                "b" -> BYTES
                "kb" -> KIBI
                "mb" -> MEBI
                "gb" -> GIBI
                else -> error("Unsupported chunk size unit: $unit")
            }
        return amount * multiplier
    }

    companion object {
        private const val BYTES = 1L
        private const val KIBI = 1024L
        private const val MEBI = KIBI * 1024L
        private const val GIBI = MEBI * 1024L
    }
}
