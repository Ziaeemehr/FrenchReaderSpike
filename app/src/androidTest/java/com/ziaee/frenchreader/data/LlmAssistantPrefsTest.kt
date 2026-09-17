package com.ziaee.frenchreader.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LlmAssistantPrefsTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun defaultsToNotDownloaded() {
        assertEquals(false, LlmAssistantPrefs.isModelDownloaded(context))
    }

    @Test
    fun setModelDownloadedPersists() {
        LlmAssistantPrefs.setModelDownloaded(context, true)
        assertEquals(true, LlmAssistantPrefs.isModelDownloaded(context))
        LlmAssistantPrefs.setModelDownloaded(context, false)
        assertEquals(false, LlmAssistantPrefs.isModelDownloaded(context))
    }
}
