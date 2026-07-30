package com.firstresponder.kit.domain

import androidx.annotation.StringRes
import com.firstresponder.kit.R

/**
 * The patient the compressions are being performed on, together with the BLS values that
 * apply to them.
 *
 * The numbers below are transcribed from the MDA BLS practical-exam comparison table
 * (adult / child / infant / newborn), which is the protocol this kit is built against.
 * Keeping them on the enum means the rate the metronome beats at and the guidance shown
 * next to it can never disagree, and that adding a patient type is a single row.
 *
 * Note the newborn column: its rate is 120 *events* per minute — 90 compressions plus 30
 * breaths at 3:1 — not 120 compressions, which is why it carries its own unit label and a
 * rate that is fixed rather than a range.
 *
 * [storageName] is the stable identifier used for persistence and navigation arguments —
 * it must not change even if the enum is reordered or renamed.
 */
enum class PatientType(
    val storageName: String,
    val emoji: String,
    @param:StringRes val labelRes: Int,
    /** Rates the metronome may be set to for this patient, in beats per minute. */
    val rateRange: IntRange,
    /** What one beat means: compressions per minute, or events per minute for a newborn. */
    @param:StringRes val rateUnitRes: Int,
    /** Compressions to breaths with a single rescuer. */
    val singleRescuerRatio: String,
    /** Compressions to breaths with two rescuers. */
    val twoRescuerRatio: String,
    @param:StringRes val depthRes: Int,
    @param:StringRes val techniqueRes: Int,
) {
    NEWBORN(
        storageName = "newborn",
        emoji = "🍼",
        labelRes = R.string.patient_newborn,
        rateRange = 120..120,
        rateUnitRes = R.string.events_per_minute,
        singleRescuerRatio = "3:1",
        twoRescuerRatio = "3:1",
        depthRes = R.string.depth_third_of_chest,
        techniqueRes = R.string.technique_two_thumbs,
    ),
    INFANT(
        storageName = "infant",
        emoji = "👶",
        labelRes = R.string.patient_infant,
        rateRange = 100..120,
        rateUnitRes = R.string.compressions_per_minute,
        singleRescuerRatio = "30:2",
        twoRescuerRatio = "15:2",
        depthRes = R.string.depth_third_of_chest_4cm,
        techniqueRes = R.string.technique_two_fingers_or_thumbs,
    ),
    CHILD(
        storageName = "child",
        emoji = "🧒",
        labelRes = R.string.patient_child,
        rateRange = 100..120,
        rateUnitRes = R.string.compressions_per_minute,
        singleRescuerRatio = "30:2",
        twoRescuerRatio = "15:2",
        depthRes = R.string.depth_third_of_chest_5cm,
        techniqueRes = R.string.technique_one_or_two_hands,
    ),
    ADULT(
        storageName = "adult",
        emoji = "🧑",
        labelRes = R.string.patient_adult,
        rateRange = 100..120,
        rateUnitRes = R.string.compressions_per_minute,
        singleRescuerRatio = "30:2",
        twoRescuerRatio = "30:2",
        depthRes = R.string.depth_5_to_6_cm,
        techniqueRes = R.string.technique_two_hands,
    ),
    ;

    /** True when one and two rescuers use the same ratio, so it need only be shown once. */
    val hasSingleRatio: Boolean get() = singleRescuerRatio == twoRescuerRatio

    /** Brings a stored, stepped or typed rate into the range this patient allows. */
    fun clampRate(bpm: Int): Int = bpm.coerceIn(rateRange)

    companion object {
        /** Parses a [storageName], falling back to [ADULT] for unknown or missing values. */
        fun fromStorageName(name: String?): PatientType =
            entries.firstOrNull { it.storageName == name } ?: ADULT
    }
}
