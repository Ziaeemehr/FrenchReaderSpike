package com.ziaee.frenchreader.util

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Simple in-memory bridge from MainActivity's onCreate/onNewIntent (where
 * ACTION_SEND / ACTION_VIEW intents actually arrive) to wherever the
 * "add text" dialog is currently hosted (Home, Library -- see
 * [com.ziaee.frenchreader.ui.shared.AddTextHost]). Not persisted to disk --
 * if the process is killed before a screen consumes it, the share is lost,
 * which matches how most Android share targets behave anyway.
 */
data class IncomingShare(
    val suggestedTitle: String,
    val body: String,
    val epubUri: Uri? = null,
    val pdfUri: Uri? = null,
    val imageUris: List<Uri> = emptyList()
)

object SharedTextHolder {
    val pending = MutableStateFlow<IncomingShare?>(null)

    fun post(share: IncomingShare) {
        pending.value = share
    }

    fun consume() {
        pending.value = null
    }
}
