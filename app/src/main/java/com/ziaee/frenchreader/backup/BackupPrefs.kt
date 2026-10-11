package com.ziaee.frenchreader.backup

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.ziaee.frenchreader.R

private const val PREFS_NAME = "backup_prefs"
private const val KEY_SIGNED_IN_EMAIL = "signed_in_email"
private const val KEY_LAST_BACKUP_AT_MS = "last_backup_at_ms"
private const val KEY_AUTO_BACKUP_ENABLED = "auto_backup_enabled"
private const val KEY_LAST_AUTO_BACKUP_AT_MS = "last_auto_backup_at_ms"

object BackupPrefs {
    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSignedInEmail(context: Context): String? =
        prefs(context).getString(KEY_SIGNED_IN_EMAIL, null)

    fun setSignedInEmail(context: Context, email: String?) {
        prefs(context).edit().putString(KEY_SIGNED_IN_EMAIL, email).apply()
    }

    fun getLastBackupAtMs(context: Context): Long? {
        val value = prefs(context).getLong(KEY_LAST_BACKUP_AT_MS, -1L)
        return if (value == -1L) null else value
    }

    fun setLastBackupAtMs(context: Context, atMs: Long) {
        prefs(context).edit().putLong(KEY_LAST_BACKUP_AT_MS, atMs).apply()
    }

    fun isAutoBackupEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_BACKUP_ENABLED, true)

    fun setAutoBackupEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_BACKUP_ENABLED, enabled).apply()
    }

    internal fun getLastAutoBackupCounts(context: Context): BackupCounts? {
        val prefs = prefs(context)
        if (!prefs.contains("auto_count_vocab")) return null
        return BackupCounts(prefs.getLong("auto_count_vocab", 0), prefs.getLong("auto_count_texts", 0),
            prefs.getLong("auto_count_exam_attempts", 0))
    }

    internal fun setLastAutoBackupCounts(context: Context, counts: BackupCounts) {
        if (!prefs(context).edit().putLong("auto_count_vocab", counts.vocab)
                .putLong("auto_count_texts", counts.texts).putLong("auto_count_exam_attempts", counts.examAttempts)
                .commit()) throw java.io.IOException("Unable to remember backup counts")
    }

    /** After a restore the remembered counts describe other data; the next backup starts afresh. */
    internal fun clearLastAutoBackupCounts(context: Context) {
        prefs(context).edit().remove("auto_count_vocab").remove("auto_count_texts").remove("auto_count_exam_attempts").apply()
    }

    fun getLastAutoBackupAtMs(context: Context): Long? =
        prefs(context).getLong(KEY_LAST_AUTO_BACKUP_AT_MS, -1L).takeIf { it != -1L }

    fun setLastAutoBackupAtMs(context: Context, atMs: Long) {
        prefs(context).edit().putLong(KEY_LAST_AUTO_BACKUP_AT_MS, atMs).apply()
    }
}

internal fun formatBackupTimestamp(atMs: Long, locale: Locale = Locale.getDefault()): String =
    SimpleDateFormat("MMM d, yyyy, HH:mm", locale).format(Date(atMs))

fun formatLastBackupLabel(context: Context, lastBackupAtMs: Long?): String =
    if (lastBackupAtMs == null) context.getString(R.string.backup_never_backed_up)
    else formatBackupTimestamp(lastBackupAtMs)
