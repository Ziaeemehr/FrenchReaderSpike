package com.ziaee.frenchreader.backup

import com.ziaee.frenchreader.util.MAX_BACKUP_ARCHIVE_BYTES
import com.ziaee.frenchreader.util.readBytesLimited
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private const val LEGACY_BACKUP_FILE_NAME = "frenchreader_backup.db"
private const val BACKUP_PREFIX = "frenchreader_backup"
private const val FILES_ENDPOINT = "https://www.googleapis.com/drive/v3/files"
private const val UPLOAD_ENDPOINT = "https://www.googleapis.com/upload/drive/v3/files"

object DriveBackupClient {
    fun findBackupFileId(accessToken: String): String? {
        return selectDriveBackups(listBackups(accessToken)).newest?.id
    }

    fun uploadBackup(accessToken: String, file: File) {
        if (file.length() > MAX_BACKUP_ARCHIVE_BYTES) throw IOException("Backup archive is too large")
        val boundary = "frenchreader-backup-${System.currentTimeMillis()}"
        val timestamp = LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val metadataJson = """{"name":"${BACKUP_PREFIX}_$timestamp.zip","parents":["appDataFolder"]}"""
        httpUpload("$UPLOAD_ENDPOINT?uploadType=multipart", accessToken, boundary, metadataJson, file)
        // Pruning old versions is best-effort: the new backup is already uploaded.
        runCatching {
            selectDriveBackups(listBackups(accessToken)).delete.forEach { httpDelete("$FILES_ENDPOINT/${it.id}", accessToken) }
        }
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

    private fun listBackups(accessToken: String): List<DriveBackupFile> {
        val url = "$FILES_ENDPOINT?spaces=appDataFolder&pageSize=1000&fields=files(id,name,createdTime)&orderBy=createdTime%20desc"
        return parseDriveBackupFiles(httpGet(url, accessToken))
    }

    private fun httpUpload(urlString: String, accessToken: String, boundary: String, metadataJson: String, file: File) {
        val prefix = multipartPrefix(metadataJson, boundary)
        val suffix = multipartSuffix(boundary)
        val connection = URL(urlString).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            connection.setFixedLengthStreamingMode(prefix.size.toLong() + file.length() + suffix.size)
            connection.outputStream.use { output ->
                output.write(prefix)
                file.inputStream().use { it.copyTo(output) }
                output.write(suffix)
            }
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

    private fun httpDelete(urlString: String, accessToken: String) {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.requestMethod = "DELETE"
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("Drive delete failed: HTTP $code")
        } finally { connection.disconnect() }
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
            return connection.inputStream.use { it.readBytesLimited(MAX_BACKUP_ARCHIVE_BYTES) }
        } finally {
            connection.disconnect()
        }
    }
}

internal data class DriveBackupFile(val id: String, val name: String, val createdTime: String)
internal data class DriveBackupSelection(
    val newest: DriveBackupFile?, val keep: List<DriveBackupFile>, val delete: List<DriveBackupFile>
)

internal fun parseDriveBackupFiles(responseJson: String): List<DriveBackupFile> {
    val files = JSONObject(responseJson).optJSONArray("files") ?: return emptyList()
    return (0 until files.length()).map { index ->
        files.getJSONObject(index).let { DriveBackupFile(it.optString("id"), it.optString("name"), it.optString("createdTime")) }
    }
}

internal fun selectDriveBackups(files: List<DriveBackupFile>): DriveBackupSelection {
    val ordered = files.filter { it.name.startsWith(BACKUP_PREFIX) }.sortedWith(
        compareBy<DriveBackupFile> { it.name == LEGACY_BACKUP_FILE_NAME }
            .thenByDescending { it.createdTime }.thenByDescending { it.name }
    )
    return DriveBackupSelection(ordered.firstOrNull(), ordered.take(5), ordered.drop(5))
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
    return multipartPrefix(metadataJson, boundary) + fileBytes + multipartSuffix(boundary)
}

private fun multipartPrefix(metadataJson: String, boundary: String): ByteArray = buildString {
        append("--").append(boundary).append("\r\n")
        append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
        append(metadataJson).append("\r\n")
        append("--").append(boundary).append("\r\n")
        append("Content-Type: application/octet-stream\r\n\r\n")
    }.toByteArray(Charsets.UTF_8)

private fun multipartSuffix(boundary: String) = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
