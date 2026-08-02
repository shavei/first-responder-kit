package com.firstresponder.kit.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.firstresponder.kit.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws a configured widget into a [RemoteViews].
 *
 * One layout serves every size and every combination of settings: what changes is which of
 * the three elements are visible, how big they are ([WidgetLayouts]), and what colour they
 * are. That is deliberate — a `RemoteViews` can only do what the host process is willing to
 * do on its behalf, and the set of calls that work identically from API 26 to today is
 * small. Sizes and colours that cannot be set through it are baked into two small bitmaps
 * instead: the rounded background, and the tinted glyph. Both are capped well under the
 * size at which the update would stop fitting through Binder.
 *
 * The widget is rendered for the size it is now and re-rendered when that changes, rather
 * than shipping a set of alternatives for the host to choose between. Resizing already
 * calls back into the provider; the alternative buys a smoother stretch mid-drag at the
 * cost of several bitmaps per update and a code path that only exists above API 31.
 *
 * [context] must already be in the language the widget should be written in — see
 * [KitWidget.localized].
 */
object WidgetRenderer {

    /** Beyond this a glyph is bigger than any sane widget; the cap bounds the update size. */
    private const val MAX_ICON_PX = 256

    /** The background is a flat rounded rectangle, so it stretches without much loss. */
    private const val MAX_BACKGROUND_PX = 160

    /** What to assume when the host has not told us how big the widget is yet. */
    private const val FALLBACK_SIZE_DP = 60f

    /**
     * @param defaultBpm the rate from Settings, used by widgets whose own rate is unset.
     */
    fun render(
        context: Context,
        appWidgetId: Int,
        config: WidgetConfig,
        widthDp: Float,
        heightDp: Float,
        defaultBpm: Int,
    ): RemoteViews {
        val tile = config.sanitized()
        val width = widthDp.takeIf { it > 0f } ?: FALLBACK_SIZE_DP
        val height = heightDp.takeIf { it > 0f } ?: FALLBACK_SIZE_DP

        val iconRes = tile.icon.resolve(tile.action)
        val value = valueText(tile, defaultBpm)
        val label = if (tile.showLabel) {
            tile.labelOrNull() ?: context.getString(tile.action.shortLabelRes)
        } else {
            null
        }

        val layout = WidgetLayouts.forSize(
            widthDp = width,
            heightDp = height,
            hasIcon = iconRes != null,
            value = value,
            label = label,
            textScalePercent = tile.textScale,
        )
        val foreground = tile.foreground?.argb
            ?: WidgetColors.autoForeground(tile.background, tile.backgroundOpacity)

        val views = RemoteViews(context.packageName, R.layout.widget_kit)
        views.drawBackground(context, tile, width, height)
        val padding = layout.paddingDp.dp(context)
        views.setViewPadding(R.id.widget_content, padding, padding, padding, padding)
        views.drawIcon(context, iconRes, layout, foreground)
        views.drawText(R.id.widget_value, value, layout.valueTextDp, layout.showValue, foreground)
        views.drawText(R.id.widget_label, label, layout.labelTextDp, layout.showLabel, foreground)

        views.setContentDescription(R.id.widget_root, describe(context, tile))
        // The configuration screen renders the same views for its preview, where there is
        // no widget to tap and every slider movement would otherwise mint a PendingIntent.
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            views.setOnClickPendingIntent(
                R.id.widget_root,
                WidgetLaunch.pendingIntent(context, appWidgetId, tile),
            )
        }
        return views
    }

    /** The number, emoji or ratio the tile shows, or null when it shows none. */
    private fun valueText(config: WidgetConfig, defaultBpm: Int): String? = when (config.value) {
        WidgetValue.NONE -> null
        WidgetValue.RATE -> config.patientType.clampRate(config.bpm ?: defaultBpm).toString()
        WidgetValue.PATIENT -> config.patientType.emoji
        WidgetValue.RATIO -> config.patientType.singleRescuerRatio
    }

    /**
     * What a screen reader announces.
     *
     * Built from the action rather than from what is drawn: a tile showing nothing but a
     * glyph still has to say what tapping it will do, and the caption — which the user may
     * have replaced with anything at all — is not that.
     */
    private fun describe(context: Context, config: WidgetConfig): String {
        val action = context.getString(config.action.labelRes)
        if (!config.action.usesPatient) return action
        return context.getString(
            R.string.widget_description_patient,
            action,
            context.getString(config.patientType.labelRes),
        )
    }

    private fun RemoteViews.drawBackground(
        context: Context,
        config: WidgetConfig,
        widthDp: Float,
        heightDp: Float,
    ) {
        if (config.backgroundOpacity == 0) {
            // Fully transparent: no bitmap to send at all, just the wallpaper showing through.
            setViewVisibility(R.id.widget_background, View.GONE)
            return
        }
        setViewVisibility(R.id.widget_background, View.VISIBLE)
        setImageViewBitmap(
            R.id.widget_background,
            backgroundBitmap(
                context = context,
                widthDp = widthDp,
                heightDp = heightDp,
                color = WidgetColors.withOpacity(config.background.argb, config.backgroundOpacity),
                cornerPercent = config.cornerPercent,
            ),
        )
    }

    private fun RemoteViews.drawIcon(
        context: Context,
        @DrawableRes iconRes: Int?,
        layout: WidgetLayout,
        tint: Int,
    ) {
        val bitmap = if (iconRes != null && layout.showIcon) {
            iconBitmap(context, iconRes, layout.iconDp, tint)
        } else {
            null
        }
        if (bitmap == null) {
            setViewVisibility(R.id.widget_icon, View.GONE)
            return
        }
        setViewVisibility(R.id.widget_icon, View.VISIBLE)
        setImageViewBitmap(R.id.widget_icon, bitmap)
    }

    private fun RemoteViews.drawText(
        viewId: Int,
        text: String?,
        sizeDp: Int,
        visible: Boolean,
        color: Int,
    ) {
        if (text == null || !visible) {
            setViewVisibility(viewId, View.GONE)
            return
        }
        setViewVisibility(viewId, View.VISIBLE)
        setTextViewText(viewId, text)
        setTextColor(viewId, color)
        // Density-independent pixels rather than scaled ones: the size already comes from
        // the size of the tile, and a system font scale applied on top of that would push
        // the caption out of a 1×1. The text-size setting on the configuration screen is
        // the deliberate way to make it bigger.
        setTextViewTextSize(viewId, TypedValue.COMPLEX_UNIT_DIP, sizeDp.toFloat())
    }

    /** A flat rounded rectangle at the widget's aspect ratio, so the corners stay round. */
    private fun backgroundBitmap(
        context: Context,
        widthDp: Float,
        heightDp: Float,
        color: Int,
        cornerPercent: Int,
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val scale = min(1f, MAX_BACKGROUND_PX / (max(widthDp, heightDp) * density))
        val width = (widthDp * density * scale).roundToInt().coerceAtLeast(1)
        val height = (heightDp * density * scale).roundToInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val radius = min(width, height) * cornerPercent / 100f
        Canvas(bitmap).drawRoundRect(
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            radius,
            radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color },
        )
        return bitmap
    }

    /** The glyph, tinted and rasterised at the size the layout asked for. */
    private fun iconBitmap(
        context: Context,
        @DrawableRes iconRes: Int,
        sizeDp: Int,
        tint: Int,
    ): Bitmap? {
        val drawable = ContextCompat.getDrawable(context, iconRes)?.mutate() ?: return null
        val density = context.resources.displayMetrics.density
        val requested = (sizeDp * density).roundToInt().coerceAtLeast(1)
        val size = min(requested, MAX_ICON_PX)

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawable.setTint(tint)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(Canvas(bitmap))
        // The image view sizes itself from the bitmap's own density, so a glyph that hit
        // the cap above is declared as a lower-density one and drawn at the size asked for.
        bitmap.density = (size * DENSITY_BASE / sizeDp.coerceAtLeast(1)).roundToInt()
        return bitmap
    }

    /** Pixels per density-independent pixel at the baseline density. */
    private const val DENSITY_BASE = 160f

    /** Density-independent pixels to pixels, for the calls that take only pixels. */
    private fun Int.dp(context: Context): Int =
        (this * context.resources.displayMetrics.density).roundToInt()
}
