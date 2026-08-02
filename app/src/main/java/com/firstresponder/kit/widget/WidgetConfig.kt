package com.firstresponder.kit.widget

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.firstresponder.kit.R
import com.firstresponder.kit.domain.PatientType
import com.firstresponder.kit.util.Bpm

/**
 * Everything one home-screen widget is, in one immutable snapshot.
 *
 * Every field is chosen by the user in [WidgetConfigActivity] and stored per widget, so two
 * widgets side by side can do and show completely different things. The defaults below are
 * what a freshly placed widget looks like before anything is touched: a dark tile that
 * opens the adult metronome.
 *
 * Nothing here is validated on construction — call [sanitized] before storing or drawing,
 * which brings every value into the range the app supports.
 */
data class WidgetConfig(
    /** What tapping the widget does. */
    val action: WidgetAction = WidgetAction.OPEN_METRONOME,
    /** Which patient the metronome actions open on. Ignored by the other actions. */
    val patientType: PatientType = PatientType.ADULT,
    /** Rate to show and to start at. Null follows the default rate saved in Settings. */
    val bpm: Int? = null,
    /** The glyph in the middle. [WidgetIcon.AUTO] follows the action, [WidgetIcon.NONE] hides it. */
    val icon: WidgetIcon = WidgetIcon.AUTO,
    /** The number or emoji drawn under the icon. */
    val value: WidgetValue = WidgetValue.NONE,
    /** Custom caption. Null or blank falls back to the action's own short name. */
    val label: String? = null,
    val showLabel: Boolean = true,
    val background: WidgetColor = WidgetColor.DARK,
    /** Icon and text colour. Null picks whichever of black or white reads on [background]. */
    val foreground: WidgetColor? = null,
    /** Background opacity in percent; 0 leaves the wallpaper showing through. */
    val backgroundOpacity: Int = 100,
    /** Corner rounding as a percentage of the widget's shorter side; 50 is a full circle. */
    val cornerPercent: Int = 30,
    /** Text size as a percentage of the size the widget would pick for itself. */
    val textScale: Int = 100,
) {

    /** The caption to draw, or null when there is none. */
    fun labelOrNull(): String? = label?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * Brings every value into range: an out-of-protocol rate, an opacity typed in from a
     * corrupted store, a label of nothing but spaces.
     *
     * A widget is drawn by the home screen, where there is no one to show an error to, so
     * every value is clamped rather than rejected.
     */
    fun sanitized(): WidgetConfig = copy(
        bpm = bpm?.let { patientType.clampRate(Bpm.clamp(it)) },
        label = labelOrNull()?.take(MAX_LABEL_LENGTH),
        backgroundOpacity = backgroundOpacity.coerceIn(0, 100),
        cornerPercent = cornerPercent.coerceIn(0, 50),
        textScale = textScale.coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE),
    )

    companion object {
        /** Long enough for a sentence, short enough that a corrupt store cannot bloat a tile. */
        const val MAX_LABEL_LENGTH = 40

        const val MIN_TEXT_SCALE = 50
        const val MAX_TEXT_SCALE = 200
    }
}

/**
 * What a tap on the widget does.
 *
 * Each entry is a whole shortcut: which screen opens, and — for the metronome — whether it
 * is already beating by the time the responder is looking at it. Adding one means adding an
 * entry here and a branch in [WidgetLaunch.routeFor].
 *
 * [storageName] is the stable persisted identifier and must not change.
 */
enum class WidgetAction(
    val storageName: String,
    /** Full description, shown in the list of actions on the configuration screen. */
    @param:StringRes val labelRes: Int,
    /** Short caption the widget itself falls back to when the user has not typed one. */
    @param:StringRes val shortLabelRes: Int,
    /** The glyph [WidgetIcon.AUTO] resolves to for this action. */
    val defaultIcon: WidgetIcon,
) {
    OPEN_APP(
        storageName = "open_app",
        labelRes = R.string.widget_action_open_app,
        shortLabelRes = R.string.widget_label_kit,
        defaultIcon = WidgetIcon.HEART,
    ),
    OPEN_METRONOME(
        storageName = "open_metronome",
        labelRes = R.string.widget_action_open_metronome,
        shortLabelRes = R.string.widget_label_cpr,
        defaultIcon = WidgetIcon.PULSE,
    ),
    START_METRONOME(
        storageName = "start_metronome",
        labelRes = R.string.widget_action_start_metronome,
        shortLabelRes = R.string.start,
        defaultIcon = WidgetIcon.PLAY,
    ),
    OPEN_OXYGEN(
        storageName = "open_oxygen",
        labelRes = R.string.widget_action_open_oxygen,
        shortLabelRes = R.string.oxygen,
        defaultIcon = WidgetIcon.OXYGEN,
    ),
    OPEN_SETTINGS(
        storageName = "open_settings",
        labelRes = R.string.widget_action_open_settings,
        shortLabelRes = R.string.settings,
        defaultIcon = WidgetIcon.SETTINGS,
    ),
    ;

    /** True when the patient type and rate change what this action does. */
    val usesPatient: Boolean get() = this == OPEN_METRONOME || this == START_METRONOME

    companion object {
        /** Parses a [storageName], falling back to [OPEN_METRONOME] for anything unknown. */
        fun fromStorageName(name: String?): WidgetAction =
            entries.firstOrNull { it.storageName == name } ?: OPEN_METRONOME
    }
}

/**
 * The glyph drawn in the middle of the widget.
 *
 * The drawables are declared at 96 dp so the renderer can scale one down to whatever the
 * widget's current size allows; see [WidgetLayout].
 */
enum class WidgetIcon(
    val storageName: String,
    @param:StringRes val labelRes: Int,
    /** Null for [AUTO], which resolves through the action, and for [NONE]. */
    @param:DrawableRes val drawableRes: Int?,
) {
    AUTO("auto", R.string.widget_icon_auto, null),
    HEART("heart", R.string.widget_icon_heart, R.drawable.ic_widget_heart),
    PULSE("pulse", R.string.widget_icon_pulse, R.drawable.ic_widget_pulse),
    PLAY("play", R.string.widget_icon_play, R.drawable.ic_widget_play),
    OXYGEN("oxygen", R.string.widget_icon_oxygen, R.drawable.ic_widget_oxygen),
    SETTINGS("settings", R.string.widget_icon_settings, R.drawable.ic_widget_settings),
    NONE("none", R.string.widget_icon_none, null),
    ;

    /** The drawable to draw for [action], resolving [AUTO] and leaving [NONE] as null. */
    @DrawableRes
    fun resolve(action: WidgetAction): Int? = when (this) {
        AUTO -> action.defaultIcon.drawableRes
        NONE -> null
        else -> drawableRes
    }

    companion object {
        /** Parses a [storageName], falling back to [AUTO]. */
        fun fromStorageName(name: String?): WidgetIcon =
            entries.firstOrNull { it.storageName == name } ?: AUTO
    }
}

/**
 * The one fact the widget shows at a glance, under the icon.
 *
 * All four are things a responder might want without opening anything: the rate the tile
 * would start beating at, who it is set up for, or the compression ratio for that patient.
 */
enum class WidgetValue(val storageName: String, @param:StringRes val labelRes: Int) {
    NONE("none", R.string.widget_value_none),
    RATE("rate", R.string.widget_value_rate),
    PATIENT("patient", R.string.widget_value_patient),
    RATIO("ratio", R.string.widget_value_ratio),
    ;

    companion object {
        /** Parses a [storageName], falling back to [NONE]. */
        fun fromStorageName(name: String?): WidgetValue =
            entries.firstOrNull { it.storageName == name } ?: NONE
    }
}
