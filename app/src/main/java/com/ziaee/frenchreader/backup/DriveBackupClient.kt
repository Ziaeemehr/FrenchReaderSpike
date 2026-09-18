package com.ziaee.frenchreader.backup

import org.json.JSONObject

internal fun parseBackupFileId(responseJson: String, fileName: String): String? {
    val files = JSONObject(responseJson).optJSONArray("files") ?: return null
    for (i in 0 until files.length()) {
        val file = files.getJSONObject(i)
        if (file.optString("name") == fileName) return file.optString("id")
    }
    return null
}

internal fun buildMultipartUploadBody(metadataJson: String, fileBytes: ByteArray, boundary: String): ByteArray {
    val prefix = buildString {
        append("--").append(boundary).append("\r\n")
        append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
        append(metadataJson).append("\r\n")
        append("--").append(boundary).append("\r\n")
        append("Content-Type: application/octet-stream\r\n\r\n")
    }.toByteArray(Charsets.UTF_8)
    val suffix = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
    return prefix + fileBytes + suffix
}
