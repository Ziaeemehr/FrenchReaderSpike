package com.ziaee.frenchreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.ui.theme.PageNavigation
import kotlin.math.abs
import java.text.NumberFormat
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PagedReadingContent(
    pages: List<ReadingPage>,
    navigation: PageNavigation,
    bookDirection: LayoutDirection,
    background: Color,
    inkFaded: Color,
    requestedAnchor: ReadingAnchor?,
    userScrollEnabled: Boolean,
    onAnchorConsumed: () -> Unit,
    onVisiblePage: (ReadingPage) -> Unit,
    modifier: Modifier = Modifier,
    pageContent: @Composable (ReadingPage) -> Unit
) {
    if (pages.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val animationScope = rememberCoroutineScope()
    var stableAnchor by remember { mutableStateOf(pages.first().firstAnchor()) }

    LaunchedEffect(requestedAnchor, pages) {
        val anchor = requestedAnchor ?: return@LaunchedEffect
        if (pages.none { page -> page.fragments.any { it.chunkIndex == anchor.chunkIndex } }) {
            return@LaunchedEffect
        }
        pagerState.animateScrollToPage(pages.pageIndexFor(anchor).coerceIn(0, pages.lastIndex))
        stableAnchor = anchor
        onAnchorConsumed()
    }
    LaunchedEffect(pages) {
        val target = pages.pageIndexFor(stableAnchor).coerceIn(0, pages.lastIndex)
        if (target != pagerState.currentPage) pagerState.scrollToPage(target)
    }
    LaunchedEffect(pagerState.settledPage, pages) {
        pages.getOrNull(pagerState.settledPage)?.let {
            stableAnchor = it.firstAnchor()
            onVisiblePage(it)
        }
    }

    val reverse = bookDirection == LayoutDirection.Rtl
    Box(
        modifier
            .fillMaxSize()
            .pointerInput(navigation, pages.size, reverse, userScrollEnabled) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val start = down.position
                    var up = down
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        up = event.changes.firstOrNull { it.id == down.id } ?: break
                    } while (!up.changedToUpIgnoreConsumed())
                    val isTap = (up.position - start).getDistance() <= viewConfiguration.touchSlop
                    if (!isTap || !userScrollEnabled || navigation == PageNavigation.SCROLL) return@awaitEachGesture
                    val leading = start.x < size.width * .2f
                    val trailing = start.x > size.width * .8f
                    if (!leading && !trailing) return@awaitEachGesture
                    val forward = if (reverse) leading else trailing
                    val target = (pagerState.currentPage + if (forward) 1 else -1)
                        .coerceIn(0, pages.lastIndex)
                    if (target != pagerState.currentPage) {
                        up.consume()
                        animationScope.launch { pagerState.animateScrollToPage(target) }
                    }
                }
            }
    ) {
        val content: @Composable (Int) -> Unit = { pageIndex ->
            val offset = (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
            val outgoingPage = pagerState.settledPage
            val incoming = pageIndex != outgoingPage && abs(offset) < 1f
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(if (incoming) 1f else 0f)
                    .graphicsLayer {
                        if (pageIndex == outgoingPage) {
                            if (navigation == PageNavigation.HORIZONTAL) {
                                translationX = offset * size.width * if (reverse) -1f else 1f
                            } else {
                                translationY = offset * size.height
                            }
                        }
                    }
                    .then(if (incoming) Modifier.shadow(12.dp) else Modifier)
                    .background(background)
            ) { pageContent(pages[pageIndex]) }
        }
        if (navigation == PageNavigation.HORIZONTAL) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                reverseLayout = reverse,
                userScrollEnabled = userScrollEnabled,
                beyondViewportPageCount = 1,
                key = { index -> pages[index].pagerKey() }
            ) { content(it) }
        } else {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = userScrollEnabled,
                beyondViewportPageCount = 1,
                key = { index -> pages[index].pagerKey() }
            ) { content(it) }
        }

        val locale = LocalConfiguration.current.locales[0]
        val formatter = remember(locale) { NumberFormat.getIntegerInstance(locale) }
        Text(
            text = androidx.compose.ui.res.stringResource(
                R.string.reading_page_indicator,
                formatter.format(pagerState.settledPage + 1),
                formatter.format(pages.size)
            ),
            color = inkFaded,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

private fun ReadingPage.firstAnchor(): ReadingAnchor = fragments.firstOrNull()?.let {
    ReadingAnchor(it.chunkIndex, it.startOffset)
} ?: ReadingAnchor(0)

// Pager keys must be Bundle-storable, so the anchor is encoded as a string.
private fun ReadingPage.pagerKey(): String = firstAnchor().let { "${it.chunkIndex}:${it.characterOffset}" }
