package com.firstresponder.kit.widget

import androidx.annotation.StringRes
import com.firstresponder.kit.R

/**
 * The colours a widget can be painted in.
 *
 * The first four are the app's own palette, so a widget matches the screen it opens; the
 * rest are neutrals for a tile that should disappear into the wallpaper. Colours are held
 * as plain ARGB ints rather than Compose `Color`s because everything downstream of here is
 * a `RemoteViews` call, which speaks ints.
 *
 * [storageName] is the stable persisted identifier and must not change.
 */
enum class WidgetColor(
    val storageName: String,
    @param:StringRes val labelRes: Int,
    val argb: Int,
) {
    /** The app's primary — emergency red. */
    RED("red", R.string.widget_color_red, 0xFFFF5A5F.toInt()),

    /** The app's "go" green. */
    GREEN("green", R.string.widget_color_green, 0xFF3DDC84.toInt()),

    BLUE("blue", R.string.widget_color_blue, 0xFF4FA8FF.toInt()),
    AMBER("amber", R.string.widget_color_amber, 0xFFFFB020.toInt()),

    /** The app's dark surface. */
    DARK("dark", R.string.widget_color_dark, 0xFF181C1D.toInt()),

    /** The app's light surface. */
    LIGHT("light", R.string.widget_color_light, 0xFFF1F4F4.toInt()),

    BLACK("black", R.string.widget_color_black, 0xFF000000.toInt()),
    WHITE("white", R.string.widget_color_white, 0xFFFFFFFF.toInt()),
    ;

    companion object {
        /** Parses a [storageName], falling back to [DARK]. */
        fun fromStorageName(name: String?): WidgetColor =
            entries.firstOrNull { it.storageName == name } ?: DARK
    }
}

/**
 * Colour arithmetic for the widget: opacity, and picking a foreground that can be read.
 *
 * Pure integer maths, deliberately free of `android.graphics.Color`, so the contrast rule
 * below is covered by ordinary unit tests rather than only ever exercised on a device.
 */
object WidgetColors {

    /** Near-white, the app's `onSurface` in the dark scheme. */
    const val INK_LIGHT: Int = 0xFFECEFEF.toInt()

    /** Near-black, the app's dark background. */
    const val INK_DARK: Int = 0xFF101314.toInt()

    /**
     * Below this opacity the wallpaper is what is actually behind the glyph, so the
     * background colour stops being evidence of anything and light ink is the safer guess.
     */
    private const val WALLPAPER_SHOWS_THROUGH_BELOW = 50

    /** Luminance above which a colour counts as light. Halfway up the perceptual scale. */
    private const val LIGHT_ABOVE = 0.45

    /** [argb] with its alpha replaced by [opacityPercent] of full. */
    fun withOpacity(argb: Int, opacityPercent: Int): Int {
        val alpha = (opacityPercent.coerceIn(0, 100) * 255 / 100)
        return (argb and 0x00FFFFFF) or (alpha shl 24)
    }

    /**
     * The ink to draw on [background] when the user has not picked one.
     *
     * A mostly transparent tile is treated as a dark one: launchers sit widgets on
     * wallpapers of every shade, and white glyphs are the convention there.
     */
    fun autoForeground(background: WidgetColor, opacityPercent: Int): Int =
        if (opacityPercent < WALLPAPER_SHOWS_THROUGH_BELOW) {
            INK_LIGHT
        } else {
            if (relativeLuminance(background.argb) > LIGHT_ABOVE) INK_DARK else INK_LIGHT
        }

    /** Perceived brightness of an opaque colour, 0 (black) to 1 (white). */
    fun relativeLuminance(argb: Int): Double {
        val red = ((argb shr 16) and 0xFF) / 255.0
        val green = ((argb shr 8) and 0xFF) / 255.0
        val blue = (argb and 0xFF) / 255.0
        // Rec. 709 weights: the eye reads green as far brighter than blue at equal value.
        return 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue)
    }

    /** Undoes the sRGB transfer function, so the weights above apply to light, not to bytes. */
    private fun linear(channel: Double): Double =
        if (channel <= 0.03928) channel / 12.92 else Math.pow((channel + 0.055) / 1.055, 2.4)
}
