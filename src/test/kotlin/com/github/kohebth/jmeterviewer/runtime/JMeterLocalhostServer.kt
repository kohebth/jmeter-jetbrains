package com.github.kohebth.jmeterviewer.runtime

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList

internal class JMeterLocalhostServer : AutoCloseable {
    private val server = HttpServer.create(
        InetSocketAddress(InetAddress.getByName(HOST), 0),
        0,
    )
    private val recordedRequests = CopyOnWriteArrayList<RecordedRequest>()
    private val handlerFailures = CopyOnWriteArrayList<Throwable>()

    val port: Int
        get() = server.address.port

    val requests: List<RecordedRequest>
        get() = recordedRequests.toList()

    val failures: List<Throwable>
        get() = handlerFailures.toList()

    init {
        server.createContext("/") { exchange ->
            try {
                handle(exchange)
            } catch (failure: Throwable) {
                handlerFailures += failure
                exchange.close()
            }
        }
        server.start()
    }

    private fun handle(exchange: HttpExchange) {
        try {
            val requestBody = exchange.requestBody.use { input ->
                String(input.readBytes(), StandardCharsets.UTF_8)
            }
            recordedRequests += RecordedRequest(
                method = exchange.requestMethod,
                path = exchange.requestURI.path,
                requestHeader = exchange.requestHeaders.getFirst(REQUEST_HEADER),
                body = requestBody,
            )

            val response = responseFor(exchange.requestURI.path)
            val bytes = response.body.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", response.contentType)
            exchange.responseHeaders.add(RESPONSE_HEADER, RESPONSE_HEADER_VALUE)
            exchange.sendResponseHeaders(response.status, bytes.size.toLong())
            exchange.responseBody.write(bytes)
        } finally {
            // The supported Java 17 HttpExchange is not AutoCloseable.
            exchange.close()
        }
    }

    private fun responseFor(path: String): LocalResponse = when {
        path == "/login" -> LocalResponse(
            contentType = JSON_CONTENT_TYPE,
            body = "{\"token\":\"smoke-token\"}",
        )
        path.startsWith("/profiles/") -> LocalResponse(
            contentType = HTML_CONTENT_TYPE,
            body = "<html><head><title>Smoke Profile</title></head></html>",
        )
        path == "/catalog" -> LocalResponse(
            contentType = JSON_CONTENT_TYPE,
            body = "{\"items\":[{\"id\":\"product-1\"}]}",
        )
        path.startsWith("/products/") -> LocalResponse(
            contentType = JSON_CONTENT_TYPE,
            body = "{\"id\":\"product-1\",\"status\":\"available\"}",
        )
        path == "/health" -> LocalResponse(
            contentType = JSON_CONTENT_TYPE,
            body = HEALTH_RESPONSE,
        )
        path == "/metrics" -> LocalResponse(
            contentType = TEXT_CONTENT_TYPE,
            body = "smoke_requests_total 1\n",
        )
        else -> LocalResponse(
            status = 404,
            contentType = TEXT_CONTENT_TYPE,
            body = "not found",
        )
    }

    override fun close() {
        server.stop(0)
    }

    internal data class RecordedRequest(
        val method: String,
        val path: String,
        val requestHeader: String?,
        val body: String,
    )

    private data class LocalResponse(
        val status: Int = 200,
        val contentType: String,
        val body: String,
    )

    companion object {
        const val HOST = "127.0.0.1"
        const val REQUEST_HEADER = "X-Smoke-Request"
        const val REQUEST_HEADER_VALUE = "request-42"
        const val RESPONSE_HEADER = "X-Smoke-Response"
        const val RESPONSE_HEADER_VALUE = "response-42"
        const val HEALTH_RESPONSE = "{\"status\":\"ok\",\"requestId\":\"request-42\"}"

        private const val JSON_CONTENT_TYPE = "application/json; charset=UTF-8"
        private const val HTML_CONTENT_TYPE = "text/html; charset=UTF-8"
        private const val TEXT_CONTENT_TYPE = "text/plain; charset=UTF-8"
    }
}
