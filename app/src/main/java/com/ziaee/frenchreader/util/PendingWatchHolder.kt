package com.ziaee.frenchreader.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Bridges both cold-start and onNewIntent video shares to navigation. */
object PendingWatchHolder {
    private val mutablePending = MutableStateFlow<String?>(null)
    val pending = mutablePending.asStateFlow()

    fun post(videoId: String) { mutablePending.value = videoId }
    fun consume() { mutablePending.value = null }
}
