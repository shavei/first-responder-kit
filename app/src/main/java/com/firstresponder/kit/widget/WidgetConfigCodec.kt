package com.firstresponder.kit.widget

import com.firstresponder.kit.domain.PatientType

/**
 * Turns a [WidgetConfig] into flat key/value pairs and back.
 *
 * Kept apart from the store that writes them so the format — which has to survive app
 * upgrades, since a widget the user placed a year ago is still on their home screen — is
 * covered by ordinary unit tests instead of only ever running against a real
 * `SharedPreferences`.
 *
 * Decoding never fails. Every unknown, missing or malformed value falls back to the
 * default for that field, so a partially written or hand-edited store yields a working
 * widget rather than a crash on the home screen.
 */
object WidgetConfigCodec {

    private const val KEY_ACTION = "action"
    private const val KEY_PATIENT = "patient"
    private const val KEY_BPM = "bpm"
    private const val KEY_ICON = "icon"
    private const val KEY_VALUE = "value"
    private const val KEY_LABEL = "label"
    private const val KEY_SHOW_LABEL = "show_label"
    private const val KEY_BACKGROUND = "background"
    private const val KEY_FOREGROUND = "foreground"
    private const val KEY_OPACITY = "opacity"
    private const val KEY_CORNERS = "corners"
    private const val KEY_TEXT_SCALE = "text_scale"

    /** Every key the codec writes, so the store can delete a widget's settings wholesale. */
    val keys: List<String> = listOf(
        KEY_ACTION, KEY_PATIENT, KEY_BPM, KEY_ICON, KEY_VALUE, KEY_LABEL, KEY_SHOW_LABEL,
        KEY_BACKGROUND, KEY_FOREGROUND, KEY_OPACITY, KEY_CORNERS, KEY_TEXT_SCALE,
    )

    /**
     * The config as strings.
     *
     * Optional fields — a rate that follows Settings, an automatic foreground — are left
     * out entirely rather than written as a sentinel, so "absent" and "not chosen" are the
     * same state and a value added in a later version reads as its default on old stores.
     */
    fun encode(config: WidgetConfig): Map<String, String> {
        val sanitized = config.sanitized()
        return buildMap {
            put(KEY_ACTION, sanitized.action.storageName)
            put(KEY_PATIENT, sanitized.patientType.storageName)
            sanitized.bpm?.let { put(KEY_BPM, it.toString()) }
            put(KEY_ICON, sanitized.icon.storageName)
            put(KEY_VALUE, sanitized.value.storageName)
            sanitized.label?.let { put(KEY_LABEL, it) }
            put(KEY_SHOW_LABEL, sanitized.showLabel.toString())
            put(KEY_BACKGROUND, sanitized.background.storageName)
            sanitized.foreground?.let { put(KEY_FOREGROUND, it.storageName) }
            put(KEY_OPACITY, sanitized.backgroundOpacity.toString())
            put(KEY_CORNERS, sanitized.cornerPercent.toString())
            put(KEY_TEXT_SCALE, sanitized.textScale.toString())
        }
    }

    /** Reads a config back out of [read], which answers null for anything it does not hold. */
    fun decode(read: (String) -> String?): WidgetConfig {
        val defaults = WidgetConfig()
        return WidgetConfig(
            action = WidgetAction.fromStorageName(read(KEY_ACTION)),
            patientType = PatientType.fromStorageName(read(KEY_PATIENT)),
            bpm = read(KEY_BPM)?.toIntOrNull(),
            icon = WidgetIcon.fromStorageName(read(KEY_ICON)),
            value = WidgetValue.fromStorageName(read(KEY_VALUE)),
            label = read(KEY_LABEL),
            showLabel = read(KEY_SHOW_LABEL)?.toBooleanStrictOrNull() ?: defaults.showLabel,
            background = WidgetColor.fromStorageName(read(KEY_BACKGROUND)),
            // Absent means automatic contrast; a name that no longer exists would too, but
            // only after a colour was removed from the palette, which cannot happen silently.
            foreground = read(KEY_FOREGROUND)
                ?.let { name -> WidgetColor.entries.firstOrNull { it.storageName == name } },
            backgroundOpacity = read(KEY_OPACITY)?.toIntOrNull() ?: defaults.backgroundOpacity,
            cornerPercent = read(KEY_CORNERS)?.toIntOrNull() ?: defaults.cornerPercent,
            textScale = read(KEY_TEXT_SCALE)?.toIntOrNull() ?: defaults.textScale,
        ).sanitized()
    }

    /** Convenience for callers that already hold the pairs in a map. */
    fun decode(values: Map<String, String?>): WidgetConfig = decode(values::get)
}
