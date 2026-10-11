package com.ziaee.frenchreader.backup

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ziaee.frenchreader.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val TAG = "AutoBackup"
private const val AUTO_BACKUP_DIR = "backups"
private const val AUTO_BACKUP_PREFIX = "french_reader_auto_"
private const val AUTO_BACKUP_SUFFIX = ".zip"
private const val PROTECTED_BACKUP_PREFIX = "french_reader_keep_"
private const val AUTO_BACKUP_BUDGET_BYTES = 1024L * 1024 * 1024
private const val AUTO_BACKUP_ALWAYS_KEEP = 3
private const val DRIVE_UPLOAD_WORK_NAME = "auto_backup_drive_upload"
private const val DRIVE_UPLOAD_DELAY_SECONDS = 60L

internal fun autoBackupFileName(atMs: Long): String =
    AUTO_BACKUP_PREFIX + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(atMs)) + AUTO_BACKUP_SUFFIX

internal fun isAutoBackupFileName(name: String): Boolean =
    name.startsWith(AUTO_BACKUP_PREFIX) && name.endsWith(AUTO_BACKUP_SUFFIX)

/** The timestamp in the name sorts lexicographically, so newest first is a plain descending sort. */
internal fun sortAutoBackupsNewestFirst(names: List<String>): List<String> =
    names.filter(::isAutoBackupFileName).sortedDescending()

internal fun protectedBackupFileName(atMs: Long): String =
    autoBackupFileName(atMs).replace(AUTO_BACKUP_PREFIX, PROTECTED_BACKUP_PREFIX)

internal fun isProtectedBackupFileName(name: String): Boolean =
    name.startsWith(PROTECTED_BACKUP_PREFIX) && name.endsWith(AUTO_BACKUP_SUFFIX)

internal fun backupTimestamp(name: String): LocalDateTime? {
    val prefix = when {
        isAutoBackupFileName(name) -> AUTO_BACKUP_PREFIX
        isProtectedBackupFileName(name) -> PROTECTED_BACKUP_PREFIX
        else -> return null
    }
    return runCatching {
        LocalDateTime.parse(name.removePrefix(prefix).removeSuffix(AUTO_BACKUP_SUFFIX),
            DateTimeFormatter.ofPattern("uuuuMMdd_HHmmss").withResolverStyle(ResolverStyle.STRICT))
    }.getOrNull()
}

internal fun selectAutoBackupsToDelete(names: List<String>, now: LocalDateTime = LocalDateTime.now()): List<String> {
    val parsed = names.filter(::isAutoBackupFileName).mapNotNull { name -> backupTimestamp(name)?.let { name to it } }
    val keep = selectBackupsToKeep(parsed.map { it.second }, now)
    return parsed.filter { it.second !in keep }.map { it.first }
}

internal fun selectProtectedBackupsToDelete(names: List<String>): List<String> =
    names.filter(::isProtectedBackupFileName).filter { backupTimestamp(it) != null }.sortedDescending().drop(5)

/** Writes local snapshots when the app leaves the foreground and schedules Drive uploads. */
object AutoBackup {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    fun directory(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, AUTO_BACKUP_DIR)

    fun latest(context: Context): File? {
        val directory = directory(context)
        val names = directory.list()?.toList() ?: return null
        return sortAutoBackupsNewestFirst(names).filter { backupTimestamp(it) != null }.firstOrNull()?.let { File(directory, it) }
    }

    fun listBackups(context: Context): List<File> =
        directory(context).listFiles().orEmpty().filter {
            it.isFile && (isAutoBackupFileName(it.name) || isProtectedBackupFileName(it.name))
        }.sortedByDescending { backupTimestamp(it.name) ?: LocalDateTime.MIN }

    suspend fun withBackupLock(action: suspend () -> Boolean): Boolean = mutex.withLock { action() }

    suspend fun protectCurrentState(context: Context) {
        if (!hasUserData(context)) return
        val archive = LocalBackup.createArchive(context)
        try {
            writeArchive(context, archive, protected = true)
            pruneProtected(context)
        } finally {
            archive.delete()
        }
    }

    private fun writeArchive(context: Context, archive: File, protected: Boolean): File {
        val directory = directory(context).apply { mkdirs() }
        var atMs = System.currentTimeMillis()
        var target = File(directory, if (protected) protectedBackupFileName(atMs) else autoBackupFileName(atMs))
        while (target.exists()) {
            atMs += 1000
            target = File(directory, if (protected) protectedBackupFileName(atMs) else autoBackupFileName(atMs))
        }
        val staging = File.createTempFile(".backup-", ".tmp", directory)
        try {
            archive.copyTo(staging, overwrite = true)
            if (!staging.renameTo(target)) throw IOException("Unable to write backup")
        } finally {
            staging.delete()
        }
        return target
    }

    private fun pruneProtected(context: Context) {
        selectProtectedBackupsToDelete(directory(context).list()?.toList().orEmpty())
            .forEach { File(directory(context), it).delete() }
    }

    fun onAppBackgrounded(context: Context) {
        val appContext = context.applicationContext
        if (!BackupPrefs.isAutoBackupEnabled(appContext)) return
        scope.launch {
            runCatching { mutex.withLock { backUpIfChanged(appContext) } }
                .onFailure { Log.w(TAG, "Automatic backup failed", it) }
        }
    }

    private suspend fun backUpIfChanged(context: Context) {
        val startedAt = System.currentTimeMillis()
        val lastAt = BackupPrefs.getLastAutoBackupAtMs(context) ?: 0L
        if (databaseModifiedAt(context) <= lastAt) return
        // Never let an empty database (fresh install, failed migration) push the
        // good backups out of the rotation.
        if (!hasUserData(context)) return

        val current = readBackupCounts(context)
        val previous = BackupPrefs.getLastAutoBackupCounts(context)
        if (previous != null && isLargeLoss(previous, current)) {
            // Set the last good backup aside. If that fails the new backup is still written: the
            // time-based rotation keeps older ones, and a failing rename must not stop backups for good.
            latest(context)?.let { latest ->
                val target = File(latest.parentFile, latest.name.replace(AUTO_BACKUP_PREFIX, PROTECTED_BACKUP_PREFIX))
                if (target.exists() || !latest.renameTo(target)) Log.w(TAG, "Unable to protect ${latest.name}")
            }
            pruneProtected(context)
        }
        val archive = LocalBackup.createArchive(context)
        try {
            val directory = directory(context).apply { mkdirs() }
            writeArchive(context, archive, protected = false)
            BackupPrefs.setLastAutoBackupCounts(context, current)
            BackupPrefs.setLastAutoBackupAtMs(context, startedAt)
            selectAutoBackupsToDelete(directory.list()?.toList().orEmpty()).forEach { File(directory, it).delete() }
            trimToSizeBudget(directory)
            scheduleDriveUpload(context)
        } finally {
            archive.delete()
        }
    }

    /**
     * Keeping backups by date instead of by count means more files. A library with many images
     * makes each one large, so beyond [AUTO_BACKUP_BUDGET_BYTES] the oldest automatic backups go,
     * never the three newest and never the protected ones.
     */
    private fun trimToSizeBudget(directory: File) {
        val files = directory.listFiles().orEmpty()
            .filter { isAutoBackupFileName(it.name) && backupTimestamp(it.name) != null }
            .sortedByDescending { it.name }
        var total = files.sumOf { it.length() }
        files.drop(AUTO_BACKUP_ALWAYS_KEEP).asReversed().forEach { file ->
            if (total <= AUTO_BACKUP_BUDGET_BYTES) return
            val size = file.length()
            if (file.delete()) total -= size
        }
    }

    private fun databaseModifiedAt(context: Context): Long {
        val database = context.getDatabasePath("french_reader.db")
        return maxOf(database.lastModified(), File(database.path + "-wal").lastModified())
    }

    private fun hasUserData(context: Context): Boolean = readBackupCounts(context).let {
        it.vocab > 0 || it.texts > 0 || it.examAttempts > 0
    }

    /**
     * The upload runs through WorkManager because a backgrounded app loses its
     * network within seconds. The short delay with REPLACE debounces quick
     * app switches: only the newest backup is uploaded, once things settle.
     */
    private fun scheduleDriveUpload(context: Context) {
        if (BackupPrefs.getSignedInEmail(context) == null) return
        val request = OneTimeWorkRequestBuilder<DriveUploadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(DRIVE_UPLOAD_DELAY_SECONDS, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(DRIVE_UPLOAD_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}

/** Uploads the newest automatic backup to the user's Google Drive app folder. */
class DriveUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext
        if (BackupPrefs.getSignedInEmail(context) == null) return@withContext Result.success()
        val archive = AutoBackup.latest(context) ?: return@withContext Result.success()
        try {
            // Only upload when Drive access is already granted; a background run
            // must never pop up a consent screen.
            val outcome = GoogleAuthManager(context).requestDriveAuthorization()
            if (outcome !is AuthorizationOutcome.Authorized) return@withContext Result.success()
            DriveBackupClient.uploadBackup(outcome.accessToken, archive)
            BackupPrefs.setLastBackupAtMs(context, System.currentTimeMillis())
            Result.success()
        } catch (error: Exception) {
            Log.w(TAG, "Automatic Drive upload failed", error)
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }
}

internal fun readBackupCounts(context: Context): BackupCounts {
    val database = AppDatabase.get(context).openHelper.readableDatabase
    fun count(table: String): Long {
        if (table == "exam_attempts" && !database.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='exam_attempts'"
            ).use { it.moveToFirst() }) return 0
        return database.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getLong(0) }
    }
    return BackupCounts(count("vocab"), count("texts"), count("exam_attempts"))
}
