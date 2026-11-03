package com.ziaee.frenchreader.backup

import java.io.File
import java.io.IOException

internal val REQUIRED_FILE_DIRECTORIES = listOf("text_bodies", "text_images")

/** A text-only/older archive does not own the live PDF directory. */
internal fun restoreFileDirectories(hasPdfs: Boolean): List<String> =
    REQUIRED_FILE_DIRECTORIES + if (hasPdfs) listOf("pdfs") else emptyList()

/** Records every move before the next can fail, so the caller can roll all of them back. */
internal fun stageBackupDirectories(
    filesDir: File, stagingRoot: File, directoryBackups: MutableList<Pair<File, File>>
) {
    restoreFileDirectories(File(stagingRoot, "pdfs").isDirectory).forEach { name ->
        val live = File(filesDir, name)
        val old = File(filesDir, ".$name.restore-old")
        old.deleteRecursively()
        if (live.exists() && !live.renameTo(old)) throw IOException("Unable to stage existing $name")
        directoryBackups += live to old
        if (!File(stagingRoot, name).renameTo(live)) throw IOException("Unable to restore $name")
    }
}
