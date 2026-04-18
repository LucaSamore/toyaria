package it.toyaria.controller

import it.toyaria.model.Chunk

class ServerDoesNotSupportRangesException(url: String) :
    IllegalStateException("Server does not support range requests for url: $url")

class ChunkDownloadException(val chunk: Chunk, cause: Throwable) :
    RuntimeException(
        "Failed to download chunk ${chunk.index} (${chunk.startByte}-${chunk.endByte})",
        cause,
    )

class ChecksumMismatchException(expected: String, actual: String) :
    IllegalStateException("Checksum mismatch. Expected: $expected, actual: $actual")
