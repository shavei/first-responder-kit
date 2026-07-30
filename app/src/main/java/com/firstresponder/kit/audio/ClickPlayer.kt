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

    /** Plays one click. Must be cheap enough to call from the timing thread. */
    fun click()

    /** Releases the audio resources. The player can be [prepare]d again afterwards. */
    fun release()
}
