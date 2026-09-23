package com.ziaee.frenchreader.shadowing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.vosk.Model
import java.io.File

class SharedModel<T : Any>(
    private val load: suspend () -> T,
    private val close: (T) -> Unit
) {
    private val mutex = Mutex()
    private var model: T? = null

    suspend fun <R> withModel(block: suspend (T) -> R): R = mutex.withLock {
        val current = model ?: load().also { model = it }
        block(current)
    }

    suspend fun invalidate() = mutex.withLock {
        val current = model
        model = null
        current?.let(close)
    }
}

object VoskModels {
    @Volatile
    private var shared: SharedModel<Model>? = null

    fun forDir(dir: File): SharedModel<Model> = shared ?: synchronized(this) {
        shared ?: SharedModel(
            load = { withContext(Dispatchers.Default) { Model(dir.absolutePath) } },
            close = Model::close
        ).also { shared = it }
    }
}
