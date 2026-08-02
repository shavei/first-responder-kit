package com.firstresponder.kit.widget

import android.content.Context
import com.firstresponder.kit.settings.AppLanguage
import com.firstresponder.kit.util.Bpm

/**
 * Where each widget's configuration lives.
 *
 * `SharedPreferences` rather than the DataStore the rest of the app settings use, for one
 * reason: a widget is drawn from a broadcast receiver, which has milliseconds to fill in a
 * `RemoteViews` and no lifecycle to suspend against. DataStore reads are asynchronous by
 * design — the right call for the UI, where nothing may block the first frame, and the
 * wrong one here, where the alternative to a synchronous read is blocking a broadcast on a
 * coroutine anyway.
 *
 * The store also keeps a copy of the two app settings a widget needs to draw itself — the
 * language and the default rate — refreshed by [KitWidget.refreshAll] whenever the app
 * goes to the background. Without them the provider would have to open the DataStore on
 * every update just to find out whether to write "110" or "מבוגר".
 */
class WidgetStore(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** The configuration for [appWidgetId], or the defaults if it has never been saved. */
    fun load(appWidgetId: Int): WidgetConfig =
        WidgetConfigCodec.decode { key -> preferences.getString(keyFor(appWidgetId, key), null) }

    /** True once the user has been through the configuration screen for this widget. */
    fun isConfigured(appWidgetId: Int): Boolean =
        preferences.contains(keyFor(appWidgetId, CONFIGURED))

    fun save(appWidgetId: Int, config: WidgetConfig) {
        val values = WidgetConfigCodec.encode(config)
        preferences.edit().apply {
            // Written key by key rather than as one blob so a field added later reads as
            // its default on a widget saved by an older version.
            WidgetConfigCodec.keys.forEach { key ->
                val value = values[key]
                // An optional field that is now unset has to have its old value cleared,
                // or the widget would keep drawing with the setting the user just removed.
                if (value == null) {
                    remove(keyFor(appWidgetId, key))
                } else {
                    putString(keyFor(appWidgetId, key), value)
                }
            }
            putBoolean(keyFor(appWidgetId, CONFIGURED), true)
        }.apply()
    }

    /** Forgets a widget the user has dragged off the home screen. */
    fun delete(appWidgetId: Int) {
        preferences.edit().apply {
            WidgetConfigCodec.keys.forEach { key -> remove(keyFor(appWidgetId, key)) }
            remove(keyFor(appWidgetId, CONFIGURED))
        }.apply()
    }

    /** Mirrors the app settings the widgets draw with. See the class comment. */
    fun saveAppState(language: AppLanguage, defaultBpm: Int) {
        preferences.edit()
            .putString(APP_LANGUAGE, language.storageName)
            .putInt(APP_DEFAULT_BPM, Bpm.clamp(defaultBpm))
            .apply()
    }

    /** The language the app was last in — the one the widgets should be written in. */
    fun appLanguage(): AppLanguage =
        AppLanguage.fromStorageName(preferences.getString(APP_LANGUAGE, null))

    /** The rate a widget that follows Settings should show and start at. */
    fun defaultBpm(): Int = Bpm.clamp(preferences.getInt(APP_DEFAULT_BPM, Bpm.DEFAULT))

    private fun keyFor(appWidgetId: Int, key: String) = "widget_${appWidgetId}_$key"

    private companion object {
        const val NAME = "widget_config"
        const val CONFIGURED = "configured"
        const val APP_LANGUAGE = "app_language"
        const val APP_DEFAULT_BPM = "app_default_bpm"
    }
}
