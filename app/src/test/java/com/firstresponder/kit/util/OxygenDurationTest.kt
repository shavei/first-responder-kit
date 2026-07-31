package com.firstresponder.kit.util

import com.firstresponder.kit.domain.OxygenCylinder
import org.junit.Assert.assertEquals
import org.junit.Test

class OxygenDurationTest {

    @Test
    fun `applies the formula from the BLS sheet`() {
        // 100 atm in a 20 L cylinder is 2000 L of oxygen; at 10 L/min that is 200 minutes.
        assertEquals(
            200,
            OxygenDuration.remainingMinutes(
                pressureAtmospheres = 100,
                capacityLitres = 20.0,
                flowLitresPerMinute = 10,
            ),
        )
    }

    @Test
    fun `rounds down so the answer is never optimistic`() {
        // 150 × 2.4 / 15 = 24.0 exactly; one atmosphere less lands mid-minute.
        assertEquals(
            23,
            OxygenDuration.remainingMinutes(
                pressureAtmospheres = 149,
                capacityLitres = 2.4,
                flowLitresPerMinute = 15,
            ),
        )
    }

    @Test
    fun `a full portable cylinder at maximum flow lasts 24 minutes`() {
        assertEquals(
            24,
            OxygenDuration.remainingMinutes(
                pressureAtmospheres = 150,
                capacityLitres = OxygenCylinder.D.capacityLitres,
                flowLitresPerMinute = 15,
            ),
        )
    }

    @Test
    fun `the large cylinder holds proportionally more`() {
        assertEquals(
            200,
            OxygenDuration.remainingMinutes(
                pressureAtmospheres = 150,
                capacityLitres = OxygenCylinder.H.capacityLitres,
                flowLitresPerMinute = 15,
            ),
        )
    }

    @Test
    fun `an empty or unflowing cylinder reports no time rather than dividing by zero`() {
        assertEquals(0, OxygenDuration.remainingMinutes(0, 20.0, 15))
        assertEquals(0, OxygenDuration.remainingMinutes(150, 20.0, 0))
        assertEquals(0, OxygenDuration.remainingMinutes(150, 20.0, -5))
    }

    @Test
    fun `inputs are held inside the ranges the controls offer`() {
        assertEquals(OxygenDuration.PRESSURE_RANGE.last, OxygenDuration.clampPressure(500))
        assertEquals(OxygenDuration.PRESSURE_RANGE.first, OxygenDuration.clampPressure(-10))
        assertEquals(OxygenDuration.FLOW_RANGE.last, OxygenDuration.clampFlow(100))
        assertEquals(OxygenDuration.FLOW_RANGE.first, OxygenDuration.clampFlow(0))
    }
}
