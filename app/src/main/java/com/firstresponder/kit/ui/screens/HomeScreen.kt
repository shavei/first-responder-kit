package com.firstresponder.kit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import com.firstresponder.kit.R
import com.firstresponder.kit.domain.PatientType
import com.firstresponder.kit.settings.ThemeMode
import com.firstresponder.kit.ui.components.BigActionButton
import com.firstresponder.kit.ui.components.BigOutlinedButton
import com.firstresponder.kit.ui.navigation.KitTool
import com.firstresponder.kit.ui.navigation.ToolRegistry
import com.firstresponder.kit.ui.theme.FirstResponderKitTheme

/**
 * The launcher screen: three patient-type buttons filling most of the display, with
 * Settings underneath.
 *
 * It is completely stateless — no view model, no I/O — which is what lets it draw on the
 * very first frame after process start.
 */
@Composable
fun HomeScreen(
    onPatientTypeSelected: (PatientType) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    defaultPatientType: PatientType = PatientType.ADULT,
    additionalTools: List<KitTool> = ToolRegistry.tools,
    onToolSelected: (KitTool) -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // The three primary targets. `weight` splits the free space evenly, so the buttons
        // stay as large as the display allows on any device. The patient type chosen as the
        // default in Settings is accented, so the usual choice is obvious at a glance.
        PatientType.entries.forEach { patientType ->
            BigActionButton(
                label = stringResource(patientType.labelRes),
                emoji = patientType.emoji,
                onClick = { onPatientTypeSelected(patientType) },
                modifier = Modifier.weight(1f),
                colors = if (patientType == defaultPatientType) {
                    ButtonDefaults.buttonColors()
                } else {
                    ButtonDefaults.filledTonalButtonColors()
                },
            )
        }

        // Future tools appear here as they are registered; nothing to draw today.
        if (additionalTools.isNotEmpty()) {
            Text(
                text = stringResource(R.string.home_more_tools),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            additionalTools.forEach { tool ->
                BigOutlinedButton(
                    label = stringResource(tool.titleRes),
                    icon = tool.icon,
                    onClick = { onToolSelected(tool) },
                )
            }
        }

        BigOutlinedButton(
            label = stringResource(R.string.settings),
            icon = Icons.Filled.Settings,
            onClick = onOpenSettings,
        )

        Text(
            text = stringResource(R.string.disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun HomeScreenPreview() {
    FirstResponderKitTheme(ThemeMode.DARK) {
        Surface {
            HomeScreen(onPatientTypeSelected = {}, onOpenSettings = {})
        }
    }
}
