package com.firstresponder.kit.audio

import com.firstresponder.kit.util.Bpm

/**
 * Where every beat sits in the click stream, counted in audio frames.
 *
 * A frame is one sample period of the output device — the unit the audio hardware itself
 * counts in. Placing the beats on that ruler rather than on a wall clock is what makes the
 * tempo exact: the frames leave the DAC at a rate set by a crystal, so two clicks written
 * 26,182 frames apart *are* 545.45 ms apart, whatever the CPU was doing in between.
 *
 * Two properties matter here and both are tested:
 *
 *  - **No accumulated rounding.** A beat is not "the previous beat plus a period"; it is
 *    `round(beat × sampleRate × 60 / bpm)` evaluated from the anchor in exact integer
 *    arithmetic. At 110 BPM and 48 kHz a period is 26,181.81… frames, so any per-beat
 *    rounding would push the grid a fifth of a millisecond further out every beat — a
 *    second of error in an hour and a half. Rounding the *product* instead keeps every beat
 *    within half a frame (about 10 µs) of its ideal position, for ever.
 *  - **Tempo changes keep the phase.** [withBpm] re-anchors on a beat that has not been
 *    rendered yet and keeps that beat's frame exactly where it already was, so changing the
 *    rate never produces one short or one long gap at the seam.
 */
class BeatGrid private constructor(
    val sampleRate: Int,
    val bpm: Int,
    private val anchorBeat: Long,
    private val anchorFrame: Long,
    /**
     * The grid this one took over from, which still answers for the beats before [anchorBeat].
     *
     * Without it, a rate change would retroactively move every earlier beat — the new tempo
     * extrapolated backwards from the hinge. Those beats have already been written into the
     * stream, and the renderer looks back a click's length on every block to pick up a tail
     * that spilled over a boundary; it has to be told where that click really went, not where
     * it would have gone at the new rate.
     */
    private val previous: BeatGrid?,
) {

    /** The frame at which beat [beat] starts. Exact for any beat, before or after the anchor. */
    fun frameOf(beat: Long): Long =
        if (previous != null && beat < anchorBeat) {
            previous.frameOf(beat)
        } else {
            anchorFrame +
                roundedDiv((beat - anchorBeat) * sampleRate * SECONDS_PER_MINUTE, bpm.toLong())
        }

    /**
     * The first beat that starts at or after [frame].
     *
     * The estimate is exact except where [frameOf]'s rounding lands on the boundary, so it
     * is nudged by at most one beat either way afterwards.
     */
    fun firstBeatAtOrAfter(frame: Long): Long {
        if (previous != null && frame <= anchorFrame) {
            // Anything before the hinge belongs to the segment that was in force then.
            return minOf(previous.firstBeatAtOrAfter(frame), anchorBeat)
        }
        val framesPerMinute = sampleRate.toLong() * SECONDS_PER_MINUTE
        var beat = anchorBeat + ceilDiv((frame - anchorFrame) * bpm, framesPerMinute)
        while (frameOf(beat) < frame) beat++
        while (frameOf(beat - 1) >= frame) beat--
        return beat
    }

    /**
     * The same grid at a new tempo, hinged on [fromBeat].
     *
     * [fromBeat] must be a beat that has not been rendered yet: everything before it keeps
     * the spacing it was given, everything from it on gets the new one, and the hinge beat
     * itself does not move — so the change costs no short or long gap.
     */
    fun withBpm(bpm: Int, fromBeat: Long): BeatGrid = BeatGrid(
        sampleRate = sampleRate,
        bpm = Bpm.clamp(bpm),
        anchorBeat = fromBeat,
        anchorFrame = frameOf(fromBeat),
        previous = this,
    )

    companion object {
        private const val SECONDS_PER_MINUTE = 60L

        /** A grid at [bpm] whose beat 0 sits at [firstBeatFrame]. */
        fun startingAt(sampleRate: Int, bpm: Int, firstBeatFrame: Long): BeatGrid = BeatGrid(
            sampleRate = sampleRate,
            bpm = Bpm.clamp(bpm),
            anchorBeat = 0L,
            anchorFrame = firstBeatFrame,
            previous = null,
        )
    }
}

/**
 * `numerator / denominator`, rounded to the nearest integer (halves away from negative).
 *
 * Integer division truncates towards zero, which would bias every beat of the grid in the
 * same direction. [Math.floorDiv] is used rather than plain division so the result is
 * continuous across zero — the grid is asked about beats before its anchor whenever the
 * renderer looks back for a click that spills over a block boundary.
 */
internal fun roundedDiv(numerator: Long, denominator: Long): Long =
    Math.floorDiv(2 * numerator + denominator, 2 * denominator)

/** `numerator / denominator`, rounded up, for negative numerators too. */
private fun ceilDiv(numerator: Long, denominator: Long): Long =
    -Math.floorDiv(-numerator, denominator)
