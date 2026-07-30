package com.firstresponder.kit.domain

import androidx.annotation.StringRes
import com.firstresponder.kit.R

/**
 * The patient the compressions are being performed on.
 *
 * The compression *rate* is the same for every patient type, so the metronome behaves
 * identically; the type is carried through the UI so the screen always shows what was
 * selected (and so future tools such as a weight estimator can reuse it).
 *
 * [storageName] is the stable identifier used for persistence and navigation arguments —
 * it must not change even if the enum is reordered or renamed.
 */
enum class PatientType(
    val storageName: String,
    val emoji: String,
    @param:StringRes val labelRes: Int,
) {
    INFANT("infant", "👶", R.string.patient_infant),
    CHILD("child", "🧒", R.string.patient_child),
    ADULT("adult", "🧑", R.string.patient_adult),
    ;

    companion object {
        /** Parses a [storageName], falling back to [ADULT] for unknown or missing values. */
        fun fromStorageName(name: String?): PatientType =
            entries.firstOrNull { it.storageName == name } ?: ADULT
    }
}
