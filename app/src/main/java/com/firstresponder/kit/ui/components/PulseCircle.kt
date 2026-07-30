package com.firstresponder.kit.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * The beat indicator: a static outer ring with a filled circle that expands on every beat.
 *
 * [pulse] is passed as a lambda rather than a plain `Float` on purpose. Reading the
 * animated value inside [drawBehind] keeps the change confined to the draw phase — no
 * recomposition and no relayout happens per beat, which is what makes the animation smooth
 * even while the timing thread is firing every half second.
 *
 * @param pulse 0f at rest, 1f at the instant of a beat.
 */
@Composable
fun PulseCircle(
    pulse: () -> Float,
    ringColor: Color,
    fillColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.drawBehind {
            val value = pulse().coerceIn(0f, 1f)
            val maxRadius = size.minDimension / 2f
            val ringStroke = (maxRadius * RING_STROKE_FRACTION).coerceAtLeast(2.dp.toPx())

            // Filled circle: grows and brightens towards the beat.
            val fillRadius = maxRadius * (REST_FRACTION + (BEAT_FRACTION - REST_FRACTION) * value)
            drawCircle(
                color = fillColor,
                radius = fillRadius,
                alpha = REST_ALPHA + (BEAT_ALPHA - REST_ALPHA) * value,
            )

            // Static ring: gives the pulse something to travel towards.
            drawCircle(
                color = ringColor,
                radius = maxRadius - ringStroke / 2f,
                style = Stroke(width = ringStroke),
            )
        },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Radius of the resting circle, as a fraction of the available radius. */
private const val REST_FRACTION = 0.62f

/** Radius at the instant of a beat. */
private const val BEAT_FRACTION = 0.94f

private const val REST_ALPHA = 0.16f
private const val BEAT_ALPHA = 0.85f

private const val RING_STROKE_FRACTION = 0.045f
