package com.firstresponder.kit.widget

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The sizes one particular widget draws itself at, worked out from the space it has.
 *
 * A size of zero means the element is not drawn at all. The renderer maps this straight
 * onto `RemoteViews` calls; nothing here knows about Android.
 */
data class WidgetLayout(
    val paddingDp: Int,
    /** Side of the square the glyph is fitted into. */
    val iconDp: Int,
    /** Text size of the value line. */
    val valueTextDp: Int,
    /** Text size of the caption. */
    val labelTextDp: Int,
) {
    val showIcon: Boolean get() = iconDp > 0
    val showValue: Boolean get() = valueTextDp > 0
    val showLabel: Boolean get() = labelTextDp > 0
}

/**
 * Fits the icon, the value and the caption into whatever the user has stretched the widget
 * to.
 *
 * A widget here can be anything from a single 40 dp cell to a whole screen, and the same
 * three elements have to look deliberate at both ends. Two rules do the work:
 *
 *  - **Everything is a share of the shorter side.** A tile twice as big gets an icon twice
 *    as big, so a 1×1 and a 4×4 are recognisably the same widget rather than one being the
 *    other with air around it.
 *  - **What cannot be read is not drawn.** Elements are dropped in reverse priority —
 *    caption, then value — until the rest are above the size at which they stop being
 *    legible. That is what lets a 1×1 be an icon and nothing else without the user having
 *    to turn the caption off themselves.
 *
 * Text is then narrowed to the width as well as the height, so a caption the user typed
 * four words into shrinks onto its line instead of wrapping across the tile.
 *
 * The user's text-size setting scales the two text elements inside that fit; it can enlarge
 * them right up to the point where they no longer share the tile, and past it the whole
 * stack shrinks together rather than the caption silently vanishing.
 */
object WidgetLayouts {

    /** Below these the element is a smudge rather than information. */
    private const val MIN_ICON_DP = 12f
    private const val MIN_VALUE_DP = 9f
    private const val MIN_LABEL_DP = 7f

    /** The glyphs are 96 dp drawables; scaling one up past that would only blur it. */
    private const val MAX_ICON_DP = 96f

    /**
     * Text has no such ceiling of its own — a rate on a widget stretched across the screen
     * should be readable across a room — so these are loose bounds against a stretch that
     * would make the tile absurd rather than large.
     */
    private const val MAX_VALUE_DP = 96f
    private const val MAX_LABEL_DP = 48f

    /** A line of text occupies rather more vertical space than its type size. */
    private const val VALUE_LINE_FACTOR = 1.25f
    private const val LABEL_LINE_FACTOR = 1.30f

    /** Breathing room between two stacked elements. */
    private const val GAP_DP = 2f

    /**
     * @param hasIcon whether a glyph was asked for.
     * @param value,label the text to be drawn, or null for none. The strings themselves
     *   rather than flags: how wide "30:2" is at a given type size is the other half of
     *   whether it fits, and a caption of one word and one of five want different sizes.
     * @param textScalePercent the user's text-size setting, as a percentage.
     */
    fun forSize(
        widthDp: Float,
        heightDp: Float,
        hasIcon: Boolean,
        value: String? = null,
        label: String? = null,
        textScalePercent: Int = 100,
    ): WidgetLayout {
        val shortSide = min(widthDp, heightDp).coerceAtLeast(1f)
        val padding = (shortSide * PADDING_SHARE).coerceIn(MIN_PADDING_DP, MAX_PADDING_DP)
        val innerWidth = (widthDp - 2 * padding).coerceAtLeast(1f)
        val innerHeight = (heightDp - 2 * padding).coerceAtLeast(1f)
        val textScale = textScalePercent
            .coerceIn(WidgetConfig.MIN_TEXT_SCALE, WidgetConfig.MAX_TEXT_SCALE) / 100f

        val wanted = buildList {
            if (hasIcon) add(Element.ICON)
            if (value != null) add(Element.VALUE)
            if (label != null) add(Element.LABEL)
        }
        val empty = WidgetLayout(padding.roundToInt(), 0, 0, 0)
        if (wanted.isEmpty()) return empty

        // Widest set first, dropping the lowest-priority element each time round.
        for (size in wanted.size downTo 1) {
            val candidate = wanted.take(size)
            fit(
                elements = candidate,
                innerWidth = innerWidth,
                innerHeight = innerHeight,
                textScale = textScale,
                padding = padding.roundToInt(),
                valueLength = value?.length ?: 0,
                labelLength = label?.length ?: 0,
            )?.let { return it }
        }

        // Nothing fits legibly — draw the most important element at its minimum and let the
        // tile clip it. An oversized glyph is still a target the user can hit.
        return when (wanted.first()) {
            Element.ICON -> empty.copy(iconDp = MIN_ICON_DP.roundToInt())
            Element.VALUE -> empty.copy(valueTextDp = MIN_VALUE_DP.roundToInt())
            Element.LABEL -> empty.copy(labelTextDp = MIN_LABEL_DP.roundToInt())
        }
    }

    /** Sizes [elements] to fill the space, or returns null if they cannot be read at that size. */
    private fun fit(
        elements: List<Element>,
        innerWidth: Float,
        innerHeight: Float,
        textScale: Float,
        padding: Int,
        valueLength: Int,
        labelLength: Int,
    ): WidgetLayout? {
        val shares = Shares.of(elements)

        // The icon is bounded by the narrow dimension too: a wide, short widget has to fit
        // its glyph between the sides, not just between top and bottom.
        var icon = if (Element.ICON in elements) {
            min(shares.icon * innerHeight, innerWidth * ICON_WIDTH_SHARE)
        } else {
            0f
        }
        var value = if (Element.VALUE in elements) shares.value * innerHeight * textScale else 0f
        var label = if (Element.LABEL in elements) shares.label * innerHeight * textScale else 0f

        // Height is only half of fitting. "30:2" and "🧑" want different type sizes in the
        // same box, and a caption of one word wants a bigger one than a caption of four.
        // The value is fitted across outright — it is one short line that may not wrap, and
        // an ellipsised rate is no rate at all. The caption is allowed to give up part way
        // and let the ellipsis finish the job.
        value = value.narrowedTo(innerWidth, valueLength, VALUE_CHAR_SHARE, floor = 0f)
        label = label.narrowedTo(
            innerWidth,
            labelLength,
            LABEL_CHAR_SHARE,
            floor = MOST_A_LINE_MAY_SHRINK,
        )

        val required = icon +
            value * VALUE_LINE_FACTOR +
            label * LABEL_LINE_FACTOR +
            GAP_DP * (elements.size - 1)
        if (required > innerHeight) {
            // Shrink the whole stack rather than dropping an element the user asked for —
            // that only happens below when something falls under its legible minimum.
            val shrink = innerHeight / required
            icon *= shrink
            value *= shrink
            label *= shrink
        }

        if (Element.ICON in elements && icon < MIN_ICON_DP) return null
        if (Element.VALUE in elements && value < MIN_VALUE_DP) return null
        if (Element.LABEL in elements && label < MIN_LABEL_DP) return null

        return WidgetLayout(
            paddingDp = padding,
            iconDp = icon.coerceAtMost(MAX_ICON_DP).roundToInt(),
            valueTextDp = value.coerceAtMost(MAX_VALUE_DP).roundToInt(),
            labelTextDp = label.coerceAtMost(MAX_LABEL_DP).roundToInt(),
        )
    }

    private const val PADDING_SHARE = 0.08f
    private const val MIN_PADDING_DP = 2f
    private const val MAX_PADDING_DP = 10f
    private const val ICON_WIDTH_SHARE = 0.92f

    /** Roughly how wide one character is, as a share of the type size. Bold text is wider. */
    private const val VALUE_CHAR_SHARE = 0.62f
    private const val LABEL_CHAR_SHARE = 0.56f

    /**
     * How far a caption may be shrunk to get itself onto one row before it stops being
     * worth it. Past this it wraps and ellipsises instead, which is the better of two bad
     * options for a caption longer than the tile it was typed into.
     */
    private const val MOST_A_LINE_MAY_SHRINK = 0.55f

    /**
     * This type size, or the largest that fits [length] characters across, whichever is
     * smaller — but never below [floor] of the size asked for.
     */
    private fun Float.narrowedTo(
        innerWidth: Float,
        length: Int,
        charShare: Float,
        floor: Float,
    ): Float {
        if (length <= 0) return this
        val fitsAcross = innerWidth / (charShare * length)
        return min(this, max(fitsAcross, this * floor))
    }

    /** Priority order: the caption is the first thing to go, the glyph the last. */
    private enum class Element { ICON, VALUE, LABEL }

    /**
     * How the height is divided, per combination of elements.
     *
     * Hand-tuned rather than derived: three elements want a smaller icon than two do, and a
     * value on its own wants to be the tile rather than sit politely in the middle of it.
     */
    private data class Shares(val icon: Float, val value: Float, val label: Float) {
        companion object {
            fun of(elements: List<Element>): Shares {
                val icon = Element.ICON in elements
                val value = Element.VALUE in elements
                val label = Element.LABEL in elements
                return when {
                    icon && value && label -> Shares(0.36f, 0.26f, 0.16f)
                    icon && value -> Shares(0.48f, 0.32f, 0f)
                    icon && label -> Shares(0.56f, 0f, 0.22f)
                    value && label -> Shares(0f, 0.44f, 0.22f)
                    icon -> Shares(0.92f, 0f, 0f)
                    value -> Shares(0f, 0.70f, 0f)
                    else -> Shares(0f, 0f, 0.44f)
                }
            }
        }
    }
}
