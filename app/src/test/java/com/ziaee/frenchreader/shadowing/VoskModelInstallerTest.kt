package com.ziaee.frenchreader.shadowing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class VoskModelInstallerTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            entries.forEach { (name, body) -> z.putNextEntry(ZipEntry(name)); z.write(body.toByteArray()); z.closeEntry() }
        }
        return out.toByteArray()
    }

    @Test fun installsAndStripsTopFolder() {
        val target = File(tmp.root, "vosk-fr")
        VoskModelInstaller.installFromZip(
            ByteArrayInputStream(zipOf("vosk-model-small-fr-0.22/am/final.mdl" to "x", "vosk-model-small-fr-0.22/conf/model.conf" to "y")),
            target
        )
        assertEquals("x", File(target, "am/final.mdl").readText())
        assertTrue(isModelInstalled(target))
        assertFalse(File(tmp.root, "vosk-fr.tmp").exists())
    }

    @Test fun truncatedZip_leavesNothingInstalled() {
        val target = File(tmp.root, "vosk-fr")
        val bytes = zipOf("m/am/final.mdl" to "x".repeat(10_000))
        runCatching { VoskModelInstaller.installFromZip(ByteArrayInputStream(bytes.copyOf(bytes.size / 2)), target) }
        assertFalse(isModelInstalled(target))
        assertFalse(File(tmp.root, "vosk-fr.tmp").exists())
    }

    @Test fun rejectsZipSlipEntries() {
        val target = File(tmp.root, "vosk-fr")
        val result = runCatching {
            VoskModelInstaller.installFromZip(ByteArrayInputStream(zipOf("m/../../evil.txt" to "x")), target)
        }
        assertTrue(result.isFailure)
        assertFalse(File(tmp.root.parentFile, "evil.txt").exists())
    }
}
