package com.ziaee.frenchreader.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.util.ProcessTextApp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Drives [SelectionToolbarContent], [DefineToolbarContent] and [ProcessTextAppsPopupContent]
 * directly with fake data, same style as [com.ziaee.frenchreader.ui.home.HomeScreenTest] --
 * no real Activity/database needed since these are stateless composables. */
@RunWith(AndroidJUnit4::class)
class SelectionToolbarTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun string(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test
    fun selectionToolbarMoreButtonInvokesCallback() {
        var moreClicked = false
        composeTestRule.setContent {
            SelectionToolbarContent(onCopy = {}, onListen = {}, onMore = { moreClicked = true })
        }

        composeTestRule.onNodeWithContentDescription(string(R.string.selection_action_more)).performClick()

        assertEquals(true, moreClicked)
    }

    @Test
    fun defineToolbarMoreButtonInvokesCallback() {
        var moreClicked = false
        composeTestRule.setContent {
            DefineToolbarContent(onDefine = {}, onMore = { moreClicked = true })
        }

        composeTestRule.onNodeWithContentDescription(string(R.string.selection_action_more)).performClick()

        assertEquals(true, moreClicked)
    }

    @Test
    fun processTextAppsPopupShowsEmptyMessageWhenNoAppsInstalled() {
        composeTestRule.setContent {
            ProcessTextAppsPopupContent(apps = emptyList(), onAppSelected = {}, onBack = {})
        }

        composeTestRule.onNodeWithText(string(R.string.process_text_apps_empty)).assertExists()
    }

    @Test
    fun processTextAppsPopupLaunchesTappedApp() {
        var selected: ProcessTextApp? = null
        val app = ProcessTextApp(label = "Reverso Context", packageName = "com.reverso.context", activityName = "MainActivity", appLabel = "Reverso Context")
        composeTestRule.setContent {
            ProcessTextAppsPopupContent(apps = listOf(app), onAppSelected = { selected = it }, onBack = {})
        }

        composeTestRule.onNodeWithText("Reverso Context").performClick()

        assertEquals(app, selected)
    }

    @Test
    fun processTextAppsPopupBackButtonInvokesCallback() {
        var backClicked = false
        composeTestRule.setContent {
            ProcessTextAppsPopupContent(apps = emptyList(), onAppSelected = {}, onBack = { backClicked = true })
        }

        composeTestRule.onNodeWithContentDescription(string(R.string.accessibility_back)).performClick()

        assertEquals(true, backClicked)
    }
}
