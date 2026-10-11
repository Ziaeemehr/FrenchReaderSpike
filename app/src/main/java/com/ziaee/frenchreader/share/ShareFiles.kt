package com.ziaee.frenchreader.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.OutputStream

private const val SHARE_DIR = "shared"
private const val STALE_AFTER_MS = 24L * 60 * 60 * 1000

/**
 * Writes a file into the cache folder exposed by the FileProvider and returns a send intent for it.
 * Files shared more than a day ago are removed first.
 * Call off the main thread.
 */
fun shareFileIntent(context: Context, fileName: String, mimeType: String, title: String, write: (OutputStream) -> Unit): Intent {
    val dir = File(context.cacheDir, SHARE_DIR)
    // The app a file was shared to may read it later (a mail draft, an upload queue), so only
    // files from earlier days are cleared.
    val staleBefore = System.currentTimeMillis() - STALE_AFTER_MS
    dir.listFiles()?.filter { it.lastModified() < staleBefore }?.forEach { it.delete() }
    dir.mkdirs()
    val file = File(dir, fileName)
    file.outputStream().use(write)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
    return Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, title)
        // Without ClipData the chooser's own preview cannot read the file on Android 10+.
        clipData = ClipData.newRawUri(title, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
