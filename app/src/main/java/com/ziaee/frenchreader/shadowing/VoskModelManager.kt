package com.ziaee.frenchreader.shadowing

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

const val VOSK_MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-fr-0.22.zip"
private const val MARKER = ".installed"

fun isModelInstalled(dir: File): Boolean = File(dir, MARKER).exists()

object VoskModelInstaller {
    fun installFromZip(zip: InputStream, targetDir: File) {
        val tmp = File(targetDir.parentFile, targetDir.name + ".tmp")
        tmp.deleteRecursively()
        try {
            tmp.mkdirs()
            val root = tmp.canonicalFile
            var hadEntry = false
            ZipInputStream(zip).use { z ->
                while (true) {
                    val entry = z.nextEntry ?: break
                    hadEntry = true
                    // Strip the single top-level folder the Vosk zips ship with.
                    val relative = entry.name.substringAfter('/', "")
                    if (relative.isEmpty()) continue
                    val out = File(tmp, relative).canonicalFile
                    require(out.path.startsWith(root.path + File.separator)) {
                        "Bad zip entry: ${entry.name}"
                    }
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { z.copyTo(it) }
                    }
                }
            }
            check(hadEntry && File(tmp, "am/final.mdl").isFile) { "Not a Vosk model" }
            File(tmp, MARKER).writeText("ok")
            targetDir.deleteRecursively()
            check(tmp.renameTo(targetDir)) { "Could not move model into place" }
        } catch (e: Throwable) {
            tmp.deleteRecursively()
            throw e
        }
    }
}

class VoskModelManager(context: Context) {
    val modelDir: File = File(context.filesDir, "vosk/fr-small")

    fun isInstalled(): Boolean = isModelInstalled(modelDir)

    fun sizeBytes(): Long = modelDir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    suspend fun download(onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        modelDir.parentFile?.mkdirs()
        val zipFile = File(modelDir.parentFile, "model.zip.part")
        try {
            val conn = URL(VOSK_MODEL_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${conn.responseCode}")
            }
            val total = conn.contentLengthLong
            var done = 0L
            conn.inputStream.use { input ->
                zipFile.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
            if (total > 0 && done != total) throw IOException("Incomplete download")
            zipFile.inputStream().use { VoskModelInstaller.installFromZip(it, modelDir) }
        } finally {
            zipFile.delete()
        }
    }

    fun delete() {
        modelDir.deleteRecursively()
    }

    suspend fun deleteAndUnload() {
        VoskModels.forDir(modelDir).invalidate()
        delete()
    }
}
