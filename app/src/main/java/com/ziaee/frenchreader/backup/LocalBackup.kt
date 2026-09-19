package com.ziaee.frenchreader.backup

import android.content.Context
import android.net.Uri
import com.ziaee.frenchreader.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

fun isValidSqliteBackup(bytes: ByteArray): Boolean =
    bytes.size >= SQLITE_HEADER.size &&
        SQLITE_HEADER.indices.all { bytes[it] == SQLITE_HEADER[it] }

object LocalBackup {
    suspend fun exportTo(context: Context, uri: Uri) {
        withContext(Dispatchers.IO) {
            val db = AppDatabase.get(context)
            AppDatabase.checkpointWal(db)
            val dbFile = context.getDatabasePath("french_reader.db")
            val output = context.contentResolver.openOutputStream(uri)
                ?: throw IOException("Unable to open backup destination")
            output.use { dbFile.inputStream().use { input -> input.copyTo(it) } }
        }
    }

    suspend fun importFrom(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Unable to open backup file")
        val bytes = input.use { it.readBytes() }
        if (!isValidSqliteBackup(bytes)) return@withContext false

        val dbFile = context.getDatabasePath("french_reader.db")
        AppDatabase.closeForRestore()
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
        dbFile.writeBytes(bytes)
        true
    }
}
