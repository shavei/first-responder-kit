package com.firstresponder.kit.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.firstresponder.kit.FirstResponderApp
import com.firstresponder.kit.audio.MetronomeConfig
import com.firstresponder.kit.audio.MetronomeEngine
import com.firstresponder.kit.domain.PatientType
import com.firstresponder.kit.settings.SettingsRepository
import com.firstresponder.kit.util.Bpm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the metronome screen draws. */
data class MetronomeUiState(
    val patientType: PatientType,
    val bpm: Int = Bpm.DEFAULT,
    val isRunning: Boolean = false,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val keepScreenOn: Boolean = true,
) {
    val canDecreaseBpm: Boolean get() = bpm > Bpm.MIN
    val canIncreaseBpm: Boolean get() = bpm < Bpm.MAX

    /** Beat period in milliseconds — the pulse animation is scaled to it. */
    val beatPeriodMillis: Long get() = Bpm.periodMillis(bpm)
}

/**
 * Drives the [MetronomeEngine] from the UI and exposes its state.
 *
 * The view model holds no Android UI references and does no timing itself: it translates
 * user intent and persisted settings into a [MetronomeConfig], and the engine does the
 * rest on its own thread.
 */
class MetronomeViewModel(
    private val engine: MetronomeEngine,
    settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val patientType: PatientType =
        PatientType.fromStorageName(savedStateHandle[ARG_PATIENT_TYPE])

    /**
     * BPM chosen on this screen. Null means "use the saved default" — adjusting the rate
     * mid-session is intentionally not persisted; the default lives in Settings.
     */
    private val sessionBpm = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<MetronomeUiState> = combine(
        settingsRepository.settings,
        sessionBpm,
        engine.isRunning,
    ) { settings, sessionBpm, isRunning ->
        MetronomeUiState(
            patientType = patientType,
            bpm = Bpm.clamp(sessionBpm ?: settings.defaultBpm),
            isRunning = isRunning,
            soundEnabled = settings.soundEnabled,
            vibrationEnabled = settings.vibrationEnabled,
            keepScreenOn = settings.keepScreenOn,
        )
    }.stateIn(
        scope = viewModelScope,
        // Eager: the engine config subscription below must stay live even while the screen
        // is not collecting (e.g. during a configuration change).
        started = SharingStarted.Eagerly,
        initialValue = MetronomeUiState(patientType = patientType),
    )

    /** One emission per beat, used only to trigger the pulse animation. */
    val beats: SharedFlow<Long> = engine.beats

    init {
        // Warm the audio track up while the user is still reading the screen, so the very
        // first beat after Start is as prompt as every later one.
        viewModelScope.launch(Dispatchers.Default) { engine.prepare() }

        // Any settings or BPM change is pushed straight to the running engine.
        viewModelScope.launch {
            uiState.collect { state -> engine.updateConfig(state.toConfig()) }
        }
    }

    fun toggleRunning() {
        if (engine.isRunning.value) {
            engine.stop()
        } else {
            engine.start(uiState.value.toConfig())
        }
    }

    /** Stops the beat, e.g. when the screen is no longer in the foreground. */
    fun stop() = engine.stop()

    /** Steps the rate by [delta] BPM, clamped to the supported range. */
    fun adjustBpm(delta: Int) {
        sessionBpm.value = Bpm.clamp(uiState.value.bpm + delta)
    }

    override fun onCleared() {
        // Leaving the screen releases the timing thread and the audio track; the engine is
        // reusable and will re-prepare itself the next time the screen opens.
        engine.release()
    }

    private fun MetronomeUiState.toConfig() = MetronomeConfig(
        bpm = bpm,
        soundEnabled = soundEnabled,
        vibrationEnabled = vibrationEnabled,
    )

    companion object {
        /**
         * Key of the navigation argument carrying [PatientType.storageName]. Declared here
         * so the view model's contract does not depend on the navigation graph.
         */
        const val ARG_PATIENT_TYPE = "patientType"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as FirstResponderApp
                MetronomeViewModel(
                    engine = app.container.metronomeEngine,
                    settingsRepository = app.container.settingsRepository,
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}
