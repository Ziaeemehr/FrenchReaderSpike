package com.ziaee.frenchreader.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HighlightPrefsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    @After
    fun clearPrefs() {
        context.getSharedPreferences("highlight_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun lastSelectedManualHighlightColorPersists() {
        assertEquals("yellow", HighlightPrefs.getLastColorKey(context))

        HighlightPrefs.setLastColorKey(context, "pink")

        assertEquals("pink", HighlightPrefs.getLastColorKey(context))
    }

    @Test
    fun unsupportedColorCannotReplaceLastSelection() {
        HighlightPrefs.setLastColorKey(context, "blue")
        HighlightPrefs.setLastColorKey(context, "purple")

        assertEquals("blue", HighlightPrefs.getLastColorKey(context))
    }
}
