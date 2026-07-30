package com.firstresponder.kit.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.firstresponder.kit.R
import com.firstresponder.kit.domain.PatientType
import com.firstresponder.kit.settings.ThemeMode
import com.firstresponder.kit.ui.components.BigActionButton
import com.firstresponder.kit.ui.components.BpmStepper
import com.firstresponder.kit.ui.components.PulseCircle
import com.firstresponder.kit.ui.components.ScreenHeader
import com.firstresponder.kit.ui.theme.FirstResponderKitTheme
import com.firstresponder.kit.util.KeepScreenOn
import com.firstresponder.kit.viewmodel.MetronomeUiState
import com.firstresponder.kit.viewmodel.MetronomeViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest

/**
 * Stateful entry point for the metronome: wires the view model, the beat animation and the
 * screen-awake flag to the stateless [MetronomeScreen] below.
 */
@Composable
fun MetronomeRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MetronomeViewModel = viewModel(factory = MetronomeViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 0f at rest, snapped to 1f on each beat and animated back down.
    val pulse = remember { Animatable(0f) }
    BeatPulseEffect(beats = viewModel.beats, pulse = pulse, beatPeriodMillis = state.beatPeriodMillis)

    // There is no foreground service, so beating on in the background would be surprising
    // (and would keep the audio track alive for nothing). Stop as soon as the screen goes
    // away; the user restarts with one tap when they come back.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.stop()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    KeepScreenOn(enabled = state.keepScreenOn && state.isRunning)

    MetronomeScreen(
        state = state,
        pulse = { pulse.value },
        onBack = onBack,
        onToggleRunning = viewModel::toggleRunning,
        onAdjustBpm = viewModel::adjustBpm,
        modifier = modifier,
    )
}

/**
 * Restarts the pulse animation on every beat.
 *
 * `collectLatest` cancels an animation still running when the next beat arrives, so fast
 * rates simply cut the decay short instead of queueing up.
 */
@Composable
private fun BeatPulseEffect(
    beats: SharedFlow<Long>,
    pulse: Animatable<Float, AnimationVector1D>,
    beatPeriodMillis: Long,
) {
    // The collector outlives BPM changes, so the period has to be read through an updated
    // state holder rather than captured once when the effect started.
    val currentPeriodMillis by rememberUpdatedState(beatPeriodMillis)
    LaunchedEffect(beats, pulse) {
        beats.collectLatest {
            pulse.snapTo(1f)
            // Decay over part of the beat so there is a visible gap before the next one.
            val duration = (currentPeriodMillis * PULSE_DECAY_FRACTION).toInt()
            pulse.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing),
            )
        }
    }
}

/** Stateless metronome UI. */
@Composable
fun MetronomeScreen(
    state: MetronomeUiState,
    pulse: () -> Float,
    onBack: () -> Unit,
    onToggleRunning: () -> Unit,
    onAdjustBpm: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(
            title = "${state.patientType.emoji}  ${stringResource(state.patientType.labelRes)}",
            onBack = onBack,
        )

        PulseCircle(
            pulse = pulse,
            ringColor = MaterialTheme.colorScheme.outline,
            fillColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${state.bpm}",
                    style = MaterialTheme.typography.displayLarge,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.bpm_unit),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            text = stringResource(R.string.compressions_per_minute),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        BpmStepper(
            bpm = state.bpm,
            onAdjust = onAdjustBpm,
            showValue = false,
        )

        BigActionButton(
            label = stringResource(if (state.isRunning) R.string.stop else R.string.start),
            icon = if (state.isRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
            onClick = onToggleRunning,
            colors = if (state.isRunning) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                )
            } else {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                )
            },
        )
    }
}

/** Fraction of one beat that the pulse takes to fade out. */
private const val PULSE_DECAY_FRACTION = 0.55

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun MetronomeScreenPreview() {
    FirstResponderKitTheme(ThemeMode.DARK) {
        Surface {
            MetronomeScreen(
                state = MetronomeUiState(patientType = PatientType.ADULT, isRunning = true),
                pulse = { 0.7f },
                onBack = {},
                onToggleRunning = {},
                onAdjustBpm = {},
            )
        }
    }
}
