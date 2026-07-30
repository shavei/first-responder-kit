package com.firstresponder.kit.domain

import com.firstresponder.kit.util.Bpm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the values transcribed from the BLS comparison table.
 *
 * These are the numbers a responder acts on, so a typo here is worth more than a compile
 * error — the point of the test is that changing one is deliberate.
 */
class PatientTypeTest {

    @Test
    fun `compression rates match the protocol`() {
        assertEquals(100..120, PatientType.ADULT.rateRange)
        assertEquals(100..120, PatientType.CHILD.rateRange)
        assertEquals(100..120, PatientType.INFANT.rateRange)
        // 120 events a minute — 90 compressions and 30 breaths at 3:1 — and not adjustable.
        assertEquals(120..120, PatientType.NEWBORN.rateRange)
    }

    @Test
    fun `compression ratios match the protocol`() {
        assertEquals("30:2", PatientType.ADULT.singleRescuerRatio)
        assertEquals("30:2", PatientType.ADULT.twoRescuerRatio)
        assertTrue(PatientType.ADULT.hasSingleRatio)

        // A second rescuer halves the ratio for a child and an infant, but not for an adult.
        assertEquals("30:2", PatientType.CHILD.singleRescuerRatio)
        assertEquals("15:2", PatientType.CHILD.twoRescuerRatio)
        assertFalse(PatientType.CHILD.hasSingleRatio)

        assertEquals("30:2", PatientType.INFANT.singleRescuerRatio)
        assertEquals("15:2", PatientType.INFANT.twoRescuerRatio)
        assertFalse(PatientType.INFANT.hasSingleRatio)

        assertEquals("3:1", PatientType.NEWBORN.singleRescuerRatio)
        assertEquals("3:1", PatientType.NEWBORN.twoRescuerRatio)
        assertTrue(PatientType.NEWBORN.hasSingleRatio)
    }

    @Test
    fun `every protocol rate is one the scheduler supports`() {
        PatientType.entries.forEach { patientType ->
            assertTrue(
                "$patientType allows rates outside ${Bpm.RANGE}",
                patientType.rateRange.first >= Bpm.MIN && patientType.rateRange.last <= Bpm.MAX,
            )
        }
    }

    @Test
    fun `clampRate pins a rate to the patient's protocol`() {
        // The saved default is irrelevant to a newborn: the protocol fixes the rate.
        assertEquals(120, PatientType.NEWBORN.clampRate(Bpm.DEFAULT))
        assertEquals(120, PatientType.NEWBORN.clampRate(100))

        assertEquals(Bpm.DEFAULT, PatientType.ADULT.clampRate(Bpm.DEFAULT))
        assertEquals(100, PatientType.ADULT.clampRate(60))
        assertEquals(120, PatientType.ADULT.clampRate(200))
    }

    @Test
    fun `storage names are stable and unique`() {
        assertEquals(
            listOf("newborn", "infant", "child", "adult"),
            PatientType.entries.map { it.storageName },
        )
        PatientType.entries.forEach { patientType ->
            assertEquals(patientType, PatientType.fromStorageName(patientType.storageName))
        }
        assertEquals(PatientType.ADULT, PatientType.fromStorageName(null))
        assertEquals(PatientType.ADULT, PatientType.fromStorageName("toddler"))
    }
}
