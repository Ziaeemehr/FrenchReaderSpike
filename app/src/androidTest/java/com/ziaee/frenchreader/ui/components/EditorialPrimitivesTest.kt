package com.ziaee.frenchreader.ui.components

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.ui.theme.FrenchReaderTheme
import com.ziaee.frenchreader.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorialPrimitivesTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun string(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test
    fun searchEntryIsOneClickableControl() {
        var clicks = 0
        composeRule.setContent {
            FrenchReaderTheme(ThemeMode.LIGHT) {
                EditorialSearchEntry(text = "Rechercher…", onClick = { clicks++ })
            }
        }
        composeRule.onNodeWithText("Rechercher…").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun bottomBarExposesSelectedDestinationAndAddAction() {
        composeRule.setContent {
            FrenchReaderTheme(ThemeMode.LIGHT) {
                EditorialBottomBar(
                    selectedDestination = EditorialDestination.HOME,
                    onHome = {}, onLibrary = {}, onAddText = {}
                )
            }
        }
        composeRule.onNodeWithText(string(R.string.nav_home)).assertIsSelected()
        composeRule.onNodeWithText(string(R.string.action_add_text)).assertHasClickAction()
    }
}
