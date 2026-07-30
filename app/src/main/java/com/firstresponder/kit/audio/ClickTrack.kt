package com.firstresponder.kit.audio

/**
 * The audio side of the metronome: it renders the beat grid, and it is the clock everything
 * else is timed against.
 *
 * Kept as an interface so the engine has no dependency on the Android audio stack and its
 * timing can be tested on a plain JVM.
 */
interface ClickTrack {

    /**
     * Allocates and warms up the audio resources.
     *
     * Safe to call more than once. Blocking (a few milliseconds), so call it off the main
     * thread — typically when the metronome screen opens, so that pressing Start is prompt.
     */
    fun prepare()

    /**
     * Starts the stream and lays out the beats on it.
     *
     * @param soundEnabled whether the clicks are audible. The stream runs either way: it is
     *   the timebase for the vibration and the animation as well, so silencing the click
     *   must not silence the clock.
     * @return the schedule the beats now sit on, or null if no audio could be opened at all
     *   — the caller then falls back to a [SystemClockSchedule] and there is no click.
     */
    fun start(bpm: Int, soundEnabled: Boolean): BeatSchedule?

    /** Mutes or unmutes the click without disturbing the grid. */
    fun setSoundEnabled(enabled: Boolean)

    /** Stops the stream. The track stays allocated, ready for the next [start]. */
    fun stop()

    /** Releases the audio resources. The track can be [prepare]d again afterwards. */
    fun release()

    /** A one-line summary of how the audio clock behaved, for the timing report. */
    fun diagnostics(): String = ""
}
