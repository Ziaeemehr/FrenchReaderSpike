package com.ziaee.frenchreader.tts

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import java.io.File
import java.io.InterruptedIOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap

/**
 * Audio files that are still being written by a streaming synthesis. The
 * player reads such a file as an open-ended stream: when it reaches the
 * current end it waits for more bytes instead of treating it as the end of
 * the audio.
 */
object GrowingAudioFiles {
    private val growing = ConcurrentHashMap.newKeySet<String>()

    fun begin(file: File) { growing.add(file.absolutePath) }

    fun finish(file: File) { growing.remove(file.absolutePath) }

    fun isGrowing(path: String?): Boolean = path != null && growing.contains(File(path).absolutePath)
}

private const val GROWTH_POLL_MS = 40L

@UnstableApi
private class GrowingFileDataSource : BaseDataSource(/* isNetwork = */ false) {
    private var file: RandomAccessFile? = null
    private var uri: Uri? = null
    private var path: String? = null
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        path = dataSpec.uri.path
        transferInitializing(dataSpec)
        // Sample the flag before the length: once it is false every byte is on disk.
        val growing = GrowingAudioFiles.isGrowing(path)
        val opening = RandomAccessFile(path, "r")
        file = opening
        opening.seek(dataSpec.position)
        opened = true
        transferStarted(dataSpec)
        return if (growing) C.LENGTH_UNSET.toLong() else (opening.length() - dataSpec.position).coerceAtLeast(0)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val source = file ?: return C.RESULT_END_OF_INPUT
        while (true) {
            val growing = GrowingAudioFiles.isGrowing(path)
            val available = source.length() - source.filePointer
            if (available > 0) {
                val read = source.read(buffer, offset, minOf(length.toLong(), available).toInt())
                if (read > 0) bytesTransferred(read)
                return read
            }
            if (!growing) return C.RESULT_END_OF_INPUT
            try {
                Thread.sleep(GROWTH_POLL_MS)
            } catch (_: InterruptedException) {
                throw InterruptedIOException()
            }
        }
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        try {
            file?.close()
        } finally {
            file = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }
}

/**
 * Plays everything the default way, except files registered in
 * [GrowingAudioFiles] at the moment they are queued: those go through
 * [GrowingFileDataSource], with index seeking so jumping between the
 * sentences already received works although the total length is unknown.
 */
@UnstableApi
class TtsMediaSourceFactory(context: Context) : MediaSource.Factory {
    private val default = DefaultMediaSourceFactory(context)
    private val growing = ProgressiveMediaSource.Factory(
        DataSource.Factory { GrowingFileDataSource() },
        DefaultExtractorsFactory().setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING)
    )

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val uri = mediaItem.localConfiguration?.uri
        val isGrowing = uri != null && uri.scheme == "file" && GrowingAudioFiles.isGrowing(uri.path)
        return if (isGrowing) growing.createMediaSource(mediaItem) else default.createMediaSource(mediaItem)
    }

    override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider): MediaSource.Factory = apply {
        default.setDrmSessionManagerProvider(provider)
        growing.setDrmSessionManagerProvider(provider)
    }

    override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy): MediaSource.Factory = apply {
        default.setLoadErrorHandlingPolicy(policy)
        growing.setLoadErrorHandlingPolicy(policy)
    }

    override fun getSupportedTypes(): IntArray = default.supportedTypes
}
