package com.ziaee.frenchreader.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Rule
import org.junit.Test

/**
 * Do TextLayoutResult positions match where justified glyphs are drawn? Compose 1.7's
 * TextAlign.Justify does not (logged only); our space-widening justification must.
 */
class JustifyPixelProbeTest {
    @get:Rule val rule = createComposeRule()

    private fun probe(label: String, styled: Boolean, widen: Boolean = false) {
        val plain = "Le matin, Marie se leve tot et prepare son cafe pendant que la ville dort encore. " +
            "Elle regarde par la fenetre les rues vides, les lampadaires qui s eteignent un a un."
        val text = buildAnnotatedString {
            if (!styled) append(plain) else {
                val i = plain.indexOf("pendant")
                append(plain.substring(0, i))
                withStyle(SpanStyle(textDecoration = TextDecoration.Underline, background = Color(0x22000000))) { append("pendant") }
                append(plain.substring(i + 7))
            }
        }
        var layout: TextLayoutResult? = null
        rule.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val style = TextStyle(fontSize = 20.sp, lineHeight = 30.sp, color = Color.Black, letterSpacing = 0.1.sp)
            val measurer = androidx.compose.ui.text.rememberTextMeasurer()
            val maxW = with(density) { 320.dp.roundToPx() }
            val shown = if (!widen) text else justifyBySpaceWidening(
                text, measurer.measure(text, style, constraints = androidx.compose.ui.unit.Constraints(maxWidth = maxW), density = density),
                maxW, density, justifyLastLine = false
            )
            var v by remember { mutableStateOf(TextFieldValue(shown)) }
            BasicTextField(
                value = v, onValueChange = { v = it }, readOnly = true,
                modifier = Modifier.width(320.dp).background(Color.White).testTag("t"),
                textStyle = TextStyle(fontSize = 20.sp, lineHeight = 30.sp, color = Color.Black, letterSpacing = 0.1.sp, textAlign = if (widen) TextAlign.Start else TextAlign.Justify),
                onTextLayout = { layout = it }
            )
        }
        rule.waitForIdle()
        val map = rule.onNodeWithTag("t").captureToImage().toPixelMap()
        val l = layout!!
        for (line in 0 until l.lineCount - 1) {
            val top = l.getLineTop(line).toInt(); val bottom = l.getLineBottom(line).toInt()
            var inkRight = -1
            for (x in map.width - 1 downTo 0) {
                var dark = false
                for (y in top until minOf(bottom, map.height)) if (map[x, y].red < 0.5f) { dark = true; break }
                if (dark) { inkRight = x; break }
            }
            val lastChar = l.getLineEnd(line, visibleEnd = true) - 1
            val box = l.getBoundingBox(lastChar)
            val primary = l.getHorizontalPosition(lastChar + 1, true)
            if (widen) org.junit.Assert.assertTrue(
                "line $line: ink ends at $inkRight but layout reports ${box.right}",
                kotlin.math.abs(inkRight - box.right) < 6f && inkRight > map.width - 12
            )
            Log.i("JustifyProbe", "$label line=$line width=${map.width} inkRight=$inkRight boxRight=${box.right} primaryEnd=$primary lineRight=${l.getLineRight(line)} char='${plain.getOrNull(lastChar)}' end=${l.getLineEnd(line)} count=${l.lineCount}")
        }
    }

    @Test fun plainText() = probe("plain", styled = false)
    @Test fun styledText() = probe("styled", styled = true)
    @Test fun widenedText() = probe("widened", styled = true, widen = true)
}
