package com.firstresponder.kit.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.firstresponder.kit.FirstResponderApp
import com.firstresponder.kit.domain.PatientType
import com.firstresponder.kit.settings.SettingsRepository
import com.firstresponder.kit.settings.ThemeMode
import com.firstresponder.kit.settings.UserSettings
import com.firstresponder.kit.util.Bpm
import com.firstresponder.kit.util.HapticPlayer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** State for the settings screen: the stored settings plus what the device supports. */
data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val hasVibrator: Boolean = true,
)

/** Reads and writes [UserSettings]. Every change is persisted immediately. */
class SettingsViewModel(
    private val repository: SettingsRepository,
    hapticPlayer: HapticPlayer,
) : ViewModel() {

    private val hasVibrator = hapticPlayer.isAvailable

    val uiState: StateFlow<SettingsUiState> = repository.settings
        .map { settings -> SettingsUiState(settings = settings, hasVibrator = hasVibrator) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = SettingsUiState(hasVibrator = hasVibrator),
        )

    fun setSoundEnabled(enabled: Boolean) = update { repository.setSoundEnabled(enabled) }

    fun setVibrationEnabled(enabled: Boolean) = update { repository.setVibrationEnabled(enabled) }

    fun setKeepScreenOn(enabled: Boolean) = update { repository.setKeepScreenOn(enabled) }

    fun setThemeMode(themeMode: ThemeMode) = update { repository.setThemeMode(themeMode) }

    fun setDefaultPatientType(patientType: PatientType) =
        update { repository.setDefaultPatientType(patientType) }

    /** Steps the stored default rate by [delta] BPM, clamped to the supported range. */
    fun adjustDefaultBpm(delta: Int) = update {
        repository.setDefaultBpm(Bpm.clamp(uiState.value.settings.defaultBpm + delta))
    }

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as FirstResponderApp
                SettingsViewModel(
                    repository = app.container.settingsRepository,
                    hapticPlayer = app.container.hapticPlayer,
                )
            }
        }
    }
}
