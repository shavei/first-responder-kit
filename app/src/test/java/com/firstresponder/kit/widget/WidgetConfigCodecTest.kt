package com.firstresponder.kit.widget

import com.firstresponder.kit.domain.PatientType
import com.firstresponder.kit.util.Bpm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stored format is a compatibility surface: a widget placed by an older version of the
 * app has to keep working after an upgrade, and the home screen is no place to find out it
 * does not.
 */
class WidgetConfigCodecTest {

    @Test
    fun `a configuration survives a round trip`() {
        val config = WidgetConfig(
            action = WidgetAction.START_METRONOME,
            patientType = PatientType.INFANT,
            bpm = 118,
            icon = WidgetIcon.PLAY,
            value = WidgetValue.RATIO,
            label = "Baby",
            showLabel = false,
            background = WidgetColor.GREEN,
            foreground = WidgetColor.BLACK,
            backgroundOpacity = 40,
            cornerPercent = 12,
            textScale = 130,
        )

        assertEquals(config, WidgetConfigCodec.decode(WidgetConfigCodec.encode(config)))
    }

    @Test
    fun `an empty store decodes to the defaults`() {
        assertEquals(WidgetConfig(), WidgetConfigCodec.decode(emptyMap()))
    }

    @Test
    fun `unknown names fall back rather than failing`() {
        val decoded = WidgetConfigCodec.decode(
            mapOf(
                "action" to "launch_the_missiles",
                "patient" to "goldfish",
                "icon" to "sparkles",
                "value" to "",
                "background" to "puce",
                "show_label" to "perhaps",
            ),
        )

        assertEquals(WidgetConfig(), decoded)
    }

    @Test
    fun `values outside their range are clamped on the way in`() {
        val decoded = WidgetConfigCodec.decode(
            mapOf(
                "opacity" to "480",
                "corners" to "-10",
                "text_scale" to "5000",
                "bpm" to "3000",
            ),
        )

        assertEquals(100, decoded.backgroundOpacity)
        assertEquals(0, decoded.cornerPercent)
        assertEquals(WidgetConfig.MAX_TEXT_SCALE, decoded.textScale)
        assertEquals(Bpm.MAX, decoded.bpm)
    }

    @Test
    fun `a rate outside the patient's protocol is pulled into it`() {
        // A newborn is beaten at 120 events a minute, whatever the widget was set up with.
        val decoded = WidgetConfigCodec.decode(
            mapOf("patient" to PatientType.NEWBORN.storageName, "bpm" to "100"),
        )

        assertEquals(120, decoded.bpm)
    }

    @Test
    fun `an unset rate and an automatic foreground are absent rather than sentinels`() {
        val encoded = WidgetConfigCodec.encode(WidgetConfig(bpm = null, foreground = null))

        assertTrue("bpm" !in encoded)
        assertTrue("foreground" !in encoded)
        assertNull(WidgetConfigCodec.decode(encoded).bpm)
        assertNull(WidgetConfigCodec.decode(encoded).foreground)
    }

    @Test
    fun `a caption of nothing but spaces is no caption`() {
        val config = WidgetConfig(label = "   ").sanitized()

        assertNull(config.label)
        assertTrue("label" !in WidgetConfigCodec.encode(config))
    }

    @Test
    fun `a caption is truncated rather than stored in full`() {
        val config = WidgetConfig(label = "x".repeat(500)).sanitized()

        assertEquals(WidgetConfig.MAX_LABEL_LENGTH, config.label?.length)
    }
}
