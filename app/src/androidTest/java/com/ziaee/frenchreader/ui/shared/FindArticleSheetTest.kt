package com.ziaee.frenchreader.ui.shared

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.ui.theme.FrenchReaderTheme
import com.ziaee.frenchreader.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the "one-tap search" fix from ROADMAP.md section 6: tapping the
 * Home search box opens this sheet, and the real query field must grab
 * focus/keyboard immediately (no second tap), submit only a non-empty
 * query, treat the IME search action exactly like the trailing icon, and
 * ignore a resubmit while a search is already in flight.
 */
@RunWith(AndroidJUnit4::class)
class FindArticleSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun string(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun setSheet(
        searchState: ContentSearchUiState = ContentSearchUiState.Idle,
        onSearch: (String) -> Unit = {}
    ) {
        composeRule.setContent {
            FrenchReaderTheme(ThemeMode.LIGHT) {
                FindArticleSheet(
                    searchState = searchState,
                    importingRef = null,
                    onSearch = onSearch,
                    onSelect = {},
                    onDismiss = {}
                )
            }
        }
    }

    @Test
    fun queryFieldAutoFocusesWhenSheetOpens() {
        setSheet()
        composeRule.onNodeWithTag(FIND_ARTICLE_QUERY_FIELD_TEST_TAG).assertIsFocused()
    }

    @Test
    fun imeSearchActionSubmitsNonEmptyQueryLikeTheIcon() {
        var submitted: String? = null
        setSheet(onSearch = { submitted = it })

        composeRule.onNodeWithTag(FIND_ARTICLE_QUERY_FIELD_TEST_TAG).performTextInput("Maupassant")
        composeRule.onNodeWithTag(FIND_ARTICLE_QUERY_FIELD_TEST_TAG).performImeAction()

        assertEquals("Maupassant", submitted)
    }

    @Test
    fun searchIconSubmitsNonEmptyQuery() {
        var submitted: String? = null
        setSheet(onSearch = { submitted = it })

        composeRule.onNodeWithTag(FIND_ARTICLE_QUERY_FIELD_TEST_TAG).performTextInput("Maupassant")
        composeRule.onNodeWithContentDescription(string(R.string.accessibility_search)).performClick()

        assertEquals("Maupassant", submitted)
    }

    @Test
    fun emptyQueryDoesNotSubmit() {
        var callCount = 0
        setSheet(onSearch = { callCount++ })

        composeRule.onNodeWithContentDescription(string(R.string.accessibility_search)).performClick()
        composeRule.onNodeWithTag(FIND_ARTICLE_QUERY_FIELD_TEST_TAG).performImeAction()

        assertEquals(0, callCount)
    }

    @Test
    fun resubmitIsIgnoredWhileASearchIsAlreadyInFlight() {
        var callCount = 0
        setSheet(searchState = ContentSearchUiState.Searching, onSearch = { callCount++ })

        composeRule.onNodeWithTag(FIND_ARTICLE_QUERY_FIELD_TEST_TAG).performTextInput("Maupassant")
        composeRule.onNodeWithContentDescription(string(R.string.accessibility_search)).performClick()

        assertTrue(callCount == 0)
    }
}
