package com.ziaee.frenchreader.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.ziaee.frenchreader.data.AppDatabase
import com.ziaee.frenchreader.util.MAX_BACKUP_ARCHIVE_BYTES
import com.ziaee.frenchreader.util.copyToLimited
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
private const val DATABASE_ENTRY = "database/french_reader.db"
private val FILE_DIRECTORIES = listOf("text_bodies", "text_images")
private const val MAX_BACKUP_ENTRIES = 20_000

fun isValidSqliteBackup(bytes: ByteArray): Boolean =
    bytes.size >= SQLITE_HEADER.size && SQLITE_HEADER.indices.all { bytes[it] == SQLITE_HEADER[it] }

object LocalBackup {
    suspend fun exportTo(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val archive = createArchive(context)
        try {
            val output = context.contentResolver.openOutputStream(uri)
                ?: throw IOException("Unable to open backup destination")
            output.use { destination -> archive.inputStream().use { it.copyTo(destination) } }
        } finally {
            archive.delete()
        }
    }

    suspend fun createArchive(context: Context): File = withContext(Dispatchers.IO) {
        val db = AppDatabase.get(context)
        AppDatabase.checkpointWal(db)
        val archive = File.createTempFile("french_reader_backup_", ".zip", context.cacheDir)
        try {
            ZipOutputStream(FileOutputStream(archive)).use { zip ->
                addFile(zip, context.getDatabasePath("french_reader.db"), DATABASE_ENTRY)
                FILE_DIRECTORIES.forEach { name -> addDirectory(zip, File(context.filesDir, name), name) }
            }
            archive
        } catch (error: Exception) {
            archive.delete()
            throw error
        }
    }

    suspend fun importFrom(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Unable to open backup file")
        val archive = File.createTempFile("french_reader_restore_", ".backup", context.cacheDir)
        try {
            input.use { source -> archive.outputStream().use { source.copyToLimited(it, MAX_BACKUP_ARCHIVE_BYTES) } }
            restoreArchive(context, archive)
        } finally {
            archive.delete()
        }
    }

    suspend fun importBytes(context: Context, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        if (bytes.size > MAX_BACKUP_ARCHIVE_BYTES) throw IOException("Backup archive is too large")
        val archive = File.createTempFile("french_reader_drive_restore_", ".backup", context.cacheDir)
        try {
            archive.writeBytes(bytes)
            restoreArchive(context, archive)
        } finally {
            archive.delete()
        }
    }

    private fun restoreArchive(context: Context, archive: File): Boolean {
        val stagingRoot = File(context.filesDir, ".backup-restore-${UUID.randomUUID()}")
        val candidateDb = File(stagingRoot, DATABASE_ENTRY)
        try {
            stagingRoot.mkdirs()
            val includesFiles = isZip(archive)
            if (includesFiles) extractArchive(archive, stagingRoot) else {
                candidateDb.parentFile?.mkdirs()
                archive.copyTo(candidateDb, overwrite = true)
            }
            if (!validateDatabase(candidateDb)) return false
            if (includesFiles) FILE_DIRECTORIES.forEach { File(stagingRoot, it).mkdirs() }
            swapIntoPlace(context, candidateDb, stagingRoot, includesFiles)
            return true
        } finally {
            stagingRoot.deleteRecursively()
        }
    }

    private fun swapIntoPlace(context: Context, candidateDb: File, stagingRoot: File, includesFiles: Boolean) {
        val dbFile = context.getDatabasePath("french_reader.db")
        val dbNew = File(dbFile.parentFile, ".${dbFile.name}.restore-new")
        val dbOld = File(dbFile.parentFile, ".${dbFile.name}.restore-old")
        val directoryBackups = mutableListOf<Pair<File, File>>()
        dbNew.delete()
        dbOld.delete()
        candidateDb.copyTo(dbNew, overwrite = true)
        AppDatabase.closeForRestore()
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
        try {
            if (includesFiles) FILE_DIRECTORIES.forEach { name ->
                val live = File(context.filesDir, name)
                val old = File(context.filesDir, ".$name.restore-old")
                old.deleteRecursively()
                if (live.exists() && !live.renameTo(old)) throw IOException("Unable to stage existing $name")
                directoryBackups += live to old
                if (!File(stagingRoot, name).renameTo(live)) throw IOException("Unable to restore $name")
            }
            if (dbFile.exists() && !dbFile.renameTo(dbOld)) throw IOException("Unable to stage existing database")
            if (!dbNew.renameTo(dbFile)) throw IOException("Unable to restore database")
            dbOld.delete()
            directoryBackups.forEach { (_, old) -> old.deleteRecursively() }
        } catch (error: Exception) {
            if (!dbFile.exists() && dbOld.exists()) dbOld.renameTo(dbFile)
            directoryBackups.asReversed().forEach { (live, old) ->
                live.deleteRecursively()
                if (old.exists()) old.renameTo(live)
            }
            throw error
        } finally {
            dbNew.delete()
        }
    }

    private fun validateDatabase(file: File): Boolean {
        if (!file.isFile) return false
        val headerOk = FileInputStream(file).use { input ->
            val header = ByteArray(SQLITE_HEADER.size)
            input.read(header) == header.size && header.contentEquals(SQLITE_HEADER)
        }
        if (!headerOk) return false
        val database = try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        } catch (_: Exception) {
            return false
        }
        return try {
            val integrityOk = database.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)
            }
            val tables = database.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('vocab','texts')", null
            ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }
            integrityOk && tables.containsAll(setOf("vocab", "texts"))
        } catch (_: Exception) {
            false
        } finally {
            database.close()
        }
    }

    private fun extractArchive(archive: File, stagingRoot: File) {
        val canonicalRoot = stagingRoot.canonicalFile
        var entryCount = 0
        var extractedBytes = 0L
        ZipInputStream(FileInputStream(archive)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                if (entryCount > MAX_BACKUP_ENTRIES) throw IOException("Backup contains too many entries")
                val output = File(canonicalRoot, entry.name).canonicalFile
                val allowed = output == File(canonicalRoot, DATABASE_ENTRY).canonicalFile ||
                    FILE_DIRECTORIES.any { name ->
                        val directory = File(canonicalRoot, name).canonicalFile
                        output == directory || output.path.startsWith(directory.path + File.separator)
                    }
                if (!allowed) { zip.closeEntry(); continue }
                if (entry.isDirectory) output.mkdirs() else {
                    output.parentFile?.mkdirs()
                    output.outputStream().use {
                        extractedBytes += zip.copyToLimited(it, MAX_BACKUP_ARCHIVE_BYTES - extractedBytes)
                    }
                }
                zip.closeEntry()
            }
        }
    }

    private fun addDirectory(zip: ZipOutputStream, directory: File, entryRoot: String) {
        if (!directory.isDirectory) return
        directory.walkTopDown().filter(File::isFile).forEach { file ->
            addFile(zip, file, "$entryRoot/${file.relativeTo(directory).invariantSeparatorsPath}")
        }
    }

    private fun addFile(zip: ZipOutputStream, file: File, entryName: String) {
        if (!file.isFile) throw IOException("Missing backup file: ${file.name}")
        zip.putNextEntry(ZipEntry(entryName))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun isZip(file: File): Boolean = file.inputStream().use { input ->
        val signature = ByteArray(4)
        input.read(signature) == 4 && signature.contentEquals(
            byteArrayOf(0x50.toByte(), 0x4b.toByte(), 0x03.toByte(), 0x04.toByte())
        )
    }
}
