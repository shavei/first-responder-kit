package com.firstresponder.kit.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.ui.graphics.vector.ImageVector
import com.firstresponder.kit.R
import com.firstresponder.kit.domain.PatientType
import com.firstresponder.kit.viewmodel.MetronomeViewModel

/** Every route in the app, in one place. */
object Destinations {

    const val HOME = "home"
    const val SETTINGS = "settings"
    const val OXYGEN = "oxygen"

    /** Navigation argument carrying [PatientType.storageName]. */
    const val ARG_PATIENT_TYPE = MetronomeViewModel.ARG_PATIENT_TYPE

    /** Optional rate to open on; 0 means the one saved in Settings. */
    const val ARG_BPM = MetronomeViewModel.ARG_BPM

    /** Whether the metronome should already be beating when the screen appears. */
    const val ARG_AUTO_START = MetronomeViewModel.ARG_AUTO_START

    const val METRONOME_ROUTE =
        "metronome/{$ARG_PATIENT_TYPE}?$ARG_BPM={$ARG_BPM}&$ARG_AUTO_START={$ARG_AUTO_START}"

    /**
     * The metronome for [patientType].
     *
     * The two optional arguments exist for the home-screen widget, which can be set up to
     * open a particular rate and to have compressions already running by the time the phone
     * is out of the pocket. Navigation inside the app passes neither.
     */
    fun metronome(
        patientType: PatientType,
        bpm: Int = 0,
        autoStart: Boolean = false,
    ): String = "metronome/${patientType.storageName}?$ARG_BPM=$bpm&$ARG_AUTO_START=$autoStart"
}

/**
 * A tool in the kit.
 *
 * The CPR metronome is the only tool implemented today, but everything the home screen
 * and the nav graph need is described by this type, so adding the next one (pulse counter,
 * respiratory rate timer, GCS calculator, drug dosage calculator, …) is:
 *
 *  1. add a [KitTool] entry to [ToolRegistry.tools];
 *  2. add a matching `composable(tool.route) { … }` in `KitNavHost`.
 *
 * Nothing else in the app has to change.
 */
data class KitTool(
    val id: String,
    @param:StringRes val titleRes: Int,
    val icon: ImageVector,
    val route: String,
)

/**
 * The tools shown on the home screen.
 *
 * The CPR metronome is not listed here: it is the primary tool and gets the patient-type
 * buttons at the top of the home screen instead of a row in the list. Additional tools
 * appear below those buttons as they are added.
 */
object ToolRegistry {

    val cprMetronome = KitTool(
        id = "cpr_metronome",
        titleRes = R.string.home_subtitle,
        icon = Icons.Filled.MonitorHeart,
        route = Destinations.METRONOME_ROUTE,
    )

    val oxygen = KitTool(
        id = "oxygen",
        titleRes = R.string.oxygen,
        icon = Icons.Filled.Air,
        route = Destinations.OXYGEN,
    )

    /** Additional tools, rendered as a list under the CPR buttons. */
    val tools: List<KitTool> = listOf(oxygen)
}
