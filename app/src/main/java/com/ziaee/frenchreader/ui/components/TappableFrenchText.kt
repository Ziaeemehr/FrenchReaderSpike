package com.ziaee.frenchreader.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    textAlign: TextAlign? = null
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text,
        modifier = modifier.pointerInput(text) {
            detectTapGestures { pos ->
                val result = layout ?: return@detectTapGestures
                val word = extractWordAt(text, result.getOffsetForPosition(pos))
                if (word.isNotBlank()) onWordTap(word)
            }
        },
        style = style,
        fontStyle = fontStyle,
        textAlign = textAlign,
        onTextLayout = { layout = it }
    )
}
