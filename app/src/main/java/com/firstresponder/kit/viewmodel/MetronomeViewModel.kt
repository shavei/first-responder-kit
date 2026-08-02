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
import com.firstresponder.kit.settings.UserSettings
import com.firstresponder.kit.util.Bpm
import com.firstresponder.kit.util.VibrationStrength
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the metronome screen draws. */
data class MetronomeUiState(
    val patientType: PatientType,
    val bpm: Int = patientType.clampRate(Bpm.DEFAULT),
    val isRunning: Boolean = false,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val vibrationAmplitude: Int = VibrationStrength.DEFAULT,
    val keepScreenOn: Boolean = true,
) {
    /** The rates this patient's protocol allows — a single value for a newborn. */
    val rateRange: IntRange get() = patientType.rateRange

    val canDecreaseBpm: Boolean get() = bpm > rateRange.first
    val canIncreaseBpm: Boolean get() = bpm < rateRange.last

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
     * Whether to be beating already when the screen appears.
     *
     * Set only by a home-screen widget configured to start compressions: the point of that
     * widget is that one tap on a locked phone is the whole interaction, so waiting for a
     * second tap on Start would defeat it.
     */
    private val autoStart: Boolean = savedStateHandle[ARG_AUTO_START] ?: false

    /**
     * BPM chosen on this screen. Null means "use the saved default" — adjusting the rate
     * mid-session is intentionally not persisted; the default lives in Settings.
     *
     * A widget may open the screen on a rate of its own, which is where a non-null starting
     * value comes from.
     */
    private val sessionBpm = MutableStateFlow(
        savedStateHandle.get<Int>(ARG_BPM)
            ?.takeIf { it > 0 }
            ?.let { patientType.clampRate(Bpm.clamp(it)) },
    )

    val uiState: StateFlow<MetronomeUiState> = combine(
        settingsRepository.settings,
        sessionBpm,
        engine.isRunning,
    ) { settings, sessionBpm, isRunning ->
        MetronomeUiState(
            patientType = patientType,
            // The patient's protocol wins over the saved default: a newborn is beaten at
            // 120 events a minute whatever rate the user prefers for an adult.
            bpm = patientType.clampRate(Bpm.clamp(sessionBpm ?: settings.defaultBpm)),
            isRunning = isRunning,
            soundEnabled = settings.soundEnabled,
            vibrationEnabled = settings.vibrationEnabled,
            vibrationAmplitude = settings.vibrationAmplitude,
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
        viewModelScope.launch(Dispatchers.Default) {
            engine.prepare()
            if (autoStart) {
                // Built from the settings directly rather than from the UI state, which
                // starts on a placeholder: a widget that says 110 has to beat 110 from the
                // first beat, not from whenever the store gets round to answering. The
                // config the collector below pushes is identical, so nothing changes on
                // arrival either.
                engine.start(configFor(settingsRepository.settings.first()))
            }
        }

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

    /** Steps the rate by [delta] BPM, clamped to the range this patient's protocol allows. */
    fun adjustBpm(delta: Int) {
        sessionBpm.value = patientType.clampRate(uiState.value.bpm + delta)
    }

    override fun onCleared() {
        // Leaving the screen releases the timing thread and the audio track; the engine is
        // reusable and will re-prepare itself the next time the screen opens.
        engine.release()
    }

    /** The engine settings this screen implies, for a caller that only has [settings]. */
    private fun configFor(settings: UserSettings) = MetronomeConfig(
        bpm = patientType.clampRate(Bpm.clamp(sessionBpm.value ?: settings.defaultBpm)),
        soundEnabled = settings.soundEnabled,
        vibrationEnabled = settings.vibrationEnabled,
        vibrationAmplitude = settings.vibrationAmplitude,
    )

    private fun MetronomeUiState.toConfig() = MetronomeConfig(
        bpm = bpm,
        soundEnabled = soundEnabled,
        vibrationEnabled = vibrationEnabled,
        vibrationAmplitude = vibrationAmplitude,
    )

    companion object {
        /**
         * Key of the navigation argument carrying [PatientType.storageName]. Declared here
         * so the view model's contract does not depend on the navigation graph.
         */
        const val ARG_PATIENT_TYPE = "patientType"

        /** Rate to open on, or 0 to follow the default saved in Settings. */
        const val ARG_BPM = "bpm"

        /** Whether the metronome starts beating without waiting to be told. */
        const val ARG_AUTO_START = "autostart"

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
