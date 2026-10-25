package com.ziaee.frenchreader.translate.llm

import java.io.EOFException
import java.net.ServerSocket
import java.net.SocketException
import kotlin.concurrent.thread
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class LlmClientRediscoveryTest {
    @Test fun coldClientRediscoversAndKeepsAddressAcrossBatchesAndRestart() {
        withServer { url, requests ->
            var savedAddress = "http://127.0.0.1:1"
            var discoveries = 0
            fun client() = LlmClient(savedAddress, "model", null, true, refindBaseUrl = {
                discoveries++
                // Mirrors LlmTranslator's persisted LocalServerPrefs.update on rediscovery.
                savedAddress = url
                savedAddress
            })
            val workerClient = client()
            repeat(2) { assertEquals("ok", workerClient.complete(LlmPrompt("s", "u")).getString("meaning")) }
            assertEquals(1, discoveries)
            assertEquals(2, requests.get())
            assertEquals("ok", client().complete(LlmPrompt("s", "u")).getString("meaning"))
            assertEquals(1, discoveries)
        }
    }

    @Test fun rediscoveryMayConfirmSameAddressAfterTransientFailure() {
        var discoveries = 0
        var server: FakeServer? = null
        val port = ServerSocket().use {
            it.bind(InetSocketAddress("127.0.0.1", 0))
            it.localPort
        }
        val url = "http://127.0.0.1:$port/llm"
        val client = LlmClient(url, "model", null, true, refindBaseUrl = {
            discoveries++
            server = FakeServer(port)
            url
        })
        try {
            assertEquals("ok", client.complete(LlmPrompt("s", "u")).getString("meaning"))
            assertEquals(1, discoveries)
        } finally { server?.close() }
    }

    private fun withServer(block: (String, AtomicInteger) -> Unit) {
        FakeServer().use { server ->
            block("http://127.0.0.1:${server.port}/llm", server.requests)
        }
    }

    private class FakeServer(port: Int = 0) : AutoCloseable {
        val requests = AtomicInteger()
        private val socket = ServerSocket().apply {
            bind(InetSocketAddress("127.0.0.1", port))
        }
        val port: Int get() = socket.localPort
        private val worker = thread(name = "llm-test-server", isDaemon = true) {
            try {
                while (!socket.isClosed) {
                    socket.accept().use { client ->
                        client.soTimeout = 5_000
                        val input = client.getInputStream().buffered()
                        fun readLine(): String {
                            val line = StringBuilder()
                            while (true) {
                                val byte = input.read()
                                if (byte == -1) throw EOFException("Incomplete HTTP request")
                                if (byte == 10) return line.toString().removeSuffix("\r")
                                line.append(byte.toChar())
                            }
                        }
                        readLine() // Request line.
                        var contentLength = 0
                        while (true) {
                            val header = readLine()
                            if (header.isEmpty()) break
                            if (header.substringBefore(':').equals("Content-Length", ignoreCase = true)) {
                                contentLength = header.substringAfter(':').trim().toInt()
                            }
                        }
                        val buffer = ByteArray(4096)
                        var remaining = contentLength
                        while (remaining > 0) {
                            val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                            if (count == -1) throw EOFException("Incomplete HTTP body")
                            remaining -= count
                        }
                        requests.incrementAndGet()
                        val body = """{"choices":[{"message":{"content":"{\"meaning\":\"ok\"}"}}]}"""
                            .toByteArray(Charsets.UTF_8)
                        client.getOutputStream().use { output ->
                            output.write(("HTTP/1.1 200 OK\r\n" +
                                "Content-Type: application/json\r\n" +
                                "Content-Length: ${body.size}\r\n" +
                                "Connection: close\r\n\r\n").toByteArray(Charsets.US_ASCII))
                            output.write(body)
                            output.flush()
                        }
                    }
                }
            } catch (error: SocketException) {
                if (!socket.isClosed) throw error
            }
        }

        override fun close() {
            socket.close()
            worker.join(6_000)
            check(!worker.isAlive) { "Fake HTTP server did not stop" }
        }
    }
}
