package com.ziaee.frenchreader.net

import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

class LocalServerTest {
    @Test fun parsesInfo() {
        val info = parseComputerInfo("""{"id":"abcdef123456","name":"Mac (French Reader)","version":1,"services":{"llm":true,"tts":false}}""")
        assertEquals("abcdef123456", info.id)
        assertEquals("Mac", info.name)
        assertTrue(info.llm)
        assertFalse(info.tts)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsUnknownInfoVersion() {
        parseComputerInfo("""{"id":"abcdef123456","name":"Mac","version":2,"services":{"llm":true,"tts":false}}""")
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsBlankComputerId() {
        parseComputerInfo("""{"id":"","name":"Mac","version":1,"services":{"llm":true,"tts":false}}""")
    }
    @Test fun stripsOnlyDisplaySuffix() {
        assertEquals("Mac", computerDisplayName("Mac (French Reader)"))
        assertEquals("Mac", computerDisplayName("Mac"))
        assertEquals("(French Reader) Mac", computerDisplayName("(French Reader) Mac"))
    }
    @Test fun verifiesIdentityWithOnlyUnauthenticatedInfoGet() {
        withInfoServer { computer, requests ->
            verifyComputer(computer)
            assertEquals(listOf("GET /frenchreader/info"), requests)
        }
    }
    @Test(expected = IllegalStateException::class) fun rejectsMismatchedIdentity() {
        withInfoServer { computer, _ -> verifyComputer(computer.copy(id = "other")) }
    }
    @Test(expected = IllegalStateException::class) fun rejectsUnsuccessfulInfoResponse() {
        withInfoServer(status = 503) { computer, _ -> verifyComputer(computer) }
    }
    @Test(expected = IllegalStateException::class) fun doesNotFollowInfoRedirects() {
        withInfoServer(status = 302) { computer, _ -> verifyComputer(computer) }
    }
    /** A one-request HTTP server on a plain socket: com.sun.net.httpserver is not on this classpath. */
    private fun withInfoServer(status: Int = 200, block: (FoundComputer, MutableList<String>) -> Unit) {
        val requests = java.util.Collections.synchronizedList(mutableListOf<String>())
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val worker = thread {
            runCatching {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    val requestLine = reader.readLine().orEmpty()
                    val headers = generateSequence { reader.readLine()?.takeIf { it.isNotEmpty() } }.toList()
                    requests.add(requestLine.substringBeforeLast(" "))
                    val authenticated = headers.any {
                        it.startsWith("Authorization:", ignoreCase = true) || it.startsWith("X-Token:", ignoreCase = true)
                    }
                    val body = """{"id":"computer-id","name":"Mac","version":1,"services":{"llm":true,"tts":false}}"""
                    val code = if (authenticated) 401 else status
                    socket.getOutputStream().write(("HTTP/1.1 $code X\r\nLocation: /redirected\r\n" +
                        "Content-Type: application/json\r\nContent-Length: ${body.toByteArray().size}\r\n" +
                        "Connection: close\r\n\r\n$body").toByteArray())
                }
            }
        }
        try { block(FoundComputer("computer-id", "Mac", "127.0.0.1", server.localPort), requests) }
        finally { server.close(); worker.join(2_000) }
    }
    @Test fun buildsServiceUrlsIncludingIpv6() {
        assertEquals("http://h:8765/llm", computerBaseUrl("h", 8765, "llm"))
        assertEquals("http://h:8765/tts", computerBaseUrl("h", 8765, "tts"))
        assertEquals("http://[::1]:8765/tts", computerBaseUrl("::1", 8765, "tts"))
    }
}
