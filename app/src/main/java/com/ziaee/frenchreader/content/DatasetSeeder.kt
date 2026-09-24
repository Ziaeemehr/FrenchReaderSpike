package com.ziaee.frenchreader.content

import android.content.Context
import android.util.Log
import com.ziaee.frenchreader.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object DatasetSeeder {
    private const val TAG = "DatasetSeeder"
    private const val PREFS_NAME = "dataset"
    private const val KEY_SEEDED_VERSION = "dataset_seeded_version"
    private const val KEY_SEED_PENDING = "dataset_seed_pending"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    fun seedIfNeeded(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            mutex.withLock {
                try {
                    seed(appContext)
                } catch (error: Exception) {
                    Log.e(TAG, "Unable to seed the built-in dataset", error)
                }
            }
        }
    }

    private suspend fun seed(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pending = prefs.getBoolean(KEY_SEED_PENDING, false)
        if (prefs.contains(KEY_SEEDED_VERSION) && !pending) return

        val db = AppDatabase.get(context)
        val repository = DatasetRepository(context, db)
        val manifest = repository.loadManifest()

        if (pending) {
            repository.installAll(manifest)
            markComplete(context, manifest.version)
            return
        }

        val isFresh = db.textDao().getAllOnce().isEmpty() &&
            db.vocabListDao().getAllOnce().isEmpty()
        if (!isFresh) {
            markComplete(context, manifest.version)
            return
        }

        check(prefs.edit().putBoolean(KEY_SEED_PENDING, true).commit()) {
            "Unable to persist the dataset seed pending state"
        }
        repository.installAll(manifest)
        markComplete(context, manifest.version)
    }

    private fun markComplete(context: Context, version: Int) {
        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_SEEDED_VERSION, version)
            .remove(KEY_SEED_PENDING)
            .commit()
        check(saved) { "Unable to persist the dataset seed completion state" }
    }
}
