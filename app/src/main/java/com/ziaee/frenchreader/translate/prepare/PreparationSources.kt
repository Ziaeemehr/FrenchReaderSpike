package com.ziaee.frenchreader.translate.prepare

import android.content.Context
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.data.AppDatabase
import com.ziaee.frenchreader.data.TextBodyStore
import com.ziaee.frenchreader.data.VocabPrefs
import com.ziaee.frenchreader.watch.CaptionCache
import com.ziaee.frenchreader.watch.WatchHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Uses exactly the text normalization and target languages used by the reader/watch/review. */
suspend fun loadPreparationSources(context: Context): List<PreparationSource> = withContext(Dispatchers.IO) {
    val database = AppDatabase.get(context)
    val bodyStore = TextBodyStore(context)
    val result = mutableListOf<PreparationSource>()
    for (doc in database.textDao().getAllOnce()) {
        result.add(libraryPreparationSource("text:${doc.id}", doc.title, bodyStore.read(doc), doc.translationLang))
    }
    val language = if (VocabPrefs.getMeaningLanguage(context) == VocabPrefs.MeaningLanguage.PERSIAN) "fa" else "en"
    val captionCache = CaptionCache(context.filesDir)
    for (video in WatchHistory(context).read()) {
        val captions = captionCache.read(video.videoId)?.captions ?: continue
        result.add(subtitlePreparationSource("video:${video.videoId}", video.title, captions.cues, language))
    }
    val cards = database.vocabDao().getAllOnce()
    result.addAll(flashcardPreparationSources(cards, language, context.getString(R.string.prepare_meanings),
        context.getString(R.string.prepare_examples), database.vocabListDao().getAllOnce(),
        context.getString(R.string.prepare_unfiled)))
    result
}
