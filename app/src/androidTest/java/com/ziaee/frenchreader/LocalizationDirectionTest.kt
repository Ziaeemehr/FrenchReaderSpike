package com.ziaee.frenchreader

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ziaee.frenchreader.data.HeadlineEntity
import com.ziaee.frenchreader.ui.home.HomeContent
import com.ziaee.frenchreader.ui.home.HomeUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Forces Persian (RTL) and English (LTR) ambient layout direction around
 * Home's root and asserts the root chrome follows it while a French
 * headline stays LTR either way -- see the implementation plan's Task 8,
 * Step 6 and the design doc's "French content remains LTR" requirement.
 *
 * Home's own composables never hardcode a direction for their chrome (it's
 * inherited from the ambient [LocalLayoutDirection], which the real app
 * sets from the AppCompat per-app locale via [MainActivity]'s configuration
 * -- exercising that full plumbing needs a real Activity + locale-restart,
 * which is what the manual per-language check in Task 9 covers). Providing
 * the ambient value directly here isolates and deterministically tests the
 * one thing this app controls: that French headline content is explicitly
 * pinned LTR regardless of what direction surrounds it.
 */
@RunWith(AndroidJUnit4::class)
class LocalizationDirectionTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun string(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private val frenchHeadline = HeadlineEntity(
        sourceId = "rfi_facile",
        sourceLabel = "RFI",
        externalId = "guid-1",
        title = "Titre français en vedette",
        snippet = "Résumé français",
        articleUrl = "https://example.com/guid-1",
        imageUrl = null,
        publishedAtMs = 100L,
        cachedAtMs = 200L
    )

    @Test
    fun rtlAmbientDirectionAppliesToRootWhileFrenchHeadlineStaysLtr() {
        val (rootNode, headlineNode) = renderHome(LayoutDirection.Rtl)
        assertEquals(LayoutDirection.Rtl, rootNode.fetchSemanticsNode().layoutInfo.layoutDirection)
        assertEquals(LayoutDirection.Ltr, headlineNode.fetchSemanticsNode().layoutInfo.layoutDirection)
    }

    @Test
    fun ltrAmbientDirectionAppliesToRootWhileFrenchHeadlineStaysLtr() {
        val (rootNode, headlineNode) = renderHome(LayoutDirection.Ltr)
        assertEquals(LayoutDirection.Ltr, rootNode.fetchSemanticsNode().layoutInfo.layoutDirection)
        assertEquals(LayoutDirection.Ltr, headlineNode.fetchSemanticsNode().layoutInfo.layoutDirection)
    }

    private fun renderHome(ambientDirection: LayoutDirection): Pair<SemanticsNodeInteraction, SemanticsNodeInteraction> {
        val state = HomeUiState(headlines = listOf(frenchHeadline))

        composeTestRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides ambientDirection) {
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
        }

        val rootNode = composeTestRule.onNodeWithText(string(R.string.home_section_today_news))
        // The headline title lives inside a clickable NewsCard, whose
        // semantics merge its descendants into one node -- the merged
        // node reports the Card's own (Rtl) direction, not the Text's own
        // (Ltr) one, so the unmerged tree is needed to see the real value.
        val headlineNode = composeTestRule.onNodeWithText(frenchHeadline.title, useUnmergedTree = true)
        return rootNode to headlineNode
    }
}
