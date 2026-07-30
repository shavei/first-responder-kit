package com.firstresponder.kit.util

import org.junit.Assert.assertEquals
import org.junit.Test

class VibrationStrengthTest {

    @Test
    fun `clamp keeps values inside the platform amplitude range`() {
        assertEquals(VibrationStrength.MIN, VibrationStrength.clamp(0))
        assertEquals(VibrationStrength.MIN, VibrationStrength.clamp(-40))
        assertEquals(VibrationStrength.MAX, VibrationStrength.clamp(1_000))
        assertEquals(128, VibrationStrength.clamp(128))
    }

    /** 0 would read as "off" even though the weakest setting still pulses. */
    @Test
    fun `the weakest amplitude still displays as one percent`() {
        assertEquals(1, VibrationStrength.percentOf(VibrationStrength.MIN))
    }

    @Test
    fun `the strongest amplitude displays as one hundred percent`() {
        assertEquals(100, VibrationStrength.percentOf(VibrationStrength.MAX))
    }

    @Test
    fun `the midpoint displays as roughly half`() {
        assertEquals(50, VibrationStrength.percentOf(VibrationStrength.MAX / 2))
    }

    @Test
    fun `the default is the strongest pulse the device can produce`() {
        assertEquals(VibrationStrength.MAX, VibrationStrength.DEFAULT)
    }
}
