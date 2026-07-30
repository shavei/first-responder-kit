package com.firstresponder.kit.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.firstresponder.kit.domain.PatientType
import com.firstresponder.kit.util.Bpm
import com.firstresponder.kit.util.VibrationStrength
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Single DataStore instance for the process, owned by the application context. */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_settings",
)

/**
 * [SettingsRepository] backed by Jetpack DataStore.
 *
 * DataStore is used instead of SharedPreferences because reads are asynchronous (nothing
 * blocks the first frame) and it exposes changes as a [Flow], so the UI stays in sync
 * without manual listeners.
 */
class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    constructor(context: Context) : this(context.applicationContext.settingsDataStore)

    override val settings: Flow<UserSettings> = dataStore.data
        // A corrupt or unreadable store must never crash an emergency tool: fall back to
        // defaults instead.
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { preferences -> preferences.toUserSettings() }

    override suspend fun setSoundEnabled(enabled: Boolean) = edit { it[Keys.SOUND] = enabled }

    override suspend fun setVibrationEnabled(enabled: Boolean) = edit { it[Keys.VIBRATION] = enabled }

    override suspend fun setVibrationAmplitude(amplitude: Int) =
        edit { it[Keys.VIBRATION_AMPLITUDE] = VibrationStrength.clamp(amplitude) }

    override suspend fun setKeepScreenOn(enabled: Boolean) = edit { it[Keys.KEEP_SCREEN_ON] = enabled }

    override suspend fun setDefaultBpm(bpm: Int) = edit { it[Keys.DEFAULT_BPM] = Bpm.clamp(bpm) }

    override suspend fun setDefaultPatientType(patientType: PatientType) =
        edit { it[Keys.DEFAULT_PATIENT_TYPE] = patientType.storageName }

    override suspend fun setThemeMode(themeMode: ThemeMode) =
        edit { it[Keys.THEME_MODE] = themeMode.name }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }

    private fun Preferences.toUserSettings(): UserSettings {
        val defaults = UserSettings()
        return UserSettings(
            soundEnabled = this[Keys.SOUND] ?: defaults.soundEnabled,
            vibrationEnabled = this[Keys.VIBRATION] ?: defaults.vibrationEnabled,
            vibrationAmplitude = VibrationStrength.clamp(
                this[Keys.VIBRATION_AMPLITUDE] ?: defaults.vibrationAmplitude,
            ),
            keepScreenOn = this[Keys.KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
            defaultBpm = Bpm.clamp(this[Keys.DEFAULT_BPM] ?: defaults.defaultBpm),
            defaultPatientType = PatientType.fromStorageName(this[Keys.DEFAULT_PATIENT_TYPE]),
            themeMode = ThemeMode.fromStorageName(this[Keys.THEME_MODE]),
        )
    }

    private object Keys {
        val SOUND = booleanPreferencesKey("sound_enabled")
        val VIBRATION = booleanPreferencesKey("vibration_enabled")
        val VIBRATION_AMPLITUDE = intPreferencesKey("vibration_amplitude")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val DEFAULT_BPM = intPreferencesKey("default_bpm")
        val DEFAULT_PATIENT_TYPE = stringPreferencesKey("default_patient_type")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
