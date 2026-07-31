package com.firstresponder.kit.domain

import androidx.annotation.StringRes
import com.firstresponder.kit.R

/**
 * Medical oxygen: what each device delivers, and what the cylinders hold.
 *
 * Transcribed from the medical-oxygen section of the MDA BLS practical-exam preparation
 * sheet, the same source the compression protocol on [PatientType] comes from. Keeping the
 * numbers here rather than in the screen means the delivery table and the duration
 * calculator can never disagree about a cylinder's size.
 */

/**
 * A way of giving oxygen, with the flow it is run at and the concentration that produces.
 *
 * Every device on the sheet is run at the same 10–15 LPM; what changes the delivered
 * fraction is the reservoir bag, which is why the bag-valve mask appears twice.
 */
enum class OxygenDevice(
    @param:StringRes val labelRes: Int,
    val flowLitresPerMinute: IntRange,
    /** Delivered oxygen fraction, as whole percent. */
    val deliveredPercent: IntRange,
) {
    RESERVOIR_MASK(
        labelRes = R.string.oxygen_device_reservoir_mask,
        flowLitresPerMinute = 10..15,
        deliveredPercent = 90..100,
    ),
    BVM_WITH_RESERVOIR(
        labelRes = R.string.oxygen_device_bvm_with_reservoir,
        flowLitresPerMinute = 10..15,
        deliveredPercent = 90..100,
    ),
    BVM_WITHOUT_RESERVOIR(
        labelRes = R.string.oxygen_device_bvm_without_reservoir,
        flowLitresPerMinute = 10..15,
        deliveredPercent = 40..60,
    ),
}

/**
 * A cylinder the kit carries, and its water capacity in litres — the volume the remaining
 * time is calculated from.
 */
enum class OxygenCylinder(
    val storageName: String,
    @param:StringRes val labelRes: Int,
    val capacityLitres: Double,
) {
    /** The large fixed cylinder, 20 L. */
    H("h", R.string.oxygen_cylinder_h, 20.0),

    /** The portable cylinder, 2.4 L. */
    D("d", R.string.oxygen_cylinder_d, 2.4),
    ;

    companion object {
        /** Parses a [storageName], falling back to the portable [D] cylinder. */
        fun fromStorageName(name: String?): OxygenCylinder =
            entries.firstOrNull { it.storageName == name } ?: D
    }
}
