package com.ziaee.frenchreader.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ModelDownloaderTest {
    @Test
    fun verifyChecksumRejectsMismatch() {
        val file = File.createTempFile("model", ".gguf")
        file.writeText("hello world")
        assertFalse(ModelDownloader.verifyChecksum(file, "0000000000000000000000000000000000000000000000000000000000000000"))
        file.delete()
    }

    @Test
    fun verifyChecksumAcceptsCorrectlyComputedDigest() {
        val file = File.createTempFile("model", ".gguf")
        file.writeBytes("test content for checksum".toByteArray())
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }
        assertTrue(ModelDownloader.verifyChecksum(file, digest))
        file.delete()
    }
}
