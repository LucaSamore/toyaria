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

interface FileServerClient : Closeable {
    suspend fun getFileSize(url: String): Long

    suspend fun downloadChunk(url: String, startByte: Long, endByte: Long): ByteArray
}

class KtorFileServerClient(
    private val client: HttpClient = HttpClient(CIO) { expectSuccess = false }
) : FileServerClient {
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

    override fun close() {
        client.close()
    }

    companion object {
        private const val BYTES_UNIT = "bytes"
        private const val UNKNOWN_CHUNK_INDEX = -1
    }
}
