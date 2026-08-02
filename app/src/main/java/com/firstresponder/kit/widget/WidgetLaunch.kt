package com.firstresponder.kit.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.firstresponder.kit.MainActivity
import com.firstresponder.kit.domain.PatientType
import com.firstresponder.kit.ui.navigation.Destinations

/**
 * What a widget opens, and how the tap gets there.
 *
 * The intent carries the widget's *choices* — action, patient, rate — rather than a route
 * string. [MainActivity] is exported, so anything on the device can send it an intent, and
 * a route rebuilt from parsed enums can only ever be one of the app's own screens. A route
 * taken verbatim from an extra would be a way for another app to steer the navigation
 * graph, and an unknown one would simply crash it.
 */
object WidgetLaunch {

    private const val EXTRA_PREFIX = "com.firstresponder.kit.extra."
    private const val EXTRA_ACTION = EXTRA_PREFIX + "WIDGET_ACTION"
    private const val EXTRA_PATIENT = EXTRA_PREFIX + "PATIENT_TYPE"
    private const val EXTRA_BPM = EXTRA_PREFIX + "BPM"

    /** The intent a tap on the widget for [appWidgetId] sends. */
    fun intent(context: Context, appWidgetId: Int, config: WidgetConfig): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            // Two widgets differing only in their extras compare equal as far as
            // PendingIntent is concerned, and the second would silently reuse the first's.
            // A per-widget URI is what keeps them distinct.
            data = Uri.parse("firstresponderkit://widget/$appWidgetId")
            putExtra(EXTRA_ACTION, config.action.storageName)
            putExtra(EXTRA_PATIENT, config.patientType.storageName)
            config.bpm?.let { putExtra(EXTRA_BPM, it) }
            // Coming from the home screen should land on the tool, not on top of whatever
            // the app was left showing an hour ago.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

    fun pendingIntent(context: Context, appWidgetId: Int, config: WidgetConfig): PendingIntent =
        PendingIntent.getActivity(
            context,
            // The widget id doubles as the request code: one slot per widget, replaced
            // rather than duplicated when its configuration changes.
            appWidgetId,
            intent(context, appWidgetId, config),
            // Immutable because nothing outside this app has any business filling in the
            // rest of it; UPDATE_CURRENT so a reconfigured widget goes where it now says.
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /**
     * The route [intent] asks for, or null if it is not one of ours — a plain launcher tap,
     * or an intent from another app that named an action the app does not have.
     */
    fun routeFrom(intent: Intent?): String? {
        val actionName = intent?.getStringExtra(EXTRA_ACTION) ?: return null
        val action = WidgetAction.entries.firstOrNull { it.storageName == actionName }
            ?: return null
        val patientType = PatientType.fromStorageName(intent.getStringExtra(EXTRA_PATIENT))
        val bpm = intent.getIntExtra(EXTRA_BPM, 0)
        return routeFor(action, patientType, bpm)
    }

    /** The destination each action opens. [bpm] of 0 means "whatever Settings says". */
    fun routeFor(action: WidgetAction, patientType: PatientType, bpm: Int): String? = when (action) {
        // Nothing to navigate to: the app already starts on the home screen.
        WidgetAction.OPEN_APP -> null
        WidgetAction.OPEN_METRONOME -> Destinations.metronome(patientType, bpm)
        WidgetAction.START_METRONOME -> Destinations.metronome(patientType, bpm, autoStart = true)
        WidgetAction.OPEN_OXYGEN -> Destinations.OXYGEN
        WidgetAction.OPEN_SETTINGS -> Destinations.SETTINGS
    }
}
