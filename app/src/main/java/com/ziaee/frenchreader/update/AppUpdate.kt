package com.ziaee.frenchreader.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A published GitHub release: [version] without the leading "v", [notes] as written on GitHub. */
data class ReleaseInfo(val version: String, val notes: String, val pageUrl: String)

/** Same link as the README's download button: always the newest release's APK. */
const val UPDATE_DOWNLOAD_URL =
    "https://github.com/Ziaeemehr/FrenchReaderSpike/releases/latest/download/FrenchReader.apk"
private const val LATEST_RELEASE_API =
    "https://api.github.com/repos/Ziaeemehr/FrenchReaderSpike/releases/latest"

private const val DAY_MS = 24L * 60 * 60 * 1000
internal const val SNOOZE_MS = 3 * DAY_MS

/** "v0.9.0" / "0.9" / "1.2.3-beta" -> [0, 9, 0] / [0, 9] / [1, 2, 3]; null if not a version. */
internal fun parseVersion(value: String): List<Int>? {
    val parts = value.trim().removePrefix("v").removePrefix("V").substringBefore('-').split('.')
    if (parts.isEmpty() || parts.size > 4) return null
    return parts.map { it.toIntOrNull() ?: return null }
}

/** True when [latest] is a strictly higher version than [current] (missing parts count as 0). */
fun isNewerVersion(latest: String, current: String): Boolean {
    val a = parseVersion(latest) ?: return false
    val b = parseVersion(current) ?: return false
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

/** Reads GitHub's "latest release" JSON; drafts and pre-releases are ignored. */
internal fun parseLatestRelease(json: String): ReleaseInfo? {
    val root = JSONObject(json)
    if (root.optBoolean("draft") || root.optBoolean("prerelease")) return null
    val tag = root.optString("tag_name").takeIf { parseVersion(it) != null } ?: return null
    return ReleaseInfo(
        version = tag.removePrefix("v").removePrefix("V"),
        notes = root.optString("body").trim(),
        pageUrl = root.optString("html_url")
    )
}

private val NOTES_HEADINGS = setOf("what's new", "whats new", "changelog", "changes", "nouveautés", "تازه‌ها")

/** Release notes are Markdown; show them as plain text in the dialog. A leading "What's new"
 * heading is dropped, since the dialog already has one. */
internal fun plainReleaseNotes(markdown: String, maxChars: Int = 1500): String =
    markdown.lines()
        .map { line -> line.trimEnd().replace(Regex("^#{1,6}\\s*"), "").replace("**", "").replace("`", "") }
        .dropWhile { it.isBlank() }
        .let { lines -> if (lines.firstOrNull()?.trim()?.trimEnd(':')?.lowercase() in NOTES_HEADINGS) lines.drop(1) else lines }
        .joinToString("\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
        .let { if (it.length > maxChars) it.take(maxChars).trimEnd() + "…" else it }

/** Automatic check: at most once a day, not while snoozed ("Later"), and only if enabled. */
internal fun shouldAutoCheck(enabled: Boolean, nowMs: Long, lastCheckMs: Long, snoozedUntilMs: Long): Boolean =
    enabled && nowMs - lastCheckMs >= DAY_MS && nowMs >= snoozedUntilMs

/** Whether to show the update dialog for [release]; a version the user skipped stays quiet. */
internal fun shouldPrompt(release: ReleaseInfo, currentVersion: String, skippedVersion: String?): Boolean =
    isNewerVersion(release.version, currentVersion) && release.version != skippedVersion

/** One request to GitHub's public API; null on any network or parse failure. */
suspend fun fetchLatestRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
    runCatching {
        val connection = URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "FrenchReaderApp")
        try {
            if (connection.responseCode !in 200..299) null
            else connection.inputStream.bufferedReader().use { parseLatestRelease(it.readText()) }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

@Suppress("DEPRECATION")
fun installedVersionName(context: Context): String = runCatching {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    info.versionName.orEmpty()
}.getOrDefault("")

object UpdatePrefs {
    private const val PREFS_NAME = "app_update"
    private const val KEY_AUTO_CHECK = "auto_check"
    private const val KEY_LAST_CHECK = "last_check_ms"
    private const val KEY_SNOOZED_UNTIL = "snoozed_until_ms"
    private const val KEY_SKIPPED_VERSION = "skipped_version"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isAutoCheckEnabled(context: Context) = prefs(context).getBoolean(KEY_AUTO_CHECK, true)
    fun setAutoCheckEnabled(context: Context, value: Boolean) = prefs(context).edit().putBoolean(KEY_AUTO_CHECK, value).apply()
    fun lastCheckMs(context: Context) = prefs(context).getLong(KEY_LAST_CHECK, 0L)
    fun setLastCheckMs(context: Context, value: Long) = prefs(context).edit().putLong(KEY_LAST_CHECK, value).apply()
    fun snoozedUntilMs(context: Context) = prefs(context).getLong(KEY_SNOOZED_UNTIL, 0L)
    fun snooze(context: Context, nowMs: Long) = prefs(context).edit().putLong(KEY_SNOOZED_UNTIL, nowMs + SNOOZE_MS).apply()
    fun skippedVersion(context: Context): String? = prefs(context).getString(KEY_SKIPPED_VERSION, null)
    fun skip(context: Context, version: String) = prefs(context).edit().putString(KEY_SKIPPED_VERSION, version).apply()
}
