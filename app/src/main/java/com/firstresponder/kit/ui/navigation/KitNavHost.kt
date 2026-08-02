package com.firstresponder.kit.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.firstresponder.kit.domain.PatientType
import com.firstresponder.kit.ui.screens.HomeScreen
import com.firstresponder.kit.ui.screens.MetronomeRoute
import com.firstresponder.kit.ui.screens.OxygenRoute
import com.firstresponder.kit.ui.screens.SettingsRoute

/**
 * The navigation graph.
 *
 * All transitions are disabled: navigation in an emergency tool should be instantaneous,
 * and a 300 ms slide is 300 ms of nothing useful. Adding a tool means adding one
 * `composable(...)` block here and one [KitTool] entry in [ToolRegistry].
 */
@Composable
fun KitNavHost(
    modifier: Modifier = Modifier,
    defaultPatientType: PatientType = PatientType.ADULT,
    navController: NavHostController = rememberNavController(),
    widgetRoute: String? = null,
    onWidgetRouteHandled: () -> Unit = {},
) {
    // A tap on a home-screen widget arrives as a route to open. `popUpTo(HOME)` keeps the
    // back stack from growing a screen per tap and makes Back mean "the home screen" rather
    // than "the tool I opened from the widget an hour ago". Handled once and then cleared,
    // so returning to the app later does not navigate again.
    LaunchedEffect(widgetRoute) {
        val route = widgetRoute ?: return@LaunchedEffect
        navController.navigate(route) {
            popUpTo(Destinations.HOME) { inclusive = false }
        }
        onWidgetRouteHandled()
    }

    NavHost(
        navController = navController,
        startDestination = Destinations.HOME,
        modifier = modifier,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable(Destinations.HOME) {
            HomeScreen(
                onPatientTypeSelected = { patientType ->
                    navController.navigate(Destinations.metronome(patientType))
                },
                onOpenSettings = { navController.navigate(Destinations.SETTINGS) },
                onToolSelected = { tool -> navController.navigate(tool.route) },
                defaultPatientType = defaultPatientType,
            )
        }

        composable(
            route = Destinations.METRONOME_ROUTE,
            arguments = listOf(
                navArgument(Destinations.ARG_PATIENT_TYPE) { type = NavType.StringType },
                navArgument(Destinations.ARG_BPM) {
                    type = NavType.IntType
                    // Zero: no rate asked for, so the one from Settings applies.
                    defaultValue = 0
                },
                navArgument(Destinations.ARG_AUTO_START) {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) {
            // The patient type reaches the view model through its SavedStateHandle.
            MetronomeRoute(onBack = navController::popBackStack)
        }

        composable(Destinations.OXYGEN) {
            OxygenRoute(onBack = navController::popBackStack)
        }

        composable(Destinations.SETTINGS) {
            SettingsRoute(onBack = navController::popBackStack)
        }
    }
}
