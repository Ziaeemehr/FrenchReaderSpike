package com.ziaee.frenchreader.translate.prepare

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.work.*
import com.ziaee.frenchreader.translate.TranslationOverrides
import com.ziaee.frenchreader.translate.llm.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object PreparationWork {
    const val NAME = "prepare_llm_translations"
    fun enqueue(context: Context, id: String) {
        val request = OneTimeWorkRequestBuilder<PreparationWorker>()
            .setInputData(workDataOf("job" to id))
            // USB reverse and LAN servers can be reachable without an Android network.
            // HTTP uses the same paired-address rediscovery as live translation.
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request).result.get()
    }
    suspend fun pause(context: Context) = withContext(Dispatchers.IO) {
        PreparationStore(context).pause()
        WorkManager.getInstance(context).cancelUniqueWork(NAME).result.get()
        Unit
    }
}

class PreparationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Blocking HTTP can outlive cancellation; never let Resume overlap the old physical request.
        requestMutex.withLock {
            val store = PreparationStore(applicationContext)
            val job = store.job() ?: return@withLock Result.success()
            if (job.id != inputData.getString("job")) return@withLock Result.success()
            if (!store.progress().allowsRequest(job.id)) return@withLock Result.success()
            var stage = "initialization"
            fun retry(error: Exception? = null): Result {
                // Never log exception messages/details, URLs, content, credentials or stack traces.
                val reason = if (error is LlmError) error.kind.name else if (error == null) "TIME_SLICE" else "LOCAL_FAILURE"
                val type = error?.javaClass?.simpleName ?: "none"
                Log.w("PreparationWorker", "Retry: reason=$reason type=$type stage=$stage attempt=$runAttemptCount")
                store.update(job.id, PreparationStatus.RETRY)
                return Result.retry()
            }
            try {
                val cache = LlmCache(applicationContext)
                val overrides = TranslationOverrides(applicationContext)
                val bundled = com.ziaee.frenchreader.content.BundledSentenceTranslations.forContext(applicationContext)
                fun complete(item: PreparationItem) = item.overridden(overrides) ||
                    item.prepared(cache, job.model, bundled, job.regenerateBundledExamples)
                val started = SystemClock.elapsedRealtime()
                var activeTranslator: LlmTranslator? = null
                fun engine(): LlmTranslator {
                    val translator = LlmTranslator.forContext(applicationContext)
                    if (translator == null || translator.cacheId != job.model) throw EngineChanged()
                    // Keep the HTTP client's last resolved address across batches.
                    return activeTranslator ?: translator.also { activeTranslator = it }
                }
                suspend fun translate(batch: List<PreparationItem>) {
                    currentCoroutineContext().ensureActive()
                    if (!store.progress().allowsRequest(job.id)) throw Paused()
                    val pending = pendingItems(batch, ::complete)
                    if (pending.isEmpty()) return
                    // Re-form batches after cache skips so context never crosses a missing/cached gap.
                    if (pending.size != batch.size) {
                        preparationBatches(pending).forEach { translate(it) }
                        return
                    }
                    // Also bound recursive BAD_RESPONSE splits, not just outer batches.
                    if (SystemClock.elapsedRealtime() - started > 180_000) throw TimeSlice()
                    val translator = engine()
                    // HTTP performs the same reachability/rediscovery as live translation.
                    // A separate socket probe can reject scoped NSD IPv6 addresses that HTTP accepts.
                    currentCoroutineContext().ensureActive()
                    if (!store.progress().allowsRequest(job.id)) throw Paused()
                    engine()
                    val first = batch.first()
                    stage = "completion"
                    val values = try {
                        if (first.kind == PreparationKind.MEANING)
                            listOf(translator.meaning(first.text, first.sentence, first.language))
                        else translator.translateLines(batch.map { it.text }, first.before, batch.last().after, first.language)
                    } catch (error: LlmError) {
                        if (error.kind == LlmError.Kind.BAD_RESPONSE && batch.size > 1) {
                            splitPreparationBatch(batch).forEach { translate(it) }
                            return
                        }
                        throw error
                    }
                    currentCoroutineContext().ensureActive()
                    engine() // Do not publish to a model that the user has switched away from during HTTP.
                    stage = "cache_publication"
                    if (!store.publishIfActive(job.id) {
                        batch.zip(values).forEach { (item, value) ->
                            if (item.kind == PreparationKind.MEANING) cache.putMeaning(item.text, item.sentence, item.language, job.model, value)
                            else {
                                cache.put(item.text, item.language, job.model, value)
                                if (job.regenerateBundledExamples && item.bundledTranslation(bundled) != null)
                                    cache.putRegenerated(item.text, item.language, job.model, value)
                            }
                        }
                    }) throw Paused()
                    val done = job.items.count(::complete)
                    store.update(job.id, PreparationStatus.RUNNING, done)
                    setProgress(workDataOf("done" to done, "total" to job.items.size))
                }
                engine()
                store.update(job.id, PreparationStatus.RUNNING, job.items.count(::complete))
                for (batch in preparationBatches(pendingItems(job.items, ::complete))) {
                    translate(batch)
                }
                store.update(job.id, PreparationStatus.COMPLETE, job.items.size)
                Result.success()
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: TimeSlice) { retry()
            } catch (_: Paused) { Result.success()
            } catch (_: EngineChanged) {
                store.update(job.id, PreparationStatus.ENGINE_CHANGED)
                Result.success()
            } catch (error: Exception) {
                retry(error)
            }
        }
    }
    private class TimeSlice : Exception()
    private class EngineChanged : Exception()
    private class Paused : Exception()
    companion object { private val requestMutex = Mutex() }
}
