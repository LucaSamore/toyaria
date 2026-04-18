package it.toyaria.controller

import it.toyaria.model.Chunk

/** Thrown when the remote server does not advertise byte-range support. */
class ServerDoesNotSupportRangesException(url: String) :
    IllegalStateException("Server does not support range requests for url: $url")

/** Wraps a low-level failure that occurs while downloading one specific chunk. */
class ChunkDownloadException(val chunk: Chunk, cause: Throwable) :
    RuntimeException(
        "Failed to download chunk ${chunk.index} (${chunk.startByte}-${chunk.endByte})",
        cause,
    )

/** Thrown when the final downloaded file digest does not match the expected checksum. */
class ChecksumMismatchException(expected: String, actual: String) :
    IllegalStateException("Checksum mismatch. Expected: $expected, actual: $actual")
