package com.ziaee.frenchreader.util

import android.content.Context
import android.content.Intent

/**
 * One installed app that can handle Android's ACTION_PROCESS_TEXT (the same
 * mechanism behind Chrome/GBoard's "Ask ChatGPT" / "Anki Card" / "Reverso
 * Context" entries in the text-selection menu).
 */
data class ProcessTextApp(
    val label: String,
    val packageName: String,
    val activityName: String,
    val appLabel: String
)

/** Drops [ownPackageName] (avoids listing this app in its own "more" sheet --
 * relevant once this app registers itself as a PROCESS_TEXT target too),
 * disambiguates entries that happen to share the same [ProcessTextApp.label]
 * (e.g. Samsung's own "Translate" and the Google Translate app both use that
 * exact label) by appending the app's own name, and sorts the rest by label
 * so the sheet reads the same every time, with Google Translate pinned first. */
fun filterAndSortProcessTextApps(apps: List<ProcessTextApp>, ownPackageName: String): List<ProcessTextApp> {
    val filtered = apps.filter { it.packageName != ownPackageName }
    val labelCounts = filtered.groupingBy { it.label }.eachCount()
    return filtered
        .map { app ->
            if (labelCounts.getValue(app.label) > 1) app.copy(label = "${app.label} (${app.appLabel})") else app
        }
        .sortedWith(compareBy<ProcessTextApp> { it.packageName != GOOGLE_TRANSLATE_PACKAGE }.thenBy { it.label.lowercase() })
}

private const val GOOGLE_TRANSLATE_PACKAGE = "com.google.android.apps.translate"


/** Queries the PackageManager for every app that can handle ACTION_PROCESS_TEXT
 * (the framework's own text-selection-toolbar mechanism), excluding this app. */
fun queryProcessTextApps(context: Context): List<ProcessTextApp> {
    val intent = Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
    val packageManager = context.packageManager
    val apps = packageManager.queryIntentActivities(intent, 0).map { resolveInfo ->
        ProcessTextApp(
            label = resolveInfo.loadLabel(packageManager).toString(),
            packageName = resolveInfo.activityInfo.packageName,
            activityName = resolveInfo.activityInfo.name,
            appLabel = resolveInfo.activityInfo.applicationInfo.loadLabel(packageManager).toString()
        )
    }
    return filterAndSortProcessTextApps(apps, context.packageName)
}

/** Sends [text] to [app] via ACTION_PROCESS_TEXT, read-only (this app doesn't
 * offer to replace the original selection with whatever comes back). */
fun launchProcessTextApp(context: Context, app: ProcessTextApp, text: String) {
    val intent = Intent(Intent.ACTION_PROCESS_TEXT).apply {
        setClassName(app.packageName, app.activityName)
        type = "text/plain"
        putExtra(Intent.EXTRA_PROCESS_TEXT, text)
        putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
    }
    context.startActivity(intent)
}
