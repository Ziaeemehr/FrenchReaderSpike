package com.ziaee.frenchreader.translate.prepare

import android.content.Context
import android.util.AtomicFile
import com.ziaee.frenchreader.translate.TranslationOverrides
import com.ziaee.frenchreader.translate.llm.LlmCache
import java.io.File
import java.util.UUID

enum class PreparationStatus { QUEUED, RUNNING, RETRY, PAUSED, COMPLETE, ENGINE_CHANGED }
data class PreparationJob(val id: String, val model: String, val items: List<PreparationItem>,
    val selectedDecks: Set<Long?>? = null, val regenerateBundledExamples: Boolean = false)
data class PreparationProgress(val id: String, val status: PreparationStatus, val done: Int, val total: Int)

/** Terminal, paused and superseded jobs must never issue another provider request. */
internal fun PreparationProgress?.allowsRequest(jobId: String): Boolean =
    this != null && id == jobId && status in setOf(PreparationStatus.QUEUED, PreparationStatus.RUNNING, PreparationStatus.RETRY)

/** The immutable approved snapshot stays off WorkManager's size-limited Data payload. No keys/tokens. */
class PreparationStore(context: Context) {
    private val prefs = context.getSharedPreferences("translation_preparation", Context.MODE_PRIVATE)
    private val snapshot = AtomicFile(File(context.filesDir, "translation_preparation.json"))

    fun regenerateBundledExamples(): Boolean = prefs.getBoolean("regenerateBundledExamples", false)
    fun setRegenerateBundledExamples(value: Boolean) { prefs.edit().putBoolean("regenerateBundledExamples", value).apply() }

    fun selection(): Set<String> = prefs.getStringSet("selection", emptySet()).orEmpty().toSet()
    fun select(ids: Set<String>) { prefs.edit().putStringSet("selection", ids.toSet()).apply() }

    // Missing key is the pre-deck-selection format, which selected all cards.
    fun selectedDecks(): Set<Long?>? = decodePreparationDeckSelection(prefs.getStringSet("decks", null))
    fun selectDecks(ids: Set<Long?>?) {
        prefs.edit().apply {
            if (ids == null) remove("decks")
            else putStringSet("decks", encodePreparationDeckSelection(ids))
        }.apply()
    }

    fun job(): PreparationJob? = synchronized(lock) {
        if (!prefs.contains("id")) return@synchronized null
        runCatching {
            val job = decodePreparationJob(snapshot.openRead().bufferedReader().use { it.readText() })
            job.takeIf { it.id == prefs.getString("id", null) }
        }.getOrNull()
    }

    fun create(model: String, items: List<PreparationItem>, selectedDecks: Set<Long?>? = null, regenerateBundledExamples: Boolean = false): PreparationJob = synchronized(lock) {
        val job = PreparationJob(UUID.randomUUID().toString(), model, items, selectedDecks, regenerateBundledExamples)
        val stream = snapshot.startWrite()
        try {
            stream.write(encodePreparationJob(job).toByteArray(Charsets.UTF_8))
            snapshot.finishWrite(stream)
        } catch (error: Exception) { snapshot.failWrite(stream); throw error }
        check(prefs.edit().putString("id", job.id).putString("status", PreparationStatus.QUEUED.name)
            .putInt("done", 0).putInt("total", items.size).commit())
        job
    }

    fun progress(): PreparationProgress? = synchronized(lock) {
        val id = prefs.getString("id", null) ?: return@synchronized null
        PreparationProgress(id, runCatching { PreparationStatus.valueOf(prefs.getString("status", "PAUSED")!!) }
            .getOrDefault(PreparationStatus.PAUSED), prefs.getInt("done", 0), prefs.getInt("total", 0))
    }

    fun update(id: String, status: PreparationStatus, done: Int? = null) = synchronized(lock) {
        val current = progress()
        if (current?.id == id && (current.status != PreparationStatus.PAUSED || status == PreparationStatus.QUEUED)) {
            prefs.edit().putString("status", status.name).apply { if (done != null) putInt("done", done) }.commit()
        }
    }

    fun pause() = synchronized(lock) {
        prefs.edit().putString("status", PreparationStatus.PAUSED.name).commit(); Unit
    }
    fun discard() = synchronized(lock) {
        prefs.edit().remove("id").remove("status").remove("done").remove("total").commit()
        snapshot.delete()
    }

    /** Serializes cancellation/clear/new-job with publication of a completed HTTP response. */
    fun publishIfActive(id: String, publish: () -> Unit): Boolean = synchronized(lock) {
        val progress = progress()
        if (!progress.allowsRequest(id)) false
        else { publish(); true }
    }

    fun clearCache(cache: LlmCache) = synchronized(lock) { pause(); cache.clearAll() }
    companion object { private val lock = Any() }
}

fun PreparationItem.cached(cache: LlmCache, model: String): Boolean = if (kind == PreparationKind.MEANING)
    cache.getMeaning(text, sentence, language, model) != null else cache.get(text, language, model) != null

fun PreparationItem.overridden(overrides: TranslationOverrides): Boolean = overrides.getOverride(text, language) != null

/** Group identity distinguishes flashcard examples from reader lines with identical text. */
fun PreparationItem.bundledTranslation(index: com.ziaee.frenchreader.content.BundledSentenceTranslations): String? =
    if (kind == PreparationKind.LINE && group.startsWith("example:")) index.get(text, language) else null

fun PreparationItem.prepared(cache: LlmCache, model: String, index: com.ziaee.frenchreader.content.BundledSentenceTranslations,
    regenerate: Boolean): Boolean = examplePrepared(bundledTranslation(index) != null, regenerate,
        personal = { cache.getRegenerated(text, language, model) != null }, ordinary = { cached(cache, model) })
