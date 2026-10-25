package com.ziaee.frenchreader.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import com.ziaee.frenchreader.ui.extractWordAt

/** Plain text whose individual words can be tapped; [onWordTap] gets the tapped word. */
@Composable
fun TappableFrenchText(
    text: String,
    onWordTap: (String) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    fontStyle: FontStyle? = null,
    textAlign: TextAlign? = null,
    selectable: Boolean = false
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    // Bumped on every word tap so a leftover selection (and its handles) is dropped
    // instead of floating over the dictionary sheet.
    var selectionResets by remember { mutableIntStateOf(0) }
    val content: @Composable (Modifier) -> Unit = { textModifier ->
        Text(
            text,
            modifier = textModifier.pointerInput(text, selectable) {
                if (selectable) {
                    awaitEachGesture {
                        // Initial pass: SelectionContainer consumes taps while a selection is shown, and a
                        // tap must still reach the dictionary. A down a parent already took is not a tap.
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        var isTap = !down.isConsumed
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val elapsed = change.uptimeMillis - down.uptimeMillis
                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop ||
                                elapsed >= viewConfiguration.longPressTimeoutMillis ||
                                event.changes.size > 1
                            ) isTap = false
                            if (!change.pressed) {
                                if (isTap) {
                                    val result = layout
                                    if (result != null) {
                                        val word = extractWordAt(text, result.getOffsetForPosition(change.position))
                                        if (word.isNotBlank()) {
                                            selectionResets++
                                            onWordTap(word)
                                        }
                                    }
                                }
                                break
                            }
                        } while (true)
                    }
                } else {
                    detectTapGestures { pos ->
                        val result = layout ?: return@detectTapGestures
                        val word = extractWordAt(text, result.getOffsetForPosition(pos))
                        if (word.isNotBlank()) onWordTap(word)
                    }
                }
            },
            style = style,
            fontStyle = fontStyle,
            textAlign = textAlign,
            onTextLayout = { layout = it }
        )
    }
    // The caller's modifier (e.g. Row weight) goes on a Box: SelectionContainer wraps its
    // content in an inner layout, so a weight passed to it never reaches the Row.
    if (selectable) Box(modifier, contentAlignment = Alignment.Center) { key(selectionResets) { SelectionContainer { content(Modifier) } } } else content(modifier)
}
