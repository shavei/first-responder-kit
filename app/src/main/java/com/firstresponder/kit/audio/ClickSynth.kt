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
     * A ~2 ms fade-out, for the same reason as [ATTACK_MS] at the other end.
     *
     * The decay alone does not reach silence inside [DURATION_MS]: at the end of the click it
     * is still around a quarter of full scale, so simply stopping there leaves a step of that
     * size in the stream. A step is broadband — it is exactly what the *attack* ramp exists to
     * avoid — and here it lands on every single beat, adding a rasp to a click that is
     * supposed to be a clean tick. Ramping the last couple of milliseconds down to zero costs
     * nothing audible from the click itself, which is long past its transient by then.
     */
    private const val RELEASE_MS = 2

    /**
     * Renders the click.
     *
     * @param sampleRate the stream's sample rate, in Hz.
     */
    fun generate(sampleRate: Int): ShortArray {
        val clickFrames = sampleRate * DURATION_MS / 1_000
        val attackFrames = (sampleRate * ATTACK_MS / 1_000).coerceAtLeast(1)
        val releaseFrames = (sampleRate * RELEASE_MS / 1_000).coerceAtLeast(1)
        val samples = ShortArray(clickFrames)

        val angularStep = 2.0 * PI * TONE_HZ / sampleRate
        for (frame in 0 until clickFrames) {
            val seconds = frame.toDouble() / sampleRate
            val decay = exp(-DECAY_PER_SECOND * seconds)
            val attack = min(1.0, frame.toDouble() / attackFrames)
            // Reaches exactly zero on the last frame, so the click ends on silence whatever
            // the decay has left at that point.
            val release = min(1.0, (clickFrames - 1 - frame).toDouble() / releaseFrames)
            val value = sin(angularStep * frame) * decay * attack * release * PEAK
            samples[frame] = (value * Short.MAX_VALUE).roundToInt().toShort()
        }
        return samples
    }
}
