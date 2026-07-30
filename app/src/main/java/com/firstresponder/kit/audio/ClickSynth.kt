package com.firstresponder.kit.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Generates the metronome click as raw 16-bit PCM.
 *
 * Synthesising the click instead of shipping an audio asset keeps the APK tiny, avoids
 * any decoding on the audio path, and lets the sample rate match the device's native
 * output rate — which is what allows the fast, low-latency audio path to be used.
 *
 * The click is a short high tone with an exponential decay: percussive enough to be
 * unambiguous at arm's length, short enough that it can never overlap the next beat.
 */
object ClickSynth {

    /** A high, cutting tone that survives phone speakers and background noise. */
    private const val TONE_HZ = 2_000.0

    /** Total click length. Far shorter than the 500 ms beat period at 120 BPM. */
    private const val DURATION_MS = 22

    /** Exponential decay rate (per second). Higher = drier, more percussive. */
    private const val DECAY_PER_SECOND = 60.0

    /** Peak amplitude, leaving headroom so the click never clips. */
    private const val PEAK = 0.85

    /** A ~1 ms fade-in removes the DC step that would otherwise pop on the speaker. */
    private const val ATTACK_MS = 1

    /**
     * Renders the click.
     *
     * @param sampleRate the stream's sample rate, in Hz.
     */
    fun generate(sampleRate: Int): ShortArray {
        val clickFrames = sampleRate * DURATION_MS / 1_000
        val attackFrames = (sampleRate * ATTACK_MS / 1_000).coerceAtLeast(1)
        val samples = ShortArray(clickFrames)

        val angularStep = 2.0 * PI * TONE_HZ / sampleRate
        for (frame in 0 until clickFrames) {
            val seconds = frame.toDouble() / sampleRate
            val decay = exp(-DECAY_PER_SECOND * seconds)
            val attack = min(1.0, frame.toDouble() / attackFrames)
            val value = sin(angularStep * frame) * decay * attack * PEAK
            samples[frame] = (value * Short.MAX_VALUE).roundToInt().toShort()
        }
        return samples
    }
}
