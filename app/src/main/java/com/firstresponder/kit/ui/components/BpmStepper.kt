package com.firstresponder.kit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton(
            icon = Icons.Filled.Remove,
            contentDescription = stringResource(R.string.decrease_bpm),
            enabled = bpm > range.first,
            onClick = { onAdjust(-step) },
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (showValue) {
                Text(
                    text = "$bpm",
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                )
            }
            val unit = stringResource(R.string.bpm_unit)
            Text(
                text = if (range.first == range.last) {
                    stringResource(R.string.bpm_fixed, range.first, unit)
                } else {
                    stringResource(R.string.bpm_range, range.first, range.last, unit)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        StepperButton(
            icon = Icons.Filled.Add,
            contentDescription = stringResource(R.string.increase_bpm),
            enabled = bpm < range.last,
            onClick = { onAdjust(step) },
        )
    }
}

@Composable
private fun StepperButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        // Oversized on purpose: adjustable one-handed without looking.
        modifier = Modifier.size(72.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(36.dp),
        )
    }
}
