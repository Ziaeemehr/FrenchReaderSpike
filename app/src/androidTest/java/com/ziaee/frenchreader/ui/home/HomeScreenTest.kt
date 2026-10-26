package com.ziaee.frenchreader.ui.home

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.data.HeadlineEntity
import com.ziaee.frenchreader.data.TextDocument
import com.ziaee.frenchreader.ui.theme.FrenchReaderTheme
import com.ziaee.frenchreader.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the stateless [HomeContent] with a static [HomeUiState] instead of
 * a real database-backed [HomeViewModel] -- see the implementation plan's
 * Task 5, Step 1.
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun string(id: Int, vararg args: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private fun headline(externalId: String = "guid-1") = HeadlineEntity(
        sourceId = "rfi_facile",
        sourceLabel = "RFI",
        externalId = externalId,
        title = "Titre de l’article",
        snippet = "Résumé de l’article",
        articleUrl = "https://example.com/$externalId",
        imageUrl = null,
        publishedAtMs = 100L,
        cachedAtMs = 200L
    )

    private fun setHomeContent(
        state: HomeUiState,
        selectedHeadline: HeadlineEntity? = null,
        onOpenVocab: () -> Unit = {},
        onOpenProgress: () -> Unit = {},
        onOpenNews: () -> Unit = {},
        onAddStudyLog: () -> Unit = {},
        onOpenLibrary: () -> Unit = {},
        onAddTextClick: () -> Unit = {},
        onOpenGradedReaders: () -> Unit = {},
        onSearchClick: () -> Unit = {},
        onOpenDictionary: () -> Unit = {},
        onStartReview: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            FrenchReaderTheme(ThemeMode.LIGHT) {
                HomeContent(
                    state = state,
                    selectedHeadline = selectedHeadline,
                    isPreviewImporting = false,
                    previewHasError = false,
                    isPreviewAlreadyDownloaded = false,
                    snackbarHostState = remember { SnackbarHostState() },
                    onOpenDictionary = onOpenDictionary,
                    onSearchClick = onSearchClick,
                    onOpenVocab = onOpenVocab,
                    onOpenProgress = onOpenProgress,
                    onOpenNews = onOpenNews,
                    onAddStudyLog = onAddStudyLog,
                    onOpenSettings = {},
                    onOpenLibrary = onOpenLibrary,
                    onAddTextClick = onAddTextClick,
                    onOpenGradedReaders = onOpenGradedReaders,
                    onPullRefresh = {},
                    onDownloadOrOpen = {},
                    onDismissPreview = {},
                    onStartReview = onStartReview
                )
            }
        }
    }

    @Test
    fun homeShowsApprovedSections() {
        val sampleHeadline = headline()
        val state = HomeUiState(
            headlines = listOf(sampleHeadline),
            recentTexts = listOf(TextDocument(id = 1L, title = "Mon texte", rawText = "Texte")),
            continueReading = TextDocument(id = 2L, title = "En cours de lecture", rawText = "Texte")
        )

        setHomeContent(state)

        composeTestRule.onNodeWithTag(HOME_LAZY_COLUMN_TEST_TAG)
            .performScrollToNode(hasText(string(R.string.home_section_today_news)))
        composeTestRule.onNodeWithText(string(R.string.home_section_today_news)).assertExists()
        composeTestRule.onNodeWithText(sampleHeadline.title, substring = true, useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("En cours de lecture").assertDoesNotExist()
        composeTestRule.onNodeWithText("Mon texte").assertDoesNotExist()

    }

    @Test
    fun selectedHeadlinePreviewStillShowsTheDownloadAction() {
        val sampleHeadline = headline()
        setHomeContent(HomeUiState(headlines = listOf(sampleHeadline)), selectedHeadline = sampleHeadline)
        composeTestRule.onNodeWithText(string(R.string.preview_action_download_read)).assertExists()
    }

    @Test
    fun progressActionsHaveIndependentCallbacks() {
        var progressClicks = 0
        var addClicks = 0
        setHomeContent(HomeUiState(todayActivityMinutes = 5.0),
            onOpenProgress = { progressClicks++ }, onAddStudyLog = { addClicks++ })

        composeTestRule.onNodeWithText(string(R.string.activity_goal_done)).assertExists()
        composeTestRule.onNodeWithContentDescription(string(R.string.study_log_add_time)).performClick()
        assertEquals(1, addClicks)
        assertEquals(0, progressClicks)
        composeTestRule.onNodeWithText(string(R.string.progress_title)).performClick()
        assertEquals(1, progressClicks)
    }

    @Test
    fun newsEntryOpensNewsWithoutShowingHeadlines() {
        val headlines = (1..4).map { index ->
            headline("guid-$index").copy(title = "Headline $index", publishedAtMs = index.toLong())
        }
        var clicks = 0
        setHomeContent(HomeUiState(headlines = headlines), onOpenNews = { clicks++ })
        composeTestRule.onNodeWithTag(HOME_LAZY_COLUMN_TEST_TAG)
            .performScrollToNode(hasText(string(R.string.home_section_today_news)))
        headlines.forEach { composeTestRule.onNodeWithText(it.title).assertDoesNotExist() }
        composeTestRule.onNodeWithText(string(R.string.home_section_today_news)).performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun dictionaryIsADirectHeaderAction() {
        var dictionaryClicks = 0
        setHomeContent(HomeUiState(), onOpenDictionary = { dictionaryClicks++ })

        composeTestRule
            .onNodeWithContentDescription(string(R.string.manual_dictionary_action))
            .performClick()

        assertEquals(1, dictionaryClicks)
    }

    @Test
    fun vocabularyIsNotInMoreMenu() {
        setHomeContent(HomeUiState())

        composeTestRule.onNodeWithText(string(R.string.home_action_import_file)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.home_action_vocabulary)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.home_action_graded_readers)).assertDoesNotExist()

        composeTestRule.onNode(
            androidx.compose.ui.test.hasContentDescription(string(R.string.home_more_actions))
        ).performClick()

        composeTestRule.onNodeWithText(string(R.string.home_action_import_file)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.home_action_vocabulary)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.home_action_graded_readers)).assertExists()
    }

    @Test
    fun savedWordsSummaryInvokesVocabularyCallback() {
        var clicks = 0
        setHomeContent(
            state = HomeUiState(savedWordCount = 7),
            onOpenVocab = { clicks++ }
        )

        composeTestRule
            .onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.resources.getQuantityString(R.plurals.home_saved_words, 7))
            .assertHasClickAction()
            .performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun gradedReadersMenuItemInvokesCallback() {
        var clicks = 0
        setHomeContent(HomeUiState(), onOpenGradedReaders = { clicks++ })

        composeTestRule.onNode(
            androidx.compose.ui.test.hasContentDescription(string(R.string.home_more_actions))
        ).performClick()
        composeTestRule.onNodeWithText(string(R.string.home_action_graded_readers)).performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun dueReviewShowsCountAndStartInvokesCallbackOnce() {
        var startCount = 0
        setHomeContent(
            state = HomeUiState(dueReviewCount = 3),
            onStartReview = { startCount++ }
        )

        composeTestRule.onNodeWithText(string(R.string.home_review_due, 3)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.home_review_start)).performClick()
        assertEquals(1, startCount)
    }

    @Test
    fun zeroDueReviewShowsCalmCompletedState() {
        setHomeContent(state = HomeUiState(dueReviewCount = 0))

        composeTestRule.onNodeWithText(string(R.string.home_review_complete)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.home_review_start)).assertDoesNotExist()
    }

    @Test
    fun homeIsSelectedInBottomNavigationAndLibraryAddRetainCallbacks() {
        var libraryClicks = 0
        var addTextClicks = 0
        setHomeContent(
            state = HomeUiState(recentTexts = listOf(TextDocument(id = 1L, title = "Mon texte", rawText = "Texte"))),
            onOpenLibrary = { libraryClicks++ },
            onAddTextClick = { addTextClicks++ }
        )

        composeTestRule.onNodeWithText(string(R.string.nav_home)).assertIsSelected()
        composeTestRule.onNodeWithText(string(R.string.nav_library)).performClick()
        composeTestRule.onNodeWithText(string(R.string.action_add_text)).performClick()
        assertEquals(1, libraryClicks)
        assertEquals(1, addTextClicks)
    }
}
