package com.firstresponder.kit.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.ui.graphics.vector.ImageVector
import com.firstresponder.kit.R
import com.firstresponder.kit.domain.PatientType
import com.firstresponder.kit.viewmodel.MetronomeViewModel

/** Every route in the app, in one place. */
object Destinations {

    const val HOME = "home"
    const val SETTINGS = "settings"

    /** Navigation argument carrying [PatientType.storageName]. */
    const val ARG_PATIENT_TYPE = MetronomeViewModel.ARG_PATIENT_TYPE

    const val METRONOME_ROUTE = "metronome/{$ARG_PATIENT_TYPE}"

    fun metronome(patientType: PatientType): String = "metronome/${patientType.storageName}"
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

    /** Additional tools, rendered as a list under the CPR buttons. Empty for now. */
    val tools: List<KitTool> = emptyList()
}
