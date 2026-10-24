package com.ziaee.frenchreader.watch

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** A small preference file, independent of the reading library and its Room schema. */
class WatchHistory(context: Context) {
    private val appContext = context.applicationContext
    private val prefs by lazy { appContext.getSharedPreferences("watch", Context.MODE_PRIVATE) }

    private val subtitlePrefs by lazy { appContext.getSharedPreferences("watch_prefs", Context.MODE_PRIVATE) }

    var subtitleSizePercent: Int
        get() = subtitlePrefs.getInt("subtitle_size_percent", 115).takeIf { it in listOf(85, 100, 115, 130, 150, 175) } ?: 115
        set(value) { subtitlePrefs.edit().putInt("subtitle_size_percent", value).commit() }

    fun read(): List<RecentVideo> = synchronized(lock) {
        runCatching {
            val array = JSONArray(prefs.getString("recents", "[]"))
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("videoId")
                if (YoutubeUrl.videoId(id) != id) return@mapNotNull null
                RecentVideo(id, item.optString("title"), item.optString("channel"), item.optLong("positionMs").coerceAtLeast(0))
            }.distinctBy { it.videoId }.take(30)
        }.getOrDefault(emptyList())
    }

    fun record(entry: RecentVideo) = synchronized(lock) { write(updateRecentVideos(read(), entry)) }
    fun remove(id: String) = synchronized(lock) { write(read().filterNot { it.videoId == id }) }
    var compactPlayer: Boolean
        get() = prefs.getBoolean("compact_player", false)
        set(value) { prefs.edit().putBoolean("compact_player", value).commit() }

    var translationEnabled: Boolean
        get() = prefs.getBoolean("translation_enabled", false)
        set(value) { prefs.edit().putBoolean("translation_enabled", value).commit() }

    // Application-lifetime FIFO: an exit flush survives ViewModel clearing and cannot be
    // overtaken by an older checkpoint. All preference access in these actions is on IO.
    suspend fun readLatest(): List<RecentVideo> = queued { read() }
    suspend fun removeLatest(id: String): List<RecentVideo> = queued { remove(id); read() }

    private suspend fun <T> queued(action: () -> T): T {
        val result = CompletableDeferred<T>()
        writes.send { result.completeWith(runCatching(action)) }
        return result.await()
    }

    fun recordAsync(entry: RecentVideo) { writes.trySend { record(entry) } }
    fun setSubtitleSizeAsync(value: Int) { writes.trySend { subtitleSizePercent = value } }
    fun setCompactAsync(value: Boolean) { writes.trySend { compactPlayer = value } }
    fun setTranslationAsync(value: Boolean) { writes.trySend { translationEnabled = value } }

    private fun write(entries: List<RecentVideo>) {
        val array = JSONArray()
        entries.forEach { item -> array.put(JSONObject().put("videoId", item.videoId).put("title", item.title)
            .put("channel", item.channel).put("positionMs", item.positionMs)) }
        prefs.edit().putString("recents", array.toString()).commit()
    }

    private companion object {
        val lock = Any()
        val writes = Channel<() -> Unit>(Channel.UNLIMITED)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scope ->
            scope.launch { for (write in writes) runCatching { write() } }
        }
    }
}
