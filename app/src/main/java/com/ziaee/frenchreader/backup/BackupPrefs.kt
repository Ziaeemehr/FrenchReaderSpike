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
}

internal fun formatBackupTimestamp(atMs: Long, locale: Locale = Locale.getDefault()): String =
    SimpleDateFormat("MMM d, yyyy, HH:mm", locale).format(Date(atMs))

fun formatLastBackupLabel(context: Context, lastBackupAtMs: Long?): String =
    if (lastBackupAtMs == null) context.getString(R.string.backup_never_backed_up)
    else formatBackupTimestamp(lastBackupAtMs)
