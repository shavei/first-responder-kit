package com.firstresponder.kit.audio

/**
 * Plays the metronome click.
 *
 * Kept as an interface so the timing engine has no dependency on the Android audio stack
 * (and can be unit tested), and so an alternative implementation can be dropped in later.
 */
interface ClickPlayer {

    /**
     * Allocates and warms up the audio resources.
     *
     * Safe to call more than once. Call it before the first beat is due — doing this work
     * lazily inside [click] would make the first beat noticeably late.
     */
    fun prepare()

    /**
     * Plays one click.
     *
     * Called on the engine's audio output thread, never on the thread that times the beats,
     * so it may block for the usual few milliseconds a platform call takes. It should still
     * return well inside one beat: an implementation that overruns will have beats dropped.
     */
    fun click()

    /** Releases the audio resources. The player can be [prepare]d again afterwards. */
    fun release()
}
