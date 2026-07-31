package com.firstresponder.kit.settings

import com.firstresponder.kit.domain.PatientType
import com.firstresponder.kit.util.Bpm
import com.firstresponder.kit.util.VibrationStrength

/**
 * Every user-configurable value in the app, in one immutable snapshot.
 *
 * The defaults below are also the values used on first launch and whenever a stored
 * value is missing or unreadable, so the app always starts in a usable state.
 */
data class UserSettings(
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    /** Strength of the per-beat pulse, on the [VibrationStrength] scale. */
    val vibrationAmplitude: Int = VibrationStrength.DEFAULT,
    val keepScreenOn: Boolean = true,
    val defaultBpm: Int = Bpm.DEFAULT,
    val defaultPatientType: PatientType = PatientType.ADULT,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val language: AppLanguage = AppLanguage.SYSTEM,
)

/** How the app picks between the light and dark colour schemes. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    companion object {
        /** Parses a stored name, falling back to [DARK] (the app default) for unknown values. */
        fun fromStorageName(name: String?): ThemeMode =
            entries.firstOrNull { it.name == name } ?: DARK
    }
}
