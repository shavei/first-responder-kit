package com.firstresponder.kit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.firstresponder.kit.R
import com.firstresponder.kit.domain.OxygenCylinder
import com.firstresponder.kit.domain.OxygenDevice
import com.firstresponder.kit.settings.ThemeMode
import com.firstresponder.kit.ui.components.ScreenHeader
import com.firstresponder.kit.ui.components.SectionHeader
import com.firstresponder.kit.ui.components.SettingBlock
import com.firstresponder.kit.ui.components.SingleChoiceRow
import com.firstresponder.kit.ui.components.ValueStepper
import com.firstresponder.kit.ui.theme.FirstResponderKitTheme
import com.firstresponder.kit.util.OxygenDuration

/**
 * The oxygen reference: what each device delivers, how long the cylinder lasts, and the
 * handling rules.
 *
 * The calculator's inputs live in [rememberSaveable] rather than in stored settings — a
 * gauge reading is true of one cylinder at one moment, so there is nothing worth carrying
 * over to the next time the screen is opened, but it does have to survive a rotation.
 */
@Composable
fun OxygenRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var cylinderName by rememberSaveable { mutableStateOf(OxygenCylinder.D.storageName) }
    var pressure by rememberSaveable { mutableIntStateOf(DEFAULT_PRESSURE) }
    var flow by rememberSaveable { mutableIntStateOf(DEFAULT_FLOW) }

    OxygenScreen(
        cylinder = OxygenCylinder.fromStorageName(cylinderName),
        pressureAtmospheres = pressure,
        flowLitresPerMinute = flow,
        onBack = onBack,
        onCylinderChange = { cylinderName = it.storageName },
        onAdjustPressure = { delta ->
            pressure = OxygenDuration.clampPressure(pressure + delta * OxygenDuration.PRESSURE_STEP)
        },
        onAdjustFlow = { delta -> flow = OxygenDuration.clampFlow(flow + delta) },
        modifier = modifier,
    )
}

/** Stateless oxygen UI. */
@Composable
fun OxygenScreen(
    cylinder: OxygenCylinder,
    pressureAtmospheres: Int,
    flowLitresPerMinute: Int,
    onBack: () -> Unit,
    onCylinderChange: (OxygenCylinder) -> Unit,
    onAdjustPressure: (Int) -> Unit,
    onAdjustFlow: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        ScreenHeader(title = stringResource(R.string.oxygen), onBack = onBack)

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            SectionHeader(stringResource(R.string.oxygen_delivery))
            OxygenDevice.entries.forEach { device -> DeliveryRow(device) }

            HorizontalDivider(modifier = Modifier.padding(top = 16.dp))

            SectionHeader(stringResource(R.string.oxygen_remaining))
            SettingBlock(title = stringResource(R.string.oxygen_cylinder)) {
                SingleChoiceRow(
                    options = OxygenCylinder.entries,
                    selected = cylinder,
                    label = { option -> stringResource(option.labelRes) },
                    onSelect = onCylinderChange,
                )
            }
            SettingBlock(title = stringResource(R.string.oxygen_pressure)) {
                ValueStepper(
                    value = stringResource(R.string.oxygen_pressure_value, pressureAtmospheres),
                    onAdjust = onAdjustPressure,
                    decreaseDescription = stringResource(R.string.oxygen_decrease_pressure),
                    increaseDescription = stringResource(R.string.oxygen_increase_pressure),
                    canDecrease = pressureAtmospheres > OxygenDuration.PRESSURE_RANGE.first,
                    canIncrease = pressureAtmospheres < OxygenDuration.PRESSURE_RANGE.last,
                )
            }
            SettingBlock(title = stringResource(R.string.oxygen_flow)) {
                ValueStepper(
                    value = stringResource(R.string.oxygen_flow_value, flowLitresPerMinute),
                    onAdjust = onAdjustFlow,
                    decreaseDescription = stringResource(R.string.oxygen_decrease_flow),
                    increaseDescription = stringResource(R.string.oxygen_increase_flow),
                    canDecrease = flowLitresPerMinute > OxygenDuration.FLOW_RANGE.first,
                    canIncrease = flowLitresPerMinute < OxygenDuration.FLOW_RANGE.last,
                )
            }
            RemainingTime(
                minutes = OxygenDuration.remainingMinutes(
                    pressureAtmospheres = pressureAtmospheres,
                    capacityLitres = cylinder.capacityLitres,
                    flowLitresPerMinute = flowLitresPerMinute,
                ),
            )

            HorizontalDivider(modifier = Modifier.padding(top = 16.dp))

            SectionHeader(stringResource(R.string.oxygen_safety))
            SafetyPoint(stringResource(R.string.oxygen_safety_fire))
            SafetyPoint(stringResource(R.string.oxygen_safety_portable))
            SafetyPoint(stringResource(R.string.oxygen_safety_defibrillator))
            SafetyPoint(stringResource(R.string.oxygen_safety_test))

            Text(
                text = stringResource(R.string.disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}

/** One row of the delivery table: the device, and what it puts into the patient. */
@Composable
private fun DeliveryRow(device: OxygenDevice, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Text(text = stringResource(device.labelRes), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(
                R.string.oxygen_flow_range,
                device.flowLitresPerMinute.first,
                device.flowLitresPerMinute.last,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(
                R.string.oxygen_delivered_range,
                device.deliveredPercent.first,
                device.deliveredPercent.last,
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** The calculator's answer, with the formula it came from and what it does not account for. */
@Composable
private fun RemainingTime(minutes: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = formatDuration(minutes),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.oxygen_formula),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.oxygen_until_empty),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Reads [minutes] out as a duration: minutes on their own under the hour, hours and
 * minutes above it, and the hours alone when they come out even.
 *
 * Both halves go through plurals — a language does not have to inflect the way English
 * does, and Hebrew, which the app also speaks, has a form for two of something.
 */
@Composable
private fun formatDuration(minutes: Int): String {
    if (minutes < MINUTES_PER_HOUR) {
        return pluralStringResource(R.plurals.oxygen_minutes, minutes, minutes)
    }

    val wholeHours = minutes / MINUTES_PER_HOUR
    val remainingMinutes = minutes % MINUTES_PER_HOUR
    val hoursText = pluralStringResource(R.plurals.oxygen_hours, wholeHours, wholeHours)
    if (remainingMinutes == 0) return hoursText

    return stringResource(
        R.string.oxygen_hours_minutes,
        hoursText,
        pluralStringResource(R.plurals.oxygen_minutes, remainingMinutes, remainingMinutes),
    )
}

@Composable
private fun SafetyPoint(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(text = BULLET, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/** Gauge reading the calculator opens on: a full portable cylinder. */
private const val DEFAULT_PRESSURE = 150

/** The flow every device on the sheet is run at, at the top of its range. */
private const val DEFAULT_FLOW = 15

private const val MINUTES_PER_HOUR = 60

private const val BULLET = "•"

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun OxygenScreenPreview() {
    FirstResponderKitTheme(ThemeMode.DARK) {
        Surface {
            OxygenScreen(
                cylinder = OxygenCylinder.D,
                pressureAtmospheres = DEFAULT_PRESSURE,
                flowLitresPerMinute = DEFAULT_FLOW,
                onBack = {},
                onCylinderChange = {},
                onAdjustPressure = {},
                onAdjustFlow = {},
            )
        }
    }
}
