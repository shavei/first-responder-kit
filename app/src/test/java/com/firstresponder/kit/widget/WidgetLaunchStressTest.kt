package com.firstresponder.kit.widget

import com.firstresponder.kit.domain.PatientType
import com.firstresponder.kit.ui.navigation.Destinations
import com.firstresponder.kit.util.Bpm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every tap a home-screen widget can produce, taken all the way to a route.
 *
 * A widget is the one part of this app that is used without looking at it first — the phone
 * comes out of a pocket, the tile is tapped, and whatever happens next has to be the right
 * thing on the first try. There is no error to show on a home screen and nobody to read it,
 * so the failure mode of a bad route is an app that opens on the wrong screen, or does not
 * open at all, in the middle of a resuscitation.
 *
 * What is checked here is the whole chain that can be checked on a JVM: every configuration
 * a user can build produces a route, that route is one the navigation graph actually
 * declares, and the arguments parse back out of it as the values that went in. The Android
 * pieces either side — `Intent` extras and `PendingIntent` identity — need a device.
 */
class WidgetLaunchStressTest {

    /**
     * Every action, patient and rate a widget can be configured with, exhaustively.
     *
     * The combination count is small enough to enumerate outright, which is exactly why it
     * should be: there is no configuration a user can reach that this does not cover.
     */
    @Test
    fun `every configuration a user can build produces a usable route`() {
        forEveryConfiguration { action, patientType, bpm ->
            val route = WidgetLaunch.routeFor(action, patientType, bpm)

            if (action == WidgetAction.OPEN_APP) {
                // The app already starts on the home screen; navigating there would only
                // stack a second copy of it.
                assertNull("$action asked for a route it does not need", route)
                return@forEveryConfiguration
            }

            assertNotNull("$action / $patientType / $bpm produced no route", route)
            requireNotNull(route)
            assertTrue(
                "$action / $patientType / $bpm produced a blank route",
                route.isNotBlank(),
            )
            assertTrue(
                "route `$route` is not one the navigation graph declares",
                route.matchesADeclaredDestination(),
            )
        }
    }

    /**
     * The metronome route's arguments have to survive the round trip into the route string
     * and back out of it — that string is the only thing that reaches the view model.
     */
    @Test
    fun `the metronome route carries its patient, rate and autostart back out intact`() {
        forEveryConfiguration { action, patientType, bpm ->
            if (!action.usesPatient) return@forEveryConfiguration
            val route = requireNotNull(WidgetLaunch.routeFor(action, patientType, bpm))
            val parsed = MetronomeRouteArgs.parse(route)

            assertEquals("route `$route` lost the patient", patientType, parsed.patientType)
            assertEquals("route `$route` lost the rate", bpm, parsed.bpm)
            assertEquals(
                "route `$route` lost whether it should already be beating",
                action == WidgetAction.START_METRONOME,
                parsed.autoStart,
            )
        }
    }

    /**
     * A rate stored by a corrupted or hand-edited store must not reach the navigation graph
     * as something that cannot be parsed back as an integer — the argument is typed, and an
     * unparseable one is a crash on arrival rather than a wrong rate.
     */
    @Test
    fun `hostile rates still produce a route with a parseable rate`() {
        val hostile = listOf(Int.MIN_VALUE, -1, 0, 1, Bpm.MIN - 1, Bpm.MAX + 1, Int.MAX_VALUE)

        for (bpm in hostile) {
            for (patientType in PatientType.entries) {
                val route = requireNotNull(
                    WidgetLaunch.routeFor(WidgetAction.START_METRONOME, patientType, bpm),
                )
                val parsed = MetronomeRouteArgs.parse(route)
                assertEquals("route `$route` mangled the rate", bpm, parsed.bpm)
                assertEquals(patientType, parsed.patientType)
            }
        }
    }

    /**
     * The rate a widget hands over is the *sanitised* one, so whatever a corrupted store
     * holds, what reaches the metronome is a rate that patient's protocol allows.
     */
    @Test
    fun `a widget's stored rate is brought into the patient's protocol before it is used`() {
        val hostile = listOf(Int.MIN_VALUE, -1, 0, 1, 60, 200, Int.MAX_VALUE)

        for (bpm in hostile) {
            for (patientType in PatientType.entries) {
                val config = WidgetConfig(
                    action = WidgetAction.START_METRONOME,
                    patientType = patientType,
                    bpm = bpm,
                ).sanitized()

                val rate = requireNotNull(config.bpm) { "a stored rate should stay a stored rate" }
                assertTrue(
                    "$patientType would have been started at $rate",
                    rate in patientType.rateRange,
                )
                assertTrue("$patientType would have been started at $rate", rate in Bpm.RANGE)
            }
        }
    }

    /** A widget with no rate of its own must keep meaning "whatever Settings says". */
    @Test
    fun `a widget that follows Settings asks for no rate of its own`() {
        val config = WidgetConfig(action = WidgetAction.START_METRONOME, bpm = null).sanitized()
        assertNull("following Settings should stay following Settings", config.bpm)

        val route = requireNotNull(
            WidgetLaunch.routeFor(config.action, config.patientType, config.bpm ?: 0),
        )
        // Zero is what the metronome reads as "use the rate saved in Settings".
        assertEquals(0, MetronomeRouteArgs.parse(route).bpm)
    }

    // -- The stored identifiers a placed widget depends on --------------------------------------

    /**
     * Every storage name is stable, distinct and parses back to itself.
     *
     * A widget the user placed a year ago is still on their home screen and is still read
     * with these names. Renaming one silently turns that widget into the fallback — a "start
     * compressions" tile that quietly becomes "open the metronome" is a tap that no longer
     * does what its owner set it up to do.
     */
    @Test
    fun `every stored identifier round-trips and none collide`() {
        assertRoundTrips(
            WidgetAction.entries.map { it.storageName },
            WidgetAction.entries,
            WidgetAction::fromStorageName,
        )
        assertRoundTrips(
            WidgetIcon.entries.map { it.storageName },
            WidgetIcon.entries,
            WidgetIcon::fromStorageName,
        )
        assertRoundTrips(
            WidgetValue.entries.map { it.storageName },
            WidgetValue.entries,
            WidgetValue::fromStorageName,
        )
        assertRoundTrips(
            WidgetColor.entries.map { it.storageName },
            WidgetColor.entries,
            WidgetColor::fromStorageName,
        )
        assertRoundTrips(
            PatientType.entries.map { it.storageName },
            PatientType.entries,
            PatientType::fromStorageName,
        )
    }

    /**
     * Anything unrecognised has to land on a working widget, not on nothing.
     *
     * These are the values a store written by a newer version, a half-finished write, or a
     * hand edit can produce. Every one of them has to give a tile that still opens something.
     */
    @Test
    fun `unrecognised identifiers fall back rather than failing`() {
        val hostile = listOf(null, "", " ", "OPEN_APP", "open app", "openapp", "\u0000", "🚑")

        for (name in hostile) {
            assertEquals(
                "action `$name` should fall back to the metronome",
                WidgetAction.OPEN_METRONOME,
                WidgetAction.fromStorageName(name),
            )
            assertEquals(WidgetIcon.AUTO, WidgetIcon.fromStorageName(name))
            assertEquals(WidgetValue.NONE, WidgetValue.fromStorageName(name))
            assertEquals(WidgetColor.DARK, WidgetColor.fromStorageName(name))
            assertEquals(PatientType.ADULT, PatientType.fromStorageName(name))

            // And the fallback is a tile that still goes somewhere sensible.
            val action = WidgetAction.fromStorageName(name)
            val route = WidgetLaunch.routeFor(action, PatientType.fromStorageName(name), 0)
            assertNotNull("the fallback action produced no route", route)
            assertTrue(requireNotNull(route).matchesADeclaredDestination())
        }
    }

    /** Every action resolves to a glyph, so no tile can come out blank. */
    @Test
    fun `every action has a glyph to draw`() {
        for (action in WidgetAction.entries) {
            assertNotNull(
                "$action has no default glyph, so an automatic icon would draw nothing",
                action.defaultIcon.drawableRes,
            )
            assertNotNull("$action resolves AUTO to nothing", WidgetIcon.AUTO.resolve(action))
            // NONE is the one that is meant to draw nothing.
            assertNull(WidgetIcon.NONE.resolve(action))
        }
    }

    /** Only the metronome actions care who the patient is; the others must ignore it. */
    @Test
    fun `the patient only changes what the metronome actions do`() {
        for (action in WidgetAction.entries) {
            val routes = PatientType.entries.map { WidgetLaunch.routeFor(action, it, 0) }.distinct()
            if (action.usesPatient) {
                assertEquals(
                    "$action should open on the widget's patient",
                    PatientType.entries.size,
                    routes.size,
                )
            } else {
                assertEquals("$action should ignore the patient, got $routes", 1, routes.size)
            }
        }
    }

    private fun forEveryConfiguration(body: (WidgetAction, PatientType, Int) -> Unit) {
        for (action in WidgetAction.entries) {
            for (patientType in PatientType.entries) {
                for (bpm in RATES) {
                    body(action, patientType, bpm)
                }
            }
        }
    }

    private fun <T> assertRoundTrips(names: List<String>, entries: List<T>, parse: (String) -> T) {
        assertEquals("two entries share a storage name: $names", names.size, names.distinct().size)
        names.forEach { name ->
            assertTrue("`$name` is not a usable storage name", name.isNotBlank())
        }
        entries.forEachIndexed { index, entry ->
            assertEquals("`${names[index]}` did not parse back", entry, parse(names[index]))
        }
    }

    /** True when [this] is a route the navigation graph declares a destination for. */
    private fun String.matchesADeclaredDestination(): Boolean = when {
        this == Destinations.OXYGEN || this == Destinations.SETTINGS || this == Destinations.HOME ->
            true
        else -> MetronomeRouteArgs.matches(this)
    }

    private companion object {
        /** Zero — "follow Settings" — plus every rate the app supports. */
        val RATES = listOf(0) + Bpm.RANGE.toList()
    }
}

/**
 * The metronome route's arguments, read back out of the route string.
 *
 * The navigation library does this parsing on a device; here it is done against the very
 * pattern the graph declares, so a route the widget builds and a route the graph expects
 * cannot drift apart without a test noticing.
 */
internal data class MetronomeRouteArgs(
    val patientType: PatientType,
    val bpm: Int,
    val autoStart: Boolean,
) {
    companion object {
        /**
         * Built from [Destinations.METRONOME_ROUTE] itself rather than written out again, so
         * that changing the pattern without changing the builder fails here.
         */
        private val PATTERN = Regex(
            // `Regex.escape` wraps the pattern in \Q…\E, so each placeholder is stepped out
            // of the quoted run and back into it around its capture group.
            "^" + Regex.escape(Destinations.METRONOME_ROUTE)
                .replace("{${Destinations.ARG_PATIENT_TYPE}}", "\\E([^/?&=]+)\\Q")
                .replace("{${Destinations.ARG_BPM}}", "\\E(-?\\d+)\\Q")
                .replace("{${Destinations.ARG_AUTO_START}}", "\\E(true|false)\\Q") + "$",
        )

        fun matches(route: String): Boolean = PATTERN.matches(route)

        fun parse(route: String): MetronomeRouteArgs {
            val match = requireNotNull(PATTERN.find(route)) {
                "route `$route` does not match the graph's `${Destinations.METRONOME_ROUTE}`"
            }
            val (patient, bpm, autoStart) = match.destructured
            return MetronomeRouteArgs(
                patientType = PatientType.fromStorageName(patient),
                bpm = bpm.toInt(),
                autoStart = autoStart.toBooleanStrict(),
            )
        }
    }
}
