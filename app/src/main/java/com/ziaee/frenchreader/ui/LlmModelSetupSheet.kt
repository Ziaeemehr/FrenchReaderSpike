package com.ziaee.frenchreader.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ziaee.frenchreader.data.LlmAssistantPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object ModelDownloader {
    const val MODEL_URL =
        "https://huggingface.co/bartowski/Qwen_Qwen3-0.6B-GGUF/resolve/main/Qwen_Qwen3-0.6B-Q4_K_M.gguf"
    const val EXPECTED_SHA256 =
        "9acfc1e001311f34b4252001b626f2e466d592a42065f66571bff3790d4e1b14"
    const val EXPECTED_SIZE_BYTES = 484_220_320L

    fun modelFile(context: Context): File =
        File(File(context.filesDir, "llm_models"), "qwen3-0.6b-q4_k_m.gguf")

    fun verifyChecksum(file: File, expectedSha256: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return actual == expectedSha256
    }

    // Downloads to a .part temp file, verifies checksum, then renames into place.
    // Returns true on success; on any failure the .part file is deleted and false is returned.
    suspend fun download(context: Context, onProgress: (Float) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            val target = modelFile(context)
            target.parentFile?.mkdirs()
            val tempFile = File(target.parentFile, "${target.name}.part")

            try {
                val connection = URL(MODEL_URL).openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.connect()
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext false

                val total = connection.contentLengthLong.takeIf { it > 0 } ?: EXPECTED_SIZE_BYTES
                var downloaded = 0L
                connection.inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress(downloaded.toFloat() / total.toFloat())
                        }
                    }
                }

                if (!verifyChecksum(tempFile, EXPECTED_SHA256)) {
                    tempFile.delete()
                    return@withContext false
                }

                tempFile.renameTo(target)
            } catch (e: Exception) {
                tempFile.delete()
                false
            }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LlmModelSetupSheet(context: Context, onDismiss: () -> Unit, onDownloaded: () -> Unit) {
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Text("Assistant IA hors ligne")
            Spacer(modifier = Modifier.height(8.dp))
            Text("Télécharge le modèle Qwen3 0.6B (461,79 Mio). Fonctionne entièrement hors ligne, rien n'est envoyé à un serveur.")
            Spacer(modifier = Modifier.height(16.dp))
            if (downloading) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.padding(vertical = 8.dp))
            }
            errorMessage?.let { Text(it) }
            Button(
                enabled = !downloading,
                onClick = {
                    downloading = true
                    errorMessage = null
                    scope.launch {
                        val success = ModelDownloader.download(context) { p -> progress = p }
                        downloading = false
                        if (success) {
                            LlmAssistantPrefs.setModelDownloaded(context, true)
                            onDownloaded()
                        } else {
                            errorMessage = "Le téléchargement a échoué. Réessaie."
                        }
                    }
                },
            ) {
                Text("Télécharger")
            }
        }
    }
}
