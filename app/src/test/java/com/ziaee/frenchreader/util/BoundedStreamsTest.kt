package com.ziaee.frenchreader.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BoundedStreamsTest {
    @Test
    fun `readBytesLimited accepts input at the limit`() {
        val bytes = byteArrayOf(1, 2, 3, 4)

        assertArrayEquals(bytes, ByteArrayInputStream(bytes).readBytesLimited(4))
    }

    @Test
    fun `readBytesLimited rejects input beyond the limit`() {
        assertThrows(IOException::class.java) {
            ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5)).readBytesLimited(4)
        }
    }

    @Test
    fun `copyToLimited does not write bytes beyond the limit`() {
        val destination = ByteArrayOutputStream()

        assertThrows(IOException::class.java) {
            ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5)).copyToLimited(destination, 4)
        }
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), destination.toByteArray())
    }
}
