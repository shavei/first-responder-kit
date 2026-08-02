package com.firstresponder.kit.widget

import com.firstresponder.kit.domain.PatientType
import com.firstresponder.kit.util.Bpm
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The widget store, fuzzed.
 *
 * Everything a placed widget is lives in `SharedPreferences` as flat strings, and it is read
 * back by a broadcast receiver that has milliseconds to draw a tile and nowhere to report a
 * problem. The store therefore has to survive whatever it is handed: values written by a
 * newer version of the app, a write interrupted half way, a widget id the launcher has
 * recycled from a tile the user deleted.
 *
 * Decoding is documented as never failing. This is that claim, checked against thousands of
 * random configurations and against stores deliberately damaged in every way one can be.
 */
class WidgetConfigCodecStressTest {

    /** Everything a user can build, round-tripped through the store's flat strings. */
    @Test
    fun `a random configuration survives the round trip unchanged`() {
        val random = Random(SEED)

        repeat(FUZZ_ROUNDS) { round ->
            val original = randomConfig(random).sanitized()
            val restored = WidgetConfigCodec.decode(WidgetConfigCodec.encode(original))

            assertEquals("round $round did not survive the store", original, restored)
        }
    }

    /**
     * Every key [WidgetConfigCodec.encode] can produce has to be one the store knows how to
     * delete.
     *
     * A launcher recycles widget ids: the id of a tile the user dragged off the home screen
     * comes back on the next one they place. A key that `delete` does not clear would be
     * inherited by that new tile, which would then quietly do what its predecessor did —
     * open the wrong tool, or start compressions at the wrong rate.
     */
    @Test
    fun `every key a configuration can write is one the store can delete`() {
        val random = Random(SEED)
        val declared = WidgetConfigCodec.keys.toSet()

        assertEquals(
            "the declared key list has duplicates: ${WidgetConfigCodec.keys}",
            WidgetConfigCodec.keys.size,
            declared.size,
        )

        repeat(FUZZ_ROUNDS) {
            val written = WidgetConfigCodec.encode(randomConfig(random)).keys
            val orphans = written - declared
            assertTrue("these keys would survive a delete: $orphans", orphans.isEmpty())
        }
    }

    /**
     * A store damaged every way a store can be: values truncated, replaced with a different
     * type, filled with control characters or emoji, or simply absent.
     */
    @Test
    fun `a corrupt store always decodes to a working widget`() {
        val random = Random(SEED)

        repeat(FUZZ_ROUNDS) { round ->
            val store = WidgetConfigCodec.keys.associateWith { hostileValue(random) }
            val config = WidgetConfigCodec.decode(store)

            assertSane("round $round from $store", config)
        }
    }

    /** A write that was interrupted leaves some keys present and the rest missing. */
    @Test
    fun `a half-written store always decodes to a working widget`() {
        val random = Random(SEED)
        val complete = WidgetConfigCodec.encode(
            WidgetConfig(
                action = WidgetAction.START_METRONOME,
                patientType = PatientType.INFANT,
                bpm = 118,
                label = "בדיקה",
            ),
        )

        repeat(FUZZ_ROUNDS) { round ->
            // Keep a random subset — the state a store is in part way through a write, and
            // also the state a store written by an older version is in for good.
            val partial = complete.filterKeys { random.nextBoolean() }
            val config = WidgetConfigCodec.decode(partial)

            assertSane("round $round from $partial", config)
        }
    }

    /** An empty store is a freshly placed widget: the documented defaults, and nothing else. */
    @Test
    fun `an empty store decodes to the documented defaults`() {
        val config = WidgetConfigCodec.decode(emptyMap<String, String?>())

        assertEquals(WidgetConfig().sanitized(), config)
        assertSane("an empty store", config)
    }

    /**
     * Sanitising has to be idempotent: the store writes sanitised values and reads them back
     * through the same clamp, so a second pass that changed anything would mean a widget
     * whose settings drift a little every time it is redrawn.
     */
    @Test
    fun `sanitising twice changes nothing the first pass did not`() {
        val random = Random(SEED)

        repeat(FUZZ_ROUNDS) { round ->
            val once = randomConfig(random).sanitized()
            assertEquals("round $round drifted on a second pass", once, once.sanitized())
        }
    }

    /**
     * A caption is truncated to a length, and the length is counted in UTF-16 code units
     * while an emoji occupies two of them. Cutting between the halves of one leaves an
     * unpaired surrogate, which is not text any more: the launcher draws it as a hollow box,
     * and it is written straight back into the store on the next save.
     */
    @Test
    fun `truncating a caption never leaves a broken character behind`() {
        val captions = listOf(
            "🚑".repeat(60),
            "a" + "🫀".repeat(60),
            "מבוגר 🚑 ".repeat(20),
            "👨‍👩‍👧‍👦".repeat(20),
        )

        for (caption in captions) {
            val label = requireNotNull(WidgetConfig(label = caption).sanitized().label)

            assertTrue(
                "`$label` was truncated to ${label.length}, past the limit",
                label.length <= WidgetConfig.MAX_LABEL_LENGTH,
            )
            label.forEachIndexed { index, character ->
                if (character.isHighSurrogate()) {
                    assertTrue(
                        "`$label` ends on the first half of a character",
                        index + 1 < label.length && label[index + 1].isLowSurrogate(),
                    )
                }
                if (character.isLowSurrogate()) {
                    assertTrue(
                        "`$label` starts on the second half of a character",
                        index > 0 && label[index - 1].isHighSurrogate(),
                    )
                }
            }
        }
    }

    /**
     * A caption of nothing but whitespace is no caption, not a blank one.
     *
     * Whitespace here means whitespace. Zero-width characters are deliberately *not* treated
     * as blank: the zero-width joiner is what holds a multi-person emoji together, and a
     * caption is one of the places people put those.
     */
    @Test
    fun `a caption of whitespace falls back to the action's own name`() {
        val blanks = listOf("", " ", "\t", "\n", "      ", "\u000B", "\u3000")

        for (blank in blanks) {
            val config = WidgetConfig(label = blank).sanitized()
            assertNull("`$blank` should not have become a caption", config.labelOrNull())
        }
    }

    /** Every configuration a fuzzed store can produce still draws and still opens something. */
    private fun assertSane(context: String, config: WidgetConfig) {
        assertNotNull("$context: no action", config.action)
        assertNotNull("$context: no patient", config.patientType)
        assertNotNull("$context: no icon", config.icon)
        assertNotNull("$context: no value", config.value)
        assertNotNull("$context: no background", config.background)

        config.bpm?.let { bpm ->
            assertTrue("$context: rate $bpm is outside the app's range", bpm in Bpm.RANGE)
            assertTrue(
                "$context: rate $bpm is outside ${config.patientType}'s protocol",
                bpm in config.patientType.rateRange,
            )
        }
        assertTrue(
            "$context: opacity ${config.backgroundOpacity}",
            config.backgroundOpacity in 0..100,
        )
        assertTrue("$context: corners ${config.cornerPercent}", config.cornerPercent in 0..50)
        assertTrue(
            "$context: text scale ${config.textScale}",
            config.textScale in WidgetConfig.MIN_TEXT_SCALE..WidgetConfig.MAX_TEXT_SCALE,
        )
        config.label?.let { label ->
            assertTrue("$context: caption `$label` is blank", label.isNotBlank())
            assertTrue(
                "$context: caption `$label` is ${label.length} long",
                label.length <= WidgetConfig.MAX_LABEL_LENGTH,
            )
        }

        // And the tap still goes somewhere.
        val route = WidgetLaunch.routeFor(config.action, config.patientType, config.bpm ?: 0)
        if (config.action != WidgetAction.OPEN_APP) {
            assertNotNull("$context: the tile would do nothing", route)
        }
    }

    private fun randomConfig(random: Random) = WidgetConfig(
        action = WidgetAction.entries.random(random),
        patientType = PatientType.entries.random(random),
        bpm = if (random.nextBoolean()) null else random.nextInt(400) - 100,
        icon = WidgetIcon.entries.random(random),
        value = WidgetValue.entries.random(random),
        label = if (random.nextBoolean()) null else randomLabel(random),
        showLabel = random.nextBoolean(),
        background = WidgetColor.entries.random(random),
        foreground = if (random.nextBoolean()) null else WidgetColor.entries.random(random),
        backgroundOpacity = random.nextInt(400) - 150,
        cornerPercent = random.nextInt(400) - 150,
        textScale = random.nextInt(800) - 300,
    )

    private fun randomLabel(random: Random): String {
        val alphabet = "abcXYZ 0123אבג🚑\t\n·"
        return buildString {
            repeat(random.nextInt(80)) { append(alphabet[random.nextInt(alphabet.length)]) }
        }
    }

    private fun hostileValue(random: Random): String? = when (random.nextInt(10)) {
        0 -> null
        1 -> ""
        2 -> " "
        3 -> "true"
        4 -> "-2147483649"
        5 -> "2147483648"
        6 -> "NaN"
        7 -> " "
        8 -> "🚑".repeat(random.nextInt(60))
        else -> buildString { repeat(random.nextInt(50)) { append(random.nextInt(10)) } }
    }

    private fun <T> List<T>.random(random: Random): T = this[random.nextInt(size)]

    private companion object {
        /** Fixed, so a failure is reproducible rather than "it went red once". */
        const val SEED = 20_260_802L
        const val FUZZ_ROUNDS = 2_000
    }
}
