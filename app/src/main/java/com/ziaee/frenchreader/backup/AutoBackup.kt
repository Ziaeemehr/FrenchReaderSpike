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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val TAG = "AutoBackup"
private const val AUTO_BACKUP_DIR = "backups"
private const val AUTO_BACKUP_PREFIX = "french_reader_auto_"
private const val AUTO_BACKUP_SUFFIX = ".zip"
internal const val AUTO_BACKUP_KEEP = 5
private const val DRIVE_UPLOAD_WORK_NAME = "auto_backup_drive_upload"
private const val DRIVE_UPLOAD_DELAY_SECONDS = 60L

internal fun autoBackupFileName(atMs: Long): String =
    AUTO_BACKUP_PREFIX + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(atMs)) + AUTO_BACKUP_SUFFIX

internal fun isAutoBackupFileName(name: String): Boolean =
    name.startsWith(AUTO_BACKUP_PREFIX) && name.endsWith(AUTO_BACKUP_SUFFIX)

/** The timestamp in the name sorts lexicographically, so newest first is a plain descending sort. */
internal fun sortAutoBackupsNewestFirst(names: List<String>): List<String> =
    names.filter(::isAutoBackupFileName).sortedDescending()

internal fun selectAutoBackupsToDelete(names: List<String>, keep: Int = AUTO_BACKUP_KEEP): List<String> =
    sortAutoBackupsNewestFirst(names).drop(keep)

/**
 * Anki-style safety net: every time the app goes to the background and the
 * database changed since the previous run, write a full backup next to the
 * app's files (keeping the last [AUTO_BACKUP_KEEP]) and, when the user is
 * signed in for cloud backup, schedule an upload of it to Google Drive.
 */
object AutoBackup {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    fun directory(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, AUTO_BACKUP_DIR)

    fun latest(context: Context): File? {
        val directory = directory(context)
        val names = directory.list()?.toList() ?: return null
        return sortAutoBackupsNewestFirst(names).firstOrNull()?.let { File(directory, it) }
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

        val archive = LocalBackup.createArchive(context)
        try {
            val directory = directory(context).apply { mkdirs() }
            archive.copyTo(File(directory, autoBackupFileName(startedAt)), overwrite = true)
            BackupPrefs.setLastAutoBackupAtMs(context, startedAt)
            selectAutoBackupsToDelete(directory.list()?.toList().orEmpty()).forEach { File(directory, it).delete() }
            scheduleDriveUpload(context)
        } finally {
            archive.delete()
        }
    }

    private fun databaseModifiedAt(context: Context): Long {
        val database = context.getDatabasePath("french_reader.db")
        return maxOf(database.lastModified(), File(database.path + "-wal").lastModified())
    }

    private fun hasUserData(context: Context): Boolean =
        AppDatabase.get(context).openHelper.readableDatabase
            .query("SELECT (SELECT COUNT(*) FROM vocab) + (SELECT COUNT(*) FROM texts)")
            .use { cursor -> cursor.moveToFirst() && cursor.getLong(0) > 0 }

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
