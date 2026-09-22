package com.ziaee.frenchreader.backup

import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val BACKUP_FILE_NAME = "frenchreader_backup.db"
private const val FILES_ENDPOINT = "https://www.googleapis.com/drive/v3/files"
private const val UPLOAD_ENDPOINT = "https://www.googleapis.com/upload/drive/v3/files"

object DriveBackupClient {
    fun findBackupFileId(accessToken: String): String? {
        val url = "$FILES_ENDPOINT?spaces=appDataFolder&fields=files(id,name)"
        val json = httpGet(url, accessToken)
        return parseBackupFileId(json, BACKUP_FILE_NAME)
    }

    fun uploadBackup(accessToken: String, existingFileId: String?, file: File) {
        val boundary = "frenchreader-backup-${System.currentTimeMillis()}"
        val metadataJson = if (existingFileId == null) {
            """{"name":"$BACKUP_FILE_NAME","parents":["appDataFolder"]}"""
        } else {
            """{"name":"$BACKUP_FILE_NAME"}"""
        }
        val body = buildMultipartUploadBody(metadataJson, file.readBytes(), boundary)
        val url = if (existingFileId == null) "$UPLOAD_ENDPOINT?uploadType=multipart"
            else "$UPLOAD_ENDPOINT/$existingFileId?uploadType=multipart"
        val method = if (existingFileId == null) "POST" else "PATCH"
        httpUpload(url, accessToken, method, boundary, body)
    }

    fun downloadBackup(accessToken: String, fileId: String): ByteArray {
        val url = "$FILES_ENDPOINT/$fileId?alt=media"
        return httpDownload(url, accessToken)
    }

    private fun httpGet(urlString: String, accessToken: String): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            if (code !in 200..299) {
                connection.errorStream?.use { it.readBytes() }
                throw IOException("Drive files.list failed: HTTP $code")
            }
            return connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            connection.disconnect()
        }
    }

    private fun httpUpload(urlString: String, accessToken: String, method: String, boundary: String, body: ByteArray) {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.requestMethod = method
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            if (code !in 200..299) {
                connection.errorStream?.use { it.readBytes() }
                throw IOException("Drive upload failed: HTTP $code")
            }
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun httpDownload(urlString: String, accessToken: String): ByteArray {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            val code = connection.responseCode
            if (code !in 200..299) {
                connection.errorStream?.use { it.readBytes() }
                throw IOException("Drive download failed: HTTP $code")
            }
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }
}

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
