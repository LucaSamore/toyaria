package it.toyaria

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import it.toyaria.controller.KtorFileServerClient
import it.toyaria.controller.ServerDoesNotSupportRangesException
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking

class HttpClientTest {
    @Test
    fun `getFileSize and downloadChunk use range requests`() = runBlocking {
        val payload = "hello world".encodeToByteArray()
        val lastRange = AtomicReference<String>()
        val server = startServer(payload, supportsRanges = true, lastRange = lastRange)
        val url = "http://localhost:${server.address.port}/file"

        KtorFileServerClient().use { client ->
            assertEquals(payload.size.toLong(), client.getFileSize(url))
            val bytes = client.downloadChunk(url, startByte = 0L, endByte = 4L)
            assertContentEquals("hello".encodeToByteArray(), bytes)
            assertEquals("bytes=0-4", lastRange.get())
        }

        server.stop(0)
    }

    @Test
    fun `getFileSize fails when accept ranges is missing`() = runBlocking {
        val payload = "hello world".encodeToByteArray()
        val server = startServer(payload, supportsRanges = false, lastRange = AtomicReference())
        val url = "http://localhost:${server.address.port}/file"

        KtorFileServerClient().use { client ->
            assertFailsWith<ServerDoesNotSupportRangesException> { client.getFileSize(url) }
        }

        server.stop(0)
    }

    private fun startServer(
        payload: ByteArray,
        supportsRanges: Boolean,
        lastRange: AtomicReference<String>,
    ): HttpServer {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/file") { exchange ->
            when (exchange.requestMethod) {
                "HEAD" -> handleHead(exchange, payload, supportsRanges)
                "GET" -> handleGet(exchange, payload, supportsRanges, lastRange)
                else -> exchange.sendResponseHeaders(405, -1)
            }
            exchange.close()
        }
        server.start()
        return server
    }

    private fun handleHead(exchange: HttpExchange, payload: ByteArray, supportsRanges: Boolean) {
        if (supportsRanges) {
            exchange.responseHeaders.add("Accept-Ranges", "bytes")
        }
        exchange.responseHeaders.add("Content-Length", payload.size.toString())
        exchange.sendResponseHeaders(200, -1)
    }

    private fun handleGet(
        exchange: HttpExchange,
        payload: ByteArray,
        supportsRanges: Boolean,
        lastRange: AtomicReference<String>,
    ) {
        val rangeHeader = exchange.requestHeaders.getFirst("Range").orEmpty()
        lastRange.set(rangeHeader)
        if (!supportsRanges || !rangeHeader.startsWith("bytes=")) {
            exchange.sendResponseHeaders(416, -1)
            return
        }
        val bounds = rangeHeader.removePrefix("bytes=").split("-")
        val start = bounds[0].toInt()
        val end = bounds[1].toInt()
        val body = payload.copyOfRange(start, end + 1)
        exchange.responseHeaders.add("Content-Length", body.size.toString())
        exchange.sendResponseHeaders(206, body.size.toLong())
        exchange.responseBody.write(body)
    }
}
