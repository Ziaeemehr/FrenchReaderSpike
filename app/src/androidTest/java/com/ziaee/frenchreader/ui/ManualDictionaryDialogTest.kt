package com.ziaee.frenchreader.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ziaee.frenchreader.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManualDictionaryDialogTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun string(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test
    fun lookupTrimsWordBeforeOpeningDictionary() {
        var lookedUp: String? = null
        composeTestRule.setContent {
            ManualDictionaryDialog(onDismiss = {}, onLookup = { lookedUp = it })
        }

        composeTestRule.onNodeWithText(string(R.string.manual_dictionary_word_hint))
            .performTextInput("  bonjour  ")
        composeTestRule.onNodeWithText(string(R.string.manual_dictionary_lookup)).performClick()

        assertEquals("bonjour", lookedUp)
    }
}
