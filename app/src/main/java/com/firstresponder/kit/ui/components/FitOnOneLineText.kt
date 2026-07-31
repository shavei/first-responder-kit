package com.firstresponder.kit.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/**
 * Text that shrinks itself until it fits on a single line.
 *
 * The app is short of horizontal space in exactly the places where the words are longest:
 * option chips split four ways, and the number between two 72 dp stepper buttons. A name
 * like "Newborn" has nowhere to wrap, and a translated unit — "אטמוספרות" for what English
 * writes "atm" — is several times the width the English layout was measured against.
 * Stepping the size down handles both, and handles them at large system font scales too,
 * where a hand-picked smaller size would simply break at a different setting.
 */
@Composable
fun FitOnOneLineText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    minFontSize: TextUnit = DEFAULT_MIN_FONT_SIZE,
) {
    val startingSize = if (style.fontSize.isSpecified) style.fontSize else DEFAULT_FONT_SIZE
    var fontSize by remember(text, startingSize) { mutableStateOf(startingSize) }
    // Drawing is held back while the size is still being narrowed down, so the oversized
    // first pass never reaches the screen.
    var settled by remember(text, startingSize) { mutableStateOf(false) }

    Text(
        text = text,
        // The line height belongs to the size the style was designed at; keeping it while
        // the text shrinks would leave a growing gap above and below.
        style = style.copy(fontSize = fontSize, lineHeight = TextUnit.Unspecified),
        color = color,
        maxLines = 1,
        softWrap = false,
        // Only reachable once the floor is hit — a translation long enough to need it is
        // better clipped than shrunk past reading size.
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = modifier.drawWithContent { if (settled) drawContent() },
        onTextLayout = { result ->
            if (result.hasVisualOverflow && fontSize > minFontSize) {
                fontSize = (fontSize.value - FONT_SIZE_STEP).sp
            } else {
                settled = true
            }
        },
    )
}

/** Smallest size the text is allowed to shrink to before it is clipped instead. */
private val DEFAULT_MIN_FONT_SIZE = 11.sp

/** Used only when the caller's style carries no size of its own. */
private val DEFAULT_FONT_SIZE = 16.sp

private const val FONT_SIZE_STEP = 1f
