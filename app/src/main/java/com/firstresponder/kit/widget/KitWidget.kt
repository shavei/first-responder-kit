package com.firstresponder.kit.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import com.firstresponder.kit.settings.AppLanguage
import com.firstresponder.kit.settings.UserSettings

/**
 * The home-screen widget.
 *
 * The provider itself is deliberately thin: it works out how much room each widget has,
 * loads that widget's configuration, and hands both to [WidgetRenderer]. Everything a
 * responder can change about a widget lives in [WidgetConfig]; everything about how it is
 * drawn lives in the renderer.
 *
 * There is no periodic update — `updatePeriodMillis` is zero. Nothing a widget shows
 * changes on its own: the rate, the patient and the caption only move when the user moves
 * them, and waking the device on a timer to redraw a tile that has not changed is exactly
 * the kind of background work an offline tool should not be doing. Updates happen when the
 * widget is placed, when it is resized, when it is reconfigured, and when the app is left
 * ([refreshAll]).
 */
class KitWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { id -> update(context, appWidgetManager, id) }
    }

    /** Called when the user resizes a widget — the one event that changes what fits. */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        update(context, appWidgetManager, appWidgetId, newOptions)
    }

    /** A widget dragged off the home screen takes its settings with it. */
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val store = WidgetStore(context)
        appWidgetIds.forEach { id -> store.delete(id) }
    }

    companion object {

        /** Redraws one widget. */
        fun update(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            options: Bundle = appWidgetManager.getAppWidgetOptions(appWidgetId),
        ) {
            val store = WidgetStore(context)
            val localized = localized(context, store.appLanguage())
            val (widthDp, heightDp) = sizeDp(context, options)

            appWidgetManager.updateAppWidget(
                appWidgetId,
                WidgetRenderer.render(
                    context = localized,
                    appWidgetId = appWidgetId,
                    config = store.load(appWidgetId),
                    widthDp = widthDp,
                    heightDp = heightDp,
                    defaultBpm = store.defaultBpm(),
                ),
            )
        }

        /** Redraws every widget the user has placed. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            manager.getAppWidgetIds(ComponentName(context, KitWidget::class.java))
                .forEach { id -> update(context, manager, id) }
        }

        /**
         * Brings the widgets in line with the app's settings.
         *
         * Called when the app goes to the background, which is the only moment the two can
         * have drifted apart: a widget showing the default rate has to show the new one
         * after it is changed in Settings, and one captioned in English has to be in Hebrew
         * after the language is switched.
         */
        fun refreshAll(context: Context, settings: UserSettings) {
            WidgetStore(context).saveAppState(
                language = settings.language,
                defaultBpm = settings.defaultBpm,
            )
            updateAll(context)
        }

        /**
         * A context whose resources answer in [language].
         *
         * A widget is drawn by the launcher, in whatever locale the *device* is set to,
         * which for this app is the wrong one: the language is chosen inside the app and
         * has to reach the home screen too.
         */
        fun localized(context: Context, language: AppLanguage): Context {
            val locale = language.locale ?: return context
            val configuration = Configuration(context.resources.configuration).apply {
                setLocale(locale)
                setLayoutDirection(locale)
            }
            return context.createConfigurationContext(configuration)
        }

        /**
         * How much room a widget has, in density-independent pixels.
         *
         * This has to be the size the widget is actually laid out at, not merely close to
         * it: the background is a bitmap stretched to fill the tile, so a size of the wrong
         * *shape* is stretched unevenly and every corner it rounded comes out as an ellipse
         * — a circle squashed into an oval, a rounded corner flattened towards square.
         *
         * From API 31 the host reports the exact sizes and there is nothing to guess at.
         * Before that all it offers is a range — the minimum width is the width in the
         * orientation the widget is widest in, the maximum height the height in the one it
         * is tallest in — and the pair for the current orientation is the best estimate
         * available.
         */
        fun sizeDp(context: Context, options: Bundle): Pair<Float, Float> {
            val landscape =
                context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            return exactSizeDp(options, landscape) ?: estimatedSizeDp(options, landscape)
        }

        /** The size the host laid the widget out at, on the versions that report it. */
        private fun exactSizeDp(options: Bundle, landscape: Boolean): Pair<Float, Float>? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
            @Suppress("DEPRECATION")
            val sizes = options
                .getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
                ?.filter { it.width > 0f && it.height > 0f }
                ?: return null
            if (sizes.isEmpty()) return null
            // The list holds one entry per orientation the host may show the widget in; the
            // wider of them is the landscape one.
            val size = if (landscape) {
                sizes.maxByOrNull { it.width }
            } else {
                sizes.minByOrNull { it.width }
            } ?: return null
            return size.width to size.height
        }

        private fun estimatedSizeDp(options: Bundle, landscape: Boolean): Pair<Float, Float> {
            val width = if (landscape) {
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
            } else {
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            }
            val height = if (landscape) {
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            } else {
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            }
            return width.toFloat() to height.toFloat()
        }
    }
}
