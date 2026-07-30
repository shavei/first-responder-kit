package com.firstresponder.kit.audio

import org.junit.Assert.assertEquals
import org.junit.Test

/** The instrument the accuracy claim is read off, so it had better report honestly. */
class TimingMonitorTest {

    @Test
    fun `a perfect session reports no error and the exact period`() {
        val monitor = TimingMonitor()
        val period = 500_000_000L

        repeat(20) { beat -> monitor.record(beat.toLong(), beat * period, beat * period) }

        val report = monitor.snapshot()
        assertEquals(20, report.beats)
        assertEquals(0, report.dropped)
        assertEquals(0.0, report.maxErrorMillis, 0.0)
        assertEquals(0.0, report.rmsErrorMillis, 0.0)
        assertEquals(500.0, report.measuredPeriodMillis, 0.0)
    }

    @Test
    fun `it reports the worst beat, not the average one`() {
        val monitor = TimingMonitor()

        monitor.record(0L, 0L, 1_000_000L) // 1 ms late
        monitor.record(1L, 500_000_000L, 500_000_000L - 7_000_000L) // 7 ms early
        monitor.record(2L, 1_000_000_000L, 1_000_000_000L)

        assertEquals(7.0, monitor.snapshot().maxErrorMillis, 0.0)
    }

    /**
     * The number that catches a slow leak: a metronome half a millisecond fast on every beat
     * looks perfect gap by gap and is a beat and a half out after two minutes.
     */
    @Test
    fun `a persistent drift shows up in the measured period`() {
        val monitor = TimingMonitor()
        val period = 500_000_000L

        repeat(100) { beat ->
            val due = beat * period
            monitor.record(beat.toLong(), due, due - beat * 500_000L)
        }

        assertEquals(499.5, monitor.snapshot().measuredPeriodMillis, 0.01)
    }

    @Test
    fun `dropped beats are counted separately`() {
        val monitor = TimingMonitor()

        monitor.record(0L, 0L, 0L)
        monitor.recordDropped()
        monitor.record(2L, 1_000_000_000L, 1_000_000_000L)

        val report = monitor.snapshot()
        assertEquals(2, report.beats)
        assertEquals(1, report.dropped)
        // Two beats an index apart: the gap is still measured across the missing one.
        assertEquals(500.0, report.measuredPeriodMillis, 0.0)
    }

    @Test
    fun `reset clears a previous session`() {
        val monitor = TimingMonitor()
        monitor.record(0L, 0L, 9_000_000L)
        monitor.recordDropped()

        monitor.reset()

        val report = monitor.snapshot()
        assertEquals(0, report.beats)
        assertEquals(0, report.dropped)
        assertEquals(0.0, report.maxErrorMillis, 0.0)
        assertEquals(0.0, report.measuredPeriodMillis, 0.0)
    }
}
