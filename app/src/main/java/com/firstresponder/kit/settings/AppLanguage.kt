package com.firstresponder.kit.settings

import java.util.Locale

/**
 * The language the app draws itself in.
 *
 * The choice is applied inside the app rather than through the system per-app language
 * setting, so it works the same on every supported version and is one tap away from the
 * screen the responder is already on.
 *
 * [storageName] is the stable persisted identifier; [languageTag] is the BCP 47 tag the
 * locale is built from, and is null for [SYSTEM], which follows the device.
 */
enum class AppLanguage(val storageName: String, val languageTag: String?) {
    SYSTEM("system", null),
    ENGLISH("english", "en"),
    HEBREW("hebrew", "he"),
    ;

    /** The locale to draw in, or null to keep whatever the device is set to. */
    val locale: Locale? get() = languageTag?.let(Locale::forLanguageTag)

    companion object {
        /** Parses a stored name, falling back to [SYSTEM] for unknown or missing values. */
        fun fromStorageName(name: String?): AppLanguage =
            entries.firstOrNull { it.storageName == name } ?: SYSTEM
    }
}
