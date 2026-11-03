package com.ziaee.frenchreader.backup

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class BackupFileDirectoriesTest {
    @Test fun `text-only archive preserves live PDFs and replaces bodies and images`() = withDirectories { live, staging ->
        stageBackupDirectories(live, staging, mutableListOf())
        assertEquals("original PDF", File(live, "pdfs/shared.pdf").readText())
        assertEquals("restored text_bodies", File(live, "text_bodies/new").readText())
        assertEquals("restored text_images", File(live, "text_images/new").readText())
        assertFalse(File(live, ".pdfs.restore-old").exists())
    }

    @Test fun `archive containing PDFs swaps PDFs and retains old files for rollback`() = withDirectories { live, staging ->
        File(staging, "pdfs").mkdirs()
        File(staging, "pdfs/restored.pdf").writeText("restored PDF")
        val moved = mutableListOf<Pair<File, File>>()
        stageBackupDirectories(live, staging, moved)
        assertEquals("restored PDF", File(live, "pdfs/restored.pdf").readText())
        assertEquals("original PDF", File(live, ".pdfs.restore-old/shared.pdf").readText())
        assertEquals(3, moved.size)
        moved.asReversed().forEach { (current, old) -> current.deleteRecursively(); old.renameTo(current) }
        assertEquals("original PDF", File(live, "pdfs/shared.pdf").readText())
    }

    @Test fun `explicitly empty PDF directory in archive replaces PDFs`() = withDirectories { live, staging ->
        File(staging, "pdfs").mkdirs()
        stageBackupDirectories(live, staging, mutableListOf())
        assertTrue(File(live, "pdfs").isDirectory)
        assertTrue(File(live, "pdfs").listFiles()!!.isEmpty())
    }

    private fun withDirectories(block: (File, File) -> Unit) {
        val root = Files.createTempDirectory("pdf-restore-test").toFile()
        try {
            val live = File(root, "live").apply { mkdirs() }
            val staging = File(root, "staging").apply { mkdirs() }
            File(live, "pdfs").mkdirs()
            File(live, "pdfs/shared.pdf").writeText("original PDF")
            listOf("text_bodies", "text_images").forEach { name ->
                File(live, name).mkdirs()
                File(live, "$name/old").writeText("original $name")
                File(staging, name).mkdirs()
                File(staging, "$name/new").writeText("restored $name")
            }
            block(live, staging)
        } finally { root.deleteRecursively() }
    }
}
