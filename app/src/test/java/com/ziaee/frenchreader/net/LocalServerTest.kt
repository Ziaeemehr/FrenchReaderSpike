package com.ziaee.frenchreader.net

import org.junit.Assert.*
import org.junit.Test

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
    @Test fun codeIsExactlySixAsciiDigits() {
        assertTrue(validComputerCode("012345"))
        for (code in listOf("12345", "1234567", "abcdef", "۱۲۳۴۵۶", " 123456")) assertFalse(validComputerCode(code))
    }
    @Test fun buildsServiceUrlsIncludingIpv6() {
        assertEquals("http://h:8765/llm", computerBaseUrl("h", 8765, "llm"))
        assertEquals("http://h:8765/tts", computerBaseUrl("h", 8765, "tts"))
        assertEquals("http://[::1]:8765/tts", computerBaseUrl("::1", 8765, "tts"))
    }
}
