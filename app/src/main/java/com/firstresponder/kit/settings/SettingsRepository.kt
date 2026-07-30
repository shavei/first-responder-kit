package com.firstresponder.kit.settings

import com.firstresponder.kit.domain.PatientType
import kotlinx.coroutines.flow.Flow

/**
 * Read/write access to the persisted [UserSettings].
 *
 * The interface keeps the storage technology out of the view models, which makes them
 * trivially testable with an in-memory fake.
 */
interface SettingsRepository {

    /** Emits the current settings and then every change to them. */
    val settings: Flow<UserSettings>

    suspend fun setSoundEnabled(enabled: Boolean)

    suspend fun setVibrationEnabled(enabled: Boolean)

    /** Values outside the supported range are clamped by the implementation. */
    suspend fun setVibrationAmplitude(amplitude: Int)

    suspend fun setKeepScreenOn(enabled: Boolean)

    /** Values outside the supported range are clamped by the implementation. */
    suspend fun setDefaultBpm(bpm: Int)

    suspend fun setDefaultPatientType(patientType: PatientType)

    suspend fun setThemeMode(themeMode: ThemeMode)
}
