package com.ziaee.frenchreader.ui.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.data.HeadlineEntity
import com.ziaee.frenchreader.data.TextDocument
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the stateless [HomeContent] with a static [HomeUiState] instead of
 * a real database-backed [HomeViewModel] -- see Task 6, Step 8 of the
 * implementation plan.
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun string(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

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

    @Test
    fun homeShowsApprovedSections() {
        val sampleHeadline = headline()
        val state = HomeUiState(
            headlines = listOf(sampleHeadline),
            recentTexts = listOf(TextDocument(id = 1L, title = "Mon texte", rawText = "Texte")),
            continueReading = TextDocument(id = 2L, title = "En cours de lecture", rawText = "Texte")
        )

        composeTestRule.setContent {
            MaterialTheme {
                HomeContent(
                    state = state,
                    selectedHeadline = null,
                    isPreviewImporting = false,
                    previewHasError = false,
                    isPreviewAlreadyDownloaded = false,
                    snackbarHostState = remember { SnackbarHostState() },
                    onSearchClick = {},
                    onFilePickerClick = {},
                    onOpenVocab = {},
                    onOpenStatistics = {},
                    onOpenSettings = {},
                    onOpenLibrary = {},
                    onAddTextClick = {},
                    onSelectHeadline = {},
                    onRetryNews = {},
                    onPullRefresh = {},
                    onOpenText = {},
                    onDownloadOrOpen = {},
                    onDismissPreview = {}
                )
            }
        }

        composeTestRule.onNodeWithText(string(R.string.home_section_today_news)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.home_section_continue_reading)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.home_section_my_texts)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.home_action_see_all)).assertExists()
        composeTestRule.onNodeWithText(sampleHeadline.title).assertExists()
    }

    @Test
    fun selectingAHeadlineShowsTheDownloadAction() {
        val sampleHeadline = headline()
        val state = HomeUiState(headlines = listOf(sampleHeadline))

        composeTestRule.setContent {
            var selected by remember { mutableStateOf<HeadlineEntity?>(null) }
            MaterialTheme {
                HomeContent(
                    state = state,
                    selectedHeadline = selected,
                    isPreviewImporting = false,
                    previewHasError = false,
                    isPreviewAlreadyDownloaded = false,
                    snackbarHostState = remember { SnackbarHostState() },
                    onSearchClick = {},
                    onFilePickerClick = {},
                    onOpenVocab = {},
                    onOpenStatistics = {},
                    onOpenSettings = {},
                    onOpenLibrary = {},
                    onAddTextClick = {},
                    onSelectHeadline = { selected = it },
                    onRetryNews = {},
                    onPullRefresh = {},
                    onOpenText = {},
                    onDownloadOrOpen = {},
                    onDismissPreview = {}
                )
            }
        }

        composeTestRule.onNodeWithText(sampleHeadline.title).performClick()
        composeTestRule.onNodeWithText(string(R.string.preview_action_download_read)).assertExists()
    }
}
