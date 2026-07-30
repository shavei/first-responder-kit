package com.firstresponder.kit.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.firstresponder.kit.R
import com.firstresponder.kit.util.Bpm

/**
 * −/+ control for the compression rate.
 *
 * Shared by the metronome screen and the settings screen so the step size and the look of
 * the control are defined exactly once. [range] defaults to the range the app supports
 * overall; the metronome screen narrows it to the one its patient's protocol allows, which
 * for a newborn is the single fixed rate — both buttons then sit disabled and the label
 * says so, rather than offering a choice the protocol does not have.
 */
@Composable
fun BpmStepper(
    bpm: Int,
    onAdjust: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showValue: Boolean = true,
    step: Int = 1,
    range: IntRange = Bpm.RANGE,
) {
    val unit = stringResource(R.string.bpm_unit)
    ValueStepper(
        value = if (showValue) "$bpm" else null,
        onAdjust = { direction -> onAdjust(direction * step) },
        decreaseDescription = stringResource(R.string.decrease_bpm),
        increaseDescription = stringResource(R.string.increase_bpm),
        modifier = modifier,
        caption = if (range.first == range.last) {
            stringResource(R.string.bpm_fixed, range.first, unit)
        } else {
            stringResource(R.string.bpm_range, range.first, range.last, unit)
        },
        canDecrease = bpm > range.first,
        canIncrease = bpm < range.last,
    )
}
