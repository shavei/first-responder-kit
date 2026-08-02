package com.firstresponder.kit.widget

import com.firstresponder.kit.domain.PatientType
import com.firstresponder.kit.ui.navigation.Destinations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where each action sends the app. */
class WidgetLaunchTest {

    @Test
    fun `opening the kit needs no route at all`() {
        // The app already starts on the home screen; navigating to it would only add a
        // second copy of it to the back stack.
        assertNull(WidgetLaunch.routeFor(WidgetAction.OPEN_APP, PatientType.ADULT, 0))
    }

    @Test
    fun `each tool has its own destination`() {
        assertEquals(
            Destinations.OXYGEN,
            WidgetLaunch.routeFor(WidgetAction.OPEN_OXYGEN, PatientType.ADULT, 0),
        )
        assertEquals(
            Destinations.SETTINGS,
            WidgetLaunch.routeFor(WidgetAction.OPEN_SETTINGS, PatientType.ADULT, 0),
        )
    }

    @Test
    fun `the metronome opens on the widget's patient`() {
        val route = WidgetLaunch.routeFor(WidgetAction.OPEN_METRONOME, PatientType.CHILD, 0)

        assertTrue(route!!.startsWith("metronome/${PatientType.CHILD.storageName}"))
        assertTrue("only the start action starts it", route.contains("autostart=false"))
    }

    @Test
    fun `the start action asks for compressions to be running on arrival`() {
        val route = WidgetLaunch.routeFor(WidgetAction.START_METRONOME, PatientType.ADULT, 118)!!

        assertTrue(route.contains("autostart=true"))
        assertTrue(route.contains("bpm=118"))
    }

    @Test
    fun `a widget with no rate of its own asks for none`() {
        val route = WidgetLaunch.routeFor(WidgetAction.OPEN_METRONOME, PatientType.ADULT, 0)!!

        // Zero is what the metronome reads as "use the rate saved in Settings".
        assertTrue(route.contains("bpm=0"))
    }
}
