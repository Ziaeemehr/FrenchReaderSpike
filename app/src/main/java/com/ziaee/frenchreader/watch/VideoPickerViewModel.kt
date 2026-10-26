package com.ziaee.frenchreader.watch

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ziaee.frenchreader.resources.DefaultResource
import com.ziaee.frenchreader.resources.YOUTUBE_CHANNEL_RESOURCES

// The app's own curated channels first (they carry levels and descriptions), then the extra picker-only ones.
internal val videoChannels = (YOUTUBE_CHANNEL_RESOURCES + EXTRA_YOUTUBE_CHANNELS.mapIndexed { index, channel ->
    DefaultResource(channel.title, "https://www.youtube.com/channel/${channel.channelId}", channel.category, 1_000L + index)
}).distinctBy { it.url }

data class VideoPickerState(
    val recent: List<RecentVideo> = emptyList(),
    val feeds: Map<String, List<ChannelVideo>> = emptyMap(),
    val selectedChannel: String? = null,
    val loading: Boolean = false,
    val error: Boolean = false
)

class VideoPickerViewModel(application: Application) : AndroidViewModel(application) {
    private val history = WatchHistory(application)
    private val directory = File(application.filesDir, "youtube_feeds")
    private val mutableState = MutableStateFlow(VideoPickerState())
    val uiState = mutableState.asStateFlow()
    private var feedJob: Job? = null

    init {
        refreshRecent()
        viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) {
                videoChannels.mapNotNull { channel ->
                    val id = channelId(channel.url)
                    readCache(id)?.let { id to it }
                }.toMap()
            }
            mutableState.update { it.copy(feeds = cached + it.feeds) }
        }
    }

    fun refreshRecent() {
        viewModelScope.launch {
            val recent = history.readLatest()
            mutableState.update { it.copy(recent = recent) }
        }
    }
    fun removeRecent(id: String) {
        viewModelScope.launch {
            val recent = history.removeLatest(id)
            mutableState.update { it.copy(recent = recent) }
        }
    }

    fun closeChannel() {
        feedJob?.cancel()
        mutableState.update { it.copy(selectedChannel = null, loading = false, error = false) }
    }

    fun openChannel(id: String, force: Boolean = false) {
        if (videoChannels.none { channelId(it.url) == id }) return
        feedJob?.cancel()
        mutableState.update { it.copy(selectedChannel = id, loading = true, error = false) }
        val publication = FeedCachePublication.begin(id)
        feedJob = viewModelScope.launch {
            try {
                val videos = withContext(Dispatchers.IO) {
                    val file = cacheFile(id)
                    val age = System.currentTimeMillis() - file.lastModified()
                    val cached = readCache(id)
                    if (!force && age in 0 until SIX_HOURS && cached != null) cached
                    else {
                        // YouTube's RSS feed returns 404 for many channels at times; the channel's Videos page lists the same uploads.
                        val rss = try { download("https://www.youtube.com/feeds/videos.xml?channel_id=$id") }
                            catch (error: CancellationException) { throw error } catch (_: Exception) { null }
                        val xml = rss?.takeIf { runCatching { parseYoutubeFeed(it) }.isSuccess }
                            ?: youtubeFeedXml(parseYoutubeChannelPage(download("https://www.youtube.com/channel/$id/videos?hl=en"), LocalDate.now())
                                .also { check(it.isNotEmpty()) })
                        parseYoutubeFeed(xml).also {
                            // A failed cache write must not discard a successfully downloaded feed.
                            currentCoroutineContext().ensureActive()
                            val coroutine = currentCoroutineContext()
                            runCatching {
                                directory.mkdirs()
                                val temporary = File.createTempFile("$id-", ".tmp", directory)
                                try {
                                    temporary.writeText(xml)
                                    FeedCachePublication.publish(id, publication) {
                                        coroutine.ensureActive()
                                        check(temporary.renameTo(file))
                                    }
                                } finally { temporary.delete() }
                            }.onFailure { error -> if (error is CancellationException) throw error }
                        }
                    }
                }
                mutableState.update { it.copy(feeds = it.feeds + (id to videos), loading = false) }
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) {
                val cached = withContext(Dispatchers.IO) { readCache(id) }
                mutableState.update { it.copy(loading = false, error = true,
                    feeds = if (cached == null) it.feeds else it.feeds + (id to cached)) }
            }
        }
    }

    private fun download(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept-Language", "en")
            check(connection.responseCode in 200..299)
            connection.inputStream.bufferedReader().use { reader ->
                val text = StringBuilder()
                val buffer = CharArray(8192)
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    text.append(buffer, 0, count)
                    check(text.length <= 2 * 1024 * 1024)
                }
                text.toString()
            }
        } finally { connection.disconnect() }
    }

    private fun cacheFile(id: String) = File(directory, "$id.xml")
    private fun readCache(id: String): List<ChannelVideo>? = runCatching {
        val file = cacheFile(id)
        check(file.length() <= 2 * 1024 * 1024)
        parseYoutubeFeed(file.readText())
    }.getOrNull()

    private companion object { const val SIX_HOURS = 6 * 60 * 60 * 1000L }
}

/** Shared across picker instances: starting a newer request invalidates any older publisher. */
private object FeedCachePublication {
    private val versions = mutableMapOf<String, Long>()
    @Synchronized fun begin(id: String): Long = ((versions[id] ?: 0L) + 1L).also { versions[id] = it }
    @Synchronized fun publish(id: String, version: Long, write: () -> Unit) {
        if (versions[id] == version) write()
    }
}
