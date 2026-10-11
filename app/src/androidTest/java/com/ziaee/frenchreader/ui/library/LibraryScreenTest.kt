package com.ziaee.frenchreader.ui.library

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.data.LibraryFolder
import com.ziaee.frenchreader.data.TextDocument
import com.ziaee.frenchreader.data.READ_MARK_FINISHED
import com.ziaee.frenchreader.data.READ_MARK_NOT_FINISHED
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
        lastChunkIndex = 0,
        lastAccessedAtMs = 1L
    )

    private fun setLibraryContent(
        state: LibraryUiState,
        onOpen: (TextDocument) -> Unit = {},
        onDelete: (TextDocument) -> Unit = {},
        onAddText: () -> Unit = {},
        onOpenHome: () -> Unit = {},
        onQueryChange: (String) -> Unit = {},
        onShelfSelect: (LibraryShelf) -> Unit = {},
        onSetLevel: (List<Long>, String?) -> Unit = { _, _ -> },
        onLevelFilterChange: (LevelFilter) -> Unit = {},
        onReadMark: (List<Long>, Int) -> Unit = { _, _ -> }
    ) {
        composeRule.setContent {
            FrenchReaderTheme(ThemeMode.LIGHT) {
                LibraryContent(
                    state = state,
                    onQueryChange = onQueryChange,
                    onSortSelect = {},
                    onShelfSelect = onShelfSelect,
                    onReadMark = onReadMark,
                    onSetLevel = onSetLevel,
                    onLevelFilterChange = onLevelFilterChange,
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
    fun rowLevelPickerSelectsCurrentLevelAndAppliesImmediately() {
        val document = TextDocument(id = 1, title = "Story", rawText = "Text", level = "A2")
        var chosen: Pair<List<Long>, String?>? = null
        setLibraryContent(LibraryUiState(documents = listOf(document)), onSetLevel = { ids, level -> chosen = ids to level })
        composeRule.onNode(hasContentDescription(string(R.string.library_row_more_actions, document.title))).performClick()
        composeRule.onNodeWithTag("setLevel_1").performClick()
        // The row behind the dialog also shows "A2"; only the chip inside the dialog is meant.
        composeRule.onNode(hasText("A2") and hasAnyAncestor(isDialog())).assertIsSelected()
        composeRule.onNodeWithText("B2").performClick()
        assertEquals(listOf(1L) to "B2", chosen)
        composeRule.onNodeWithText(string(R.string.text_level_set)).assertDoesNotExist()
    }

    @Test
    fun bulkLevelPickerCanClearLevels() {
        var chosen: Pair<List<Long>, String?>? = null
        val document = TextDocument(id = 1, title = "Story", rawText = "Text", level = "A2")
        setLibraryContent(
            LibraryUiState(documents = listOf(document), selectedIds = setOf(1L)),
            onSetLevel = { ids, level -> chosen = ids to level }
        )
        composeRule.onNodeWithTag("bulkReadMark").performClick()
        composeRule.onNodeWithText(string(R.string.text_level_set)).performClick()
        composeRule.onNodeWithText(string(R.string.text_level_unspecified)).performClick()
        assertEquals(listOf(1L) to null, chosen)
    }

    @Test
    fun levelFilterOffersExistingLevelsAndCurrentSelection() {
        var chosen: LevelFilter? = null
        setLibraryContent(
            LibraryUiState(documents = listOf(TextDocument(title = "Story", rawText = "Text", level = "A2")), levelFilter = LevelFilter.Level("C1")),
            onLevelFilterChange = { chosen = it }
        )
        composeRule.onNodeWithTag("levelFilters").performClick()
        composeRule.onNodeWithText("B2").assertDoesNotExist()
        composeRule.onNode(hasText("A2") and hasAnyAncestor(isPopup())).performClick()
        assertEquals(LevelFilter.Level("A2"), chosen)
    }

    @Test
    fun shelfChipsShowCountsAndSelectFinished() {
        var shelf: LibraryShelf? = null
        setLibraryContent(
            LibraryUiState(shelfCounts = LibraryShelfCounts(1, 2)),
            onShelfSelect = { shelf = it }
        )
        composeRule.onNodeWithTag("shelfReading").assertIsSelected()
        composeRule.onNodeWithText(string(R.string.library_shelf_finished, 2)).assertExists()
        composeRule.onNodeWithText(string(R.string.library_shelf_all, 3)).assertExists()
        composeRule.onNodeWithTag("shelfFinished").performClick()
        assertEquals(LibraryShelf.FINISHED, shelf)
    }

    @Test
    fun rowMenuMarksUntouchedTextFinished() {
        val document = TextDocument(id = 1, title = "New", rawText = "Text")
        var mark: Pair<List<Long>, Int>? = null
        setLibraryContent(LibraryUiState(documents = listOf(document)), onReadMark = { ids, value -> mark = ids to value })
        composeRule.onNode(hasContentDescription(string(R.string.library_row_more_actions, document.title))).performClick()
        composeRule.onNodeWithText(string(R.string.library_mark_finished)).performClick()
        assertEquals(listOf(document.id) to READ_MARK_FINISHED, mark)
    }

    @Test
    fun finishedRowMenuMovesTextBackToReading() {
        val document = TextDocument(id = 1, title = "Done", rawText = "Text", readMark = READ_MARK_FINISHED)
        var mark: Pair<List<Long>, Int>? = null
        setLibraryContent(
            LibraryUiState(shelf = LibraryShelf.FINISHED, documents = listOf(document)),
            onReadMark = { ids, value -> mark = ids to value }
        )
        composeRule.onNodeWithText(string(R.string.library_status_read)).assertExists()
        composeRule.onNode(hasContentDescription(string(R.string.library_row_more_actions, document.title))).performClick()
        composeRule.onNodeWithText(string(R.string.library_move_back_reading)).performClick()
        assertEquals(listOf(document.id) to READ_MARK_NOT_FINISHED, mark)
    }

    @Test
    fun selectionMenuOffersBothMarksForAllSelectedTexts() {
        val documents = listOf(doc(1, "First", false), doc(2, "Second", false))
        var mark: Pair<List<Long>, Int>? = null
        setLibraryContent(
            LibraryUiState(documents = documents, selectedIds = setOf(1L, 2L)),
            onReadMark = { ids, value -> mark = ids to value }
        )
        composeRule.onNodeWithTag("bulkReadMark").performClick()
        composeRule.onNodeWithText(string(R.string.library_mark_finished)).performClick()
        assertEquals(listOf(1L, 2L) to READ_MARK_FINISHED, mark)
        composeRule.onNodeWithTag("bulkReadMark").performClick()
        composeRule.onNodeWithText(string(R.string.library_move_back_reading)).performClick()
        assertEquals(listOf(1L, 2L) to READ_MARK_NOT_FINISHED, mark)
    }

    @Test
    fun finishedShelfEmptyShowsExplanation() {
        setLibraryContent(LibraryUiState(shelf = LibraryShelf.FINISHED, totalDocumentCount = 1, shelfCounts = LibraryShelfCounts(1, 0)))
        composeRule.onNodeWithText(string(R.string.library_finished_empty)).assertExists()
    }

    @Test
    fun readingShelfEmptyPointsToFinished() {
        setLibraryContent(LibraryUiState(totalDocumentCount = 2, shelfCounts = LibraryShelfCounts(0, 2)))
        composeRule.onNodeWithText(string(R.string.library_reading_empty)).assertExists()
    }

    @Test
    fun populatedStateShowsSearchSortAndDocuments() {
        val document = doc(1L, "Mon article", completed = true)
        setLibraryContent(
            LibraryUiState(shelf = LibraryShelf.FINISHED, documents = listOf(document), completionByTextId = mapOf(document.id to true))
        )

        composeRule.onNodeWithText(string(R.string.library_search_hint)).assertExists()
        composeRule.onNodeWithText(document.title).assertExists()
        composeRule.onNodeWithText(string(R.string.library_status_read)).assertExists()
    }

    @Test
    fun untouchedDocumentShowsNewStatus() {
        val document = TextDocument(id = 7L, title = "Nouveau texte", rawText = "Texte")
        setLibraryContent(LibraryUiState(documents = listOf(document)))

        composeRule.onNodeWithText(string(R.string.library_status_new)).assertExists()
        composeRule.onNodeWithText(string(R.string.library_status_in_progress)).assertDoesNotExist()
    }

    @Test
    fun folderDropdownCollapsesSubfoldersUntilExpandedAndSelectsFolder() {
        val parent = LibraryFolder(id = 10L, name = "Histoires")
        val child = LibraryFolder(id = 11L, name = "A2", parentId = parent.id)
        var selected: FolderFilter? = null
        composeRule.setContent {
            FrenchReaderTheme(ThemeMode.LIGHT) {
                LibraryContent(
                    state = LibraryUiState(folders = listOf(parent, child)),
                    onQueryChange = {}, onSortSelect = {}, onOpen = {}, onDelete = {},
                    onAddText = {}, onFilePickerClick = {}, onOpenHome = {},
                    onFolderFilterChange = { selected = it }
                )
            }
        }

        composeRule.onNodeWithTag("folderFilters").performClick()
        composeRule.onNodeWithTag("folderFilterAll").assertExists()
        composeRule.onNodeWithTag("folderFilterUnfiled").assertExists()
        composeRule.onNodeWithTag("folderFilter_${child.id}").assertDoesNotExist()
        composeRule.onNodeWithTag("folderToggle_${parent.id}").performClick()
        composeRule.onNodeWithTag("folderFilter_${child.id}").performClick()
        assertEquals(FolderFilter.Folder(child.id), selected)
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
            state = LibraryUiState(shelf = LibraryShelf.FINISHED, documents = listOf(document), completionByTextId = mapOf(document.id to true)),
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
