package com.firstresponder.kit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * −/+ around a value, with an optional caption underneath.
 *
 * Every stepped number in the app — compression rate, cylinder pressure, oxygen flow —
 * uses this, so they are all the same size, the same distance apart and disabled in the
 * same way at the ends of their range.
 *
 * @param value the number as it should read, already formatted with its unit.
 * @param caption a line under the value: the range, the unit, whatever the screen needs.
 */
@Composable
fun ValueStepper(
    value: String?,
    onAdjust: (Int) -> Unit,
    decreaseDescription: String,
    increaseDescription: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    canDecrease: Boolean = true,
    canIncrease: Boolean = true,
    valueStyle: TextStyle = MaterialTheme.typography.displaySmall,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton(
            icon = Icons.Filled.Remove,
            contentDescription = decreaseDescription,
            enabled = canDecrease,
            onClick = { onAdjust(-1) },
        )

        // Weighted rather than free: the value carries its unit, and a translated unit is
        // far wider than the English one it was laid out against. Without this the middle
        // grows and pushes the buttons off the screen.
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (value != null) {
                FitOnOneLineText(
                    text = value,
                    style = valueStyle,
                    // The value is the thing being read at arm's length; below this it is
                    // not doing its job, so it clips instead.
                    minFontSize = 20.sp,
                )
            }
            if (caption != null) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        StepperButton(
            icon = Icons.Filled.Add,
            contentDescription = increaseDescription,
            enabled = canIncrease,
            onClick = { onAdjust(1) },
        )
    }
}

@Composable
private fun StepperButton(
    icon: ImageVector,
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
