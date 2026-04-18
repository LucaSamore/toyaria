package it.toyaria.controller

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import it.toyaria.model.Chunk
import java.io.Closeable

/** Abstraction over a file server supporting HTTP range requests. */
interface FileServerClient : Closeable {
    /**
     * Returns the full file size in bytes for the given URL.
     *
     * @param url remote file URL.
     * @return content length in bytes.
     * @throws ServerDoesNotSupportRangesException when the server does not support byte ranges.
     */
    suspend fun getFileSize(url: String): Long

    /**
     * Downloads a closed byte range `[startByte, endByte]` from the remote file.
     *
     * @param url remote file URL.
     * @param startByte first byte index in the inclusive range.
     * @param endByte last byte index in the inclusive range.
     * @return bytes returned by the server for the requested range.
     */
    suspend fun downloadChunk(url: String, startByte: Long, endByte: Long): ByteArray
}

/**
 * [FileServerClient] implementation based on Ktor CIO.
 *
 * It validates range support with a `HEAD` request and uses `Range` headers for chunk downloads.
 */
class KtorFileServerClient(
    private val client: HttpClient = HttpClient(CIO) { expectSuccess = false }
) : FileServerClient {
    /**
     * Performs a `HEAD` request and extracts `Content-Length` after validating that `Accept-Ranges`
     * includes `bytes`.
     *
     * @throws ServerDoesNotSupportRangesException when `Accept-Ranges` does not include `bytes`.
     */
    override suspend fun getFileSize(url: String): Long {
        val response = client.head(url)
        check(response.status.isSuccess()) {
            "HEAD request to $url failed with status ${response.status}"
        }
        val acceptRanges = response.headers[HttpHeaders.AcceptRanges].orEmpty()
        if (!acceptRanges.contains(BYTES_UNIT, ignoreCase = true)) {
            throw ServerDoesNotSupportRangesException(url)
        }
        val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        return checkNotNull(contentLength) {
            "Missing valid ${HttpHeaders.ContentLength} header for url: $url"
        }
    }

    /**
     * Fetches one chunk and wraps transport/protocol failures as [ChunkDownloadException].
     *
     * @throws ChunkDownloadException when the request fails or returns an invalid status.
     */
    override suspend fun downloadChunk(url: String, startByte: Long, endByte: Long): ByteArray {
        val chunk = Chunk(index = UNKNOWN_CHUNK_INDEX, startByte = startByte, endByte = endByte)
        return runCatching<ByteArray> {
                val response =
                    client.get(url) {
                        headers.append(HttpHeaders.Range, "bytes=$startByte-$endByte")
                    }
                check(
                    response.status == HttpStatusCode.PartialContent ||
                        response.status == HttpStatusCode.OK
                ) {
                    "Range request failed with status ${response.status}"
                }
                response.body<ByteArray>()
            }
            .getOrElse { cause -> throw ChunkDownloadException(chunk, cause) }
    }

    /** Closes the underlying Ktor client. */
    override fun close() {
        client.close()
    }

    companion object {
        private const val BYTES_UNIT = "bytes"
        private const val UNKNOWN_CHUNK_INDEX = -1
    }
}
