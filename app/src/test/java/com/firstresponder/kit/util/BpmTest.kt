package com.firstresponder.kit.util

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class BpmTest {

    @Test
    fun `clamp keeps values inside the supported range`() {
        assertEquals(Bpm.MIN, Bpm.clamp(80))
        assertEquals(Bpm.MAX, Bpm.clamp(200))
        assertEquals(110, Bpm.clamp(110))
    }

    @Test
    fun `period matches the rate`() {
        assertEquals(TimeUnit.MINUTES.toNanos(1) / 120, Bpm.periodNanos(120))
        assertEquals(500L, Bpm.periodMillis(120))
    }

    @Test
    fun `period of an out-of-range rate is clamped, never zero or negative`() {
        assertEquals(Bpm.periodNanos(Bpm.MAX), Bpm.periodNanos(999))
        assertEquals(Bpm.periodNanos(Bpm.MIN), Bpm.periodNanos(0))
    }
}
