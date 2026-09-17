package com.ziaee.frenchreader.llm

import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class LlamaCppEngine(
    private val modelPathProvider: () -> String,
    private val isSupportedAbi: () -> Boolean = { Build.SUPPORTED_ABIS.contains("arm64-v8a") },
    private val nCtx: Int = 2048,
    private val nThreads: Int = 4,
    private val idleUnloadDelayMs: Long = 120_000L,
) : LocalLlmEngine {

    private val mutex = Mutex()
    private var handle: Long = 0L

    // One background scope per engine instance, cancelled in unload() so the idle timer
    // never outlives the handle it's meant to unload. Spike requirement: unload after ~2min
    // idle, and immediately on onTrimMemory (callers invoke unload() directly for that path).
    private val idleScope = CoroutineScope(SupervisorJob())
    private var idleUnloadJob: Job? = null

    private fun resetIdleTimer() {
        idleUnloadJob?.cancel()
        idleUnloadJob = idleScope.launch {
            delay(idleUnloadDelayMs)
            unload()
        }
    }

    override suspend fun ensureLoaded(): Boolean = mutex.withLock {
        if (handle != 0L) {
            resetIdleTimer()
            return@withLock true
        }
        if (!isSupportedAbi()) return@withLock false

        val modelFile = File(modelPathProvider())
        if (!modelFile.exists() || !modelFile.canRead()) return@withLock false

        val loaded = LlmNative.nativeLoadModel(modelFile.absolutePath, nCtx, nThreads)
        if (loaded == 0L) return@withLock false
        handle = loaded
        resetIdleTimer()
        true
    }

    override suspend fun generate(
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int,
        grammar: String?,
    ): LlmResult = mutex.withLock {
        if (handle == 0L) {
            val modelFile = File(modelPathProvider())
            if (!isSupportedAbi() || !modelFile.exists() || !modelFile.canRead()) {
                return@withLock LlmResult.Failure(LlmResult.FailureReason.NOT_DOWNLOADED)
            }
            val loaded = LlmNative.nativeLoadModel(modelFile.absolutePath, nCtx, nThreads)
            if (loaded == 0L) {
                return@withLock LlmResult.Failure(LlmResult.FailureReason.LOAD_FAILED)
            }
            handle = loaded
        }
        resetIdleTimer()

        val text = try {
            LlmNative.nativeGenerate(handle, systemPrompt, userPrompt, maxTokens, grammar)
        } catch (e: OutOfMemoryError) {
            return@withLock LlmResult.Failure(LlmResult.FailureReason.OUT_OF_MEMORY)
        }

        if (text.isBlank()) {
            LlmResult.Failure(LlmResult.FailureReason.GENERATION_FAILED)
        } else {
            LlmResult.Success(text)
        }
    }

    // Safe to call from anywhere, any number of times: onTrimMemory, Activity.onStop, the
    // idle timer above, or AssistantViewModel.onCleared(). A no-op if already unloaded.
    //
    // NOTE: this deliberately does NOT free the native handle synchronously on the calling
    // thread. generate()/ensureLoaded() hold `mutex` for the full duration of a native call
    // that can take several seconds; freeing the handle outside that mutex would let unload()
    // race a same-handle nativeGenerate() in flight (use-after-free) if a caller (e.g.
    // AssistantViewModel.onCleared()) invokes unload() while generation is still running.
    // Instead, enqueue the free onto idleScope so it runs only after acquiring the same
    // mutex generate()/ensureLoaded() use -- this makes unload() non-blocking for the caller
    // but race-free and non-double-freeing (the handle != 0L check happens inside the lock).
    override fun unload() {
        idleUnloadJob?.cancel()
        idleScope.launch {
            mutex.withLock {
                if (handle != 0L) {
                    LlmNative.nativeUnload(handle)
                    handle = 0L
                }
            }
        }
    }
}
