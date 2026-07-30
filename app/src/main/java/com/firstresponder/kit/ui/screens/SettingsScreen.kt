package com.firstresponder.kit.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.firstresponder.kit.R
import com.firstresponder.kit.domain.PatientType
import com.firstresponder.kit.settings.ThemeMode
import com.firstresponder.kit.ui.components.BpmStepper
import com.firstresponder.kit.ui.components.ScreenHeader
import com.firstresponder.kit.ui.components.SectionHeader
import com.firstresponder.kit.ui.components.SettingBlock
import com.firstresponder.kit.ui.components.SettingToggleRow
import com.firstresponder.kit.ui.components.SingleChoiceRow
import com.firstresponder.kit.ui.components.VibrationStrengthSlider
import com.firstresponder.kit.ui.theme.FirstResponderKitTheme
import com.firstresponder.kit.viewmodel.SettingsUiState
import com.firstresponder.kit.viewmodel.SettingsViewModel

/** Stateful settings entry point. */
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsScreen(
        state = state,
        onBack = onBack,
        onSoundChange = viewModel::setSoundEnabled,
        onVibrationChange = viewModel::setVibrationEnabled,
        onVibrationAmplitudeChange = viewModel::setVibrationAmplitude,
        onKeepScreenOnChange = viewModel::setKeepScreenOn,
        onAdjustDefaultBpm = viewModel::adjustDefaultBpm,
        onDefaultPatientTypeChange = viewModel::setDefaultPatientType,
        onThemeModeChange = viewModel::setThemeMode,
        modifier = modifier,
    )
}

/**
 * Stateless settings UI. Every change is written straight through to storage — there is no
 * save button to forget about.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onSoundChange: (Boolean) -> Unit,
    onVibrationChange: (Boolean) -> Unit,
    onVibrationAmplitudeChange: (Int) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onAdjustDefaultBpm: (Int) -> Unit,
    onDefaultPatientTypeChange: (PatientType) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.settings

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        ScreenHeader(title = stringResource(R.string.settings), onBack = onBack)

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            SectionHeader(stringResource(R.string.settings_feedback))
            SettingToggleRow(
                title = stringResource(R.string.settings_sound),
                summary = stringResource(R.string.settings_sound_summary),
                checked = settings.soundEnabled,
                onCheckedChange = onSoundChange,
            )
            SettingToggleRow(
                title = stringResource(R.string.settings_vibration),
                summary = stringResource(R.string.settings_vibration_summary),
                checked = settings.vibrationEnabled,
                onCheckedChange = onVibrationChange,
                // Nothing to toggle on a device without a vibrator.
                enabled = state.hasVibrator,
            )
            // Strength only matters while the pulse is actually firing, so the slider follows
            // the toggle rather than sitting there greyed out.
            if (settings.vibrationEnabled && state.hasVibrator) {
                SettingBlock(
                    title = stringResource(R.string.settings_vibration_strength),
                    summary = if (state.hasAmplitudeControl) {
                        stringResource(R.string.settings_vibration_strength_summary)
                    } else {
                        stringResource(R.string.settings_vibration_strength_duration)
                    },
                ) {
                    VibrationStrengthSlider(
                        amplitude = settings.vibrationAmplitude,
                        onAmplitudeChange = onVibrationAmplitudeChange,
                    )
                }
            }

            HorizontalDivider()

            SectionHeader(stringResource(R.string.settings_display))
            SettingToggleRow(
                title = stringResource(R.string.settings_keep_awake),
                summary = stringResource(R.string.settings_keep_awake_summary),
                checked = settings.keepScreenOn,
                onCheckedChange = onKeepScreenOnChange,
            )
            SettingBlock(title = stringResource(R.string.settings_theme)) {
                SingleChoiceRow(
                    options = ThemeMode.entries,
                    selected = settings.themeMode,
                    label = { mode -> stringResource(mode.labelRes()) },
                    onSelect = onThemeModeChange,
                )
            }

            HorizontalDivider()

            SectionHeader(stringResource(R.string.settings_defaults))
            SettingBlock(title = stringResource(R.string.settings_default_bpm)) {
                BpmStepper(bpm = settings.defaultBpm, onAdjust = onAdjustDefaultBpm)
            }
            SettingBlock(title = stringResource(R.string.settings_default_patient)) {
                SingleChoiceRow(
                    options = PatientType.entries,
                    selected = settings.defaultPatientType,
                    // Stacked rather than side by side: with four patient types there is
                    // not enough width for the emoji and the name to share a line.
                    emoji = { type -> type.emoji },
                    label = { type -> stringResource(type.labelRes) },
                    onSelect = onDefaultPatientTypeChange,
                )
            }

            Text(
                text = stringResource(R.string.disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}

/** Label for each theme option. Kept next to the screen that renders them. */
private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun SettingsScreenPreview() {
    FirstResponderKitTheme(ThemeMode.DARK) {
        Surface {
            SettingsScreen(
                state = SettingsUiState(),
                onBack = {},
                onSoundChange = {},
                onVibrationChange = {},
                onVibrationAmplitudeChange = {},
                onKeepScreenOnChange = {},
                onAdjustDefaultBpm = {},
                onDefaultPatientTypeChange = {},
                onThemeModeChange = {},
            )
        }
    }
}
