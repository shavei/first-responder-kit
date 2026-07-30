package com.firstresponder.kit.audio

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Translates a position in the click stream into the wall-clock instant it is *heard*.
 *
 * This is the piece that lets the vibration and the animation be synchronised with the
 * click rather than merely fired at the same moment. Writing a click into the stream does
 * not make a sound: it enters a pipeline — the app's buffer, the mixer, the HAL, and on
 * Bluetooth a codec and a radio link — and comes out of the speaker somewhere between 20 ms
 * and a quarter of a second later, by an amount that is different on every device, changes
 * when the audio route changes, and cannot be guessed. A vibration fired when the click is
 * *written* is therefore early by that whole latency; at 110 BPM a tenth of a second is a
 * fifth of a beat, which is felt as two separate cues.
 *
 * The platform will however tell us the answer. `AudioTrack.getTimestamp` reports a
 * (frame, nanoTime) pair for audio that has actually been presented at the output, in the
 * same monotonic clock as [System.nanoTime]. One such pair fixes the offset of the whole
 * stream, and the frame rate does the rest:
 *
 *     heardAt(frame) = originNanos + frame × nanosPerFrame
 *
 * Two properties this has to have, both of which come down to how [observe] treats a new
 * reading:
 *
 *  - **It has to keep up.** The output's real frame rate is not exactly the nominal one — a
 *    crystal is off by tens of parts per million, a Bluetooth sink by more — and an underrun
 *    pushes every later frame back. Both show up as the offset slowly walking away, and both
 *    are absorbed by continuously re-fitting it. Nothing extrapolates further than the
 *    fraction of a second between a beat being scheduled and being played, so the nominal
 *    rate is ample for the arithmetic itself.
 *  - **It must not lurch.** Timestamps are quantised and occasionally simply wrong. Applying
 *    a correction in full would move the next vibration by that error, which is heard as one
 *    rushed beat; instead the offset is walked towards each new reading at a small fraction
 *    of real time — far faster than any real drift, far slower than a beat. The first real
 *    reading is exempt, since it replaces a guess made before any beat was scheduled.
 *
 * Written by the renderer thread and read by the timing thread. The published mapping is a
 * single volatile field, so a reader either sees a correction or does not; it can never see
 * half of one.
 */
class AudioClock(sampleRate: Int) {

    private val nanosPerFrame: Double = NANOS_PER_SECOND / sampleRate.toDouble()

    /** The [System.nanoTime] at which frame 0 is (or would have been) heard. */
    @Volatile
    private var originNanos: Double = 0.0

    @Volatile
    private var observations: Int = 0

    /** When the offset was last corrected, so the correction budget can be worked out. */
    private var lastCorrectionNanos: Long = 0L

    /** True once the platform has told us where the stream really is. */
    val isLocked: Boolean get() = observations > 0

    /**
     * How far the mapping has been corrected since it was seeded, in nanoseconds.
     *
     * A diagnostic: a few milliseconds over a session is the expected drift of a real output,
     * while tens of milliseconds means something moved — an underrun, or a route change.
     */
    @Volatile
    var totalCorrectionNanos: Long = 0L
        private set

    /**
     * Sets the starting guess: the instant frame 0 is expected to be heard.
     *
     * Used for the fraction of a second before the first real timestamp arrives, and for the
     * whole session on the rare device whose timestamps never become valid.
     */
    fun seed(originNanos: Long) {
        this.originNanos = originNanos.toDouble()
        observations = 0
        totalCorrectionNanos = 0L
    }

    /**
     * Feeds in a platform timestamp: [framePosition] was presented at [presentedAtNanos].
     *
     * Called from the renderer thread only.
     */
    fun observe(framePosition: Long, presentedAtNanos: Long) {
        if (framePosition < 0L) return
        val target = presentedAtNanos - framePosition * nanosPerFrame

        if (observations == 0) {
            // The seed was a guess about hardware we had not measured yet, and nothing has
            // been scheduled off it this early, so there is no spacing to protect.
            originNanos = target
            lastCorrectionNanos = presentedAtNanos
            observations = 1
            return
        }

        val elapsedNanos = (presentedAtNanos - lastCorrectionNanos).coerceAtLeast(0L)
        val budget = elapsedNanos * MAX_CORRECTION_FRACTION
        val correction = (target - originNanos).coerceIn(-budget, budget)
        originNanos += correction
        totalCorrectionNanos += abs(correction).roundToLong()
        lastCorrectionNanos = presentedAtNanos
        observations++
    }

    /** The [System.nanoTime] instant at which [frame] reaches the listener. */
    fun nanosFor(frame: Long): Long = (originNanos + frame * nanosPerFrame).roundToLong()

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000.0

        /**
         * Ceiling on how fast the offset may be corrected: 1% of elapsed time.
         *
         * Two orders of magnitude faster than the drift of any real output, so tracking is
         * never the limit; and slow enough that a bad reading shifts the next vibration by a
         * fraction of a millisecond rather than by the size of the error. A genuine step —
         * the tens of milliseconds an underrun costs — is made good within a few seconds.
         */
        const val MAX_CORRECTION_FRACTION = 0.01
    }
}
