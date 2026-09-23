package com.ziaee.frenchreader.util

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

internal const val MAX_TEXT_IMPORT_BYTES = 20L * 1024 * 1024
internal const val MAX_BACKUP_ARCHIVE_BYTES = 256L * 1024 * 1024

internal fun InputStream.readBytesLimited(maxBytes: Long): ByteArray {
    require(maxBytes >= 0) { "maxBytes must not be negative" }
    val initialSize = minOf(maxBytes, DEFAULT_BUFFER_SIZE.toLong()).toInt()
    return ByteArrayOutputStream(initialSize).use { output ->
        copyToLimited(output, maxBytes)
        output.toByteArray()
    }
}

internal fun InputStream.copyToLimited(output: OutputStream, maxBytes: Long): Long {
    require(maxBytes >= 0) { "maxBytes must not be negative" }
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val remaining = maxBytes - total
        if (remaining == 0L) {
            if (read() < 0) return total
            throw IOException("Input exceeds the $maxBytes byte limit")
        }
        val requested = minOf(buffer.size.toLong(), remaining).toInt()
        val count = read(buffer, 0, requested)
        if (count < 0) return total
        if (count == 0) continue
        output.write(buffer, 0, count)
        total += count
    }
}
