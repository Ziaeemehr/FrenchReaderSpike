package com.ziaee.frenchreader

import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ziaee.frenchreader.util.SharedTextHolder
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [MainActivity.handleIncomingIntent] posts an ACTION_PROCESS_TEXT payload to
 * [SharedTextHolder], but Home's [com.ziaee.frenchreader.ui.shared.AddTextHost]
 * consumes it (opening the prefilled Add Text dialog) during the very first
 * composition -- by the time [ActivityScenario.launch] returns (it waits for
 * RESUMED, which happens after that first composition), `pending` is already
 * back to null. So this asserts the real, user-visible outcome (the dialog
 * showing the shared text) instead of the transient signal.
 */
@RunWith(AndroidJUnit4::class)
class ProcessTextIntentTest {
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @Before
    fun setUp() {
        SharedTextHolder.consume()
    }

    @After
    fun tearDown() {
        SharedTextHolder.consume()
    }

    @Test
    fun processTextIntentOpensAddTextDialogPrefilledWithSelectedText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_PROCESS_TEXT)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_PROCESS_TEXT, "some French text")

        ActivityScenario.launch<MainActivity>(intent).use {
            composeTestRule.onNodeWithText("some French text").assertExists()
        }
    }
}
