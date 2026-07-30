package com.firstresponder.kit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.firstresponder.kit.R
import com.firstresponder.kit.util.VibrationStrength
import kotlin.math.roundToInt

/**
 * Slider over the vibrator's full range, from the weakest pulse the motor can produce to its
 * hardware maximum.
 *
 * [onAmplitudeChange] fires when the drag settles rather than on every pixel: each change is
 * a write to storage and a preview pulse, and neither wants to happen sixty times a second.
 * The readout still follows the finger, driven by local state that re-syncs whenever the
 * stored value changes.
 */
@Composable
fun VibrationStrengthSlider(
    amplitude: Int,
    onAmplitudeChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragged by remember(amplitude) { mutableFloatStateOf(amplitude.toFloat()) }

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = dragged,
            onValueChange = { dragged = it },
            onValueChangeFinished = { onAmplitudeChange(dragged.roundToInt()) },
            valueRange = VibrationStrength.MIN.toFloat()..VibrationStrength.MAX.toFloat(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            EndLabel(stringResource(R.string.vibration_strength_min))
            Text(
                text = stringResource(
                    R.string.percent,
                    VibrationStrength.percentOf(dragged.roundToInt()),
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            EndLabel(stringResource(R.string.vibration_strength_max))
        }
    }
}

@Composable
private fun EndLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Preview(showBackground = true)
@Composable
private fun VibrationStrengthSliderPreview() {
    VibrationStrengthSlider(amplitude = VibrationStrength.DEFAULT, onAmplitudeChange = {})
}
