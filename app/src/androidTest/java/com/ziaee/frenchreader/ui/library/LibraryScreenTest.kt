package com.ziaee.frenchreader.ui.library

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.data.TextDocument
import com.ziaee.frenchreader.ui.theme.FrenchReaderTheme
import com.ziaee.frenchreader.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun string(id: Int, vararg args: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private fun doc(id: Long, title: String, completed: Boolean) = TextDocument(
        id = id,
        title = title,
        rawText = if (completed) "Un court paragraphe." else "Premier paragraphe.\n\nDeuxième paragraphe.",
        sourceName = "France Info",
        lastChunkIndex = 0
    )

    private fun setLibraryContent(
        state: LibraryUiState,
        onOpen: (TextDocument) -> Unit = {},
        onDelete: (TextDocument) -> Unit = {},
        onAddText: () -> Unit = {},
        onOpenHome: () -> Unit = {},
        onQueryChange: (String) -> Unit = {}
    ) {
        composeRule.setContent {
            FrenchReaderTheme(ThemeMode.LIGHT) {
                LibraryContent(
                    state = state,
                    onQueryChange = onQueryChange,
                    onSortSelect = {},
                    onOpen = onOpen,
                    onDelete = onDelete,
                    onAddText = onAddText,
                    onFilePickerClick = {},
                    onOpenHome = onOpenHome
                )
            }
        }
    }

    @Test
    fun populatedStateShowsSearchSortAndDocuments() {
        val document = doc(1L, "Mon article", completed = true)
        setLibraryContent(
            LibraryUiState(documents = listOf(document), completionByTextId = mapOf(document.id to true))
        )

        composeRule.onNodeWithText(string(R.string.library_search_hint)).assertExists()
        composeRule.onNodeWithText(document.title).assertExists()
        composeRule.onNodeWithText(string(R.string.library_status_read)).assertExists()
    }

    @Test
    fun emptyLibraryShowsEmptyStateNotNoResults() {
        setLibraryContent(LibraryUiState(query = "", documents = emptyList()))

        composeRule.onNodeWithText(string(R.string.library_empty_title)).assertExists()
        composeRule.onNodeWithText(string(R.string.library_empty_body)).assertExists()
        composeRule.onNodeWithText(string(R.string.library_no_results, ""), substring = true).assertDoesNotExist()
    }

    @Test
    fun noSearchResultsShowsNoResultsNotEmptyState() {
        setLibraryContent(LibraryUiState(query = "zzz", documents = emptyList()))

        composeRule.onNodeWithText(string(R.string.library_no_results, "zzz")).assertExists()
        composeRule.onNodeWithText(string(R.string.library_empty_title)).assertDoesNotExist()
    }

    @Test
    fun deletingARowRequiresConfirmation() {
        var deleteCount = 0
        val document = doc(1L, "Mon article", completed = true)
        setLibraryContent(
            state = LibraryUiState(documents = listOf(document)),
            onDelete = { deleteCount++ }
        )

        composeRule.onNode(hasContentDescription(string(R.string.library_row_more_actions, document.title))).performClick()
        composeRule.onNodeWithText(string(R.string.action_delete)).performClick()
        composeRule.onNodeWithText(string(R.string.library_delete_confirm_title)).assertExists()
        composeRule.onNodeWithText(string(R.string.action_cancel)).performClick()
        assertEquals(0, deleteCount)

        composeRule.onNode(hasContentDescription(string(R.string.library_row_more_actions, document.title))).performClick()
        composeRule.onNodeWithText(string(R.string.action_delete)).performClick()
        composeRule.onNodeWithText(string(R.string.action_delete)).performClick()
        assertEquals(1, deleteCount)
    }

    @Test
    fun libraryIsSelectedInBottomNavigationAndHomeRetainsCallback() {
        var homeClicks = 0
        setLibraryContent(state = LibraryUiState(), onOpenHome = { homeClicks++ })

        composeRule.onNode(hasText(string(R.string.nav_library)) and hasClickAction()).assertIsSelected()
        composeRule.onNodeWithText(string(R.string.nav_home)).performClick()
        assertEquals(1, homeClicks)
    }

    @Test
    fun selectionModeShowsContextActionsAndTogglesRow() {
        val first = doc(1L, "Premier", completed = false)
        val second = doc(2L, "Deuxième", completed = false)
        var toggledId: Long? = null
        composeRule.setContent {
            FrenchReaderTheme(ThemeMode.LIGHT) {
                LibraryContent(
                    state = LibraryUiState(documents = listOf(first, second), selectedIds = setOf(first.id)),
                    onQueryChange = {}, onSortSelect = {}, onOpen = {}, onDelete = {},
                    onAddText = {}, onFilePickerClick = {}, onOpenHome = {},
                    onToggleSelection = { toggledId = it }
                )
            }
        }

        composeRule.onNodeWithTag("selectionBar").assertExists()
        composeRule.onNodeWithTag("selectionCount").assertExists()
        composeRule.onNodeWithTag("selectAll").assertExists()
        composeRule.onNodeWithTag("bulkDelete").assertExists()
        composeRule.onNodeWithTag("bulkMove").assertExists()
        composeRule.onNodeWithTag("bulkTags").assertExists()
        composeRule.onNodeWithTag("libraryRow_2").performClick()
        assertEquals(second.id, toggledId)
    }
}
