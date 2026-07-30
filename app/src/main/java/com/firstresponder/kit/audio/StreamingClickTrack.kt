package com.firstresponder.kit.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import com.firstresponder.kit.util.Bpm
import kotlin.math.abs

/**
 * The click stream: one continuous [AudioTrack] with the beats written into it at exact
 * frame positions.
 *
 * **Why a stream rather than a click fired per beat.** Retriggering a short sound on every
 * beat asks the platform to start playback afresh 110 times a minute, and a start takes a
 * variable few milliseconds — the mixer has to be woken, the buffer re-primed, the fast path
 * re-entered. That variability lands directly on the beat the user hears, and no amount of
 * accuracy in the scheduler can take it back out. Here the clicks are simply *drawn* into a
 * stream that is already running, at frame offsets computed from [BeatGrid]. Their spacing
 * is then a property of the audio hardware's own clock: exact to a fraction of a frame,
 * immune to garbage collection, thermal throttling and a busy CPU alike. The renderer may be
 * late writing a block and nothing about the tempo changes.
 *
 * **Why it keeps running when the sound is off.** The stream is also the timebase for the
 * vibration and the on-screen pulse: [AudioClock] converts a frame position into the instant
 * it is heard, and the other two cues are scheduled against that. Muting the click therefore
 * writes silence rather than stopping the stream, so vibrate-only practice is timed by the
 * same crystal, and turning the sound back on cannot shift the phase.
 *
 * **Why the alarm stream.** Sonification and notification usages are routed to the system
 * stream, which the platform force-mutes whenever the ringer is silent or on vibrate — so on
 * the phone of anyone who keeps it silent (which is most people, and nearly everyone carrying
 * one on a shift) the metronome came out inaudible with no indication why. The alarm stream
 * is exempt from the ringer mode and from Do Not Disturb's default policy, and it carries its
 * own volume that the hardware keys reach from anywhere in the app; see
 * `MainActivity.volumeControlStream`.
 */
class StreamingClickTrack(context: Context) : ClickTrack {

    private val audioManager: AudioManager? =
        context.applicationContext.getSystemService(AudioManager::class.java)

    /** Guards the track and the session fields against the UI and renderer threads. */
    private val lock = Any()

    private var track: AudioTrack? = null
    private var sampleRate: Int = 0
    private var blockFrames: Int = 0
    private var click: ShortArray = ShortArray(0)

    /** The beat layout of the current session. Read by the renderer and the schedule. */
    @Volatile
    private var grid: BeatGrid? = null

    @Volatile
    private var soundEnabled: Boolean = true

    /** Frames already handed to the track. Everything below this is fixed and cannot move. */
    @Volatile
    private var committedFrames: Long = 0L

    @Volatile
    private var rendering: Boolean = false

    private var renderThread: Thread? = null
    private var clock: AudioClock? = null

    /** Kept after the session ends so the timing report can mention it. */
    private var underruns: Int = 0

    override fun prepare() {
        synchronized(lock) { prepareLocked() }
    }

    private fun prepareLocked() {
        if (track != null) return

        // Matching the native output rate avoids an internal resampler, which is both a
        // prerequisite for the fast audio path and one less source of latency.
        sampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)
            .takeIf { it > 0 } ?: FALLBACK_SAMPLE_RATE
        // Writing in whole device bursts is what the fast path is shaped around; a write of
        // exactly one burst hands the mixer a full period every time it asks for one.
        blockFrames = deviceBurstFrames() ?: (sampleRate / DEFAULT_BLOCKS_PER_SECOND)
        click = ClickSynth.generate(sampleRate)

        val minBufferBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(0)
        // Deep enough that an ordinary scheduling hiccup on the renderer cannot starve the
        // mixer. Buffer depth costs latency, and latency here is measured and compensated for
        // rather than guessed at, so it is the cheap thing to spend and an underrun — which
        // shifts every later frame — is the expensive one.
        val bufferBytes = maxOf(minBufferBytes, blockFrames * BYTES_PER_FRAME * BLOCKS_IN_BUFFER)

        // A handful of devices reject the low-latency performance mode; fall back to a plain
        // track rather than losing the click altogether.
        track = buildTrack(bufferBytes, lowLatency = true)
            ?: buildTrack(bufferBytes, lowLatency = false)
        // The click is synthesised with headroom, so the track itself runs wide open: the only
        // thing that should decide how loud this is is the user's alarm volume.
        track?.setVolume(AudioTrack.getMaxVolume())
    }

    override fun start(bpm: Int, soundEnabled: Boolean): BeatSchedule? = synchronized(lock) {
        if (rendering) return null
        prepareLocked()
        val track = this.track ?: return null

        this.soundEnabled = soundEnabled
        val clock = AudioClock(sampleRate)
        this.clock = clock
        // The first beat sits a short way into the stream. The pipeline needs a moment to
        // start moving before anything can be heard at all, and by placing beat 0 beyond that
        // the platform has reported where the stream really is before the beat is dispatched
        // — so even the first beat's vibration is aligned to the click rather than guessed at.
        grid = BeatGrid.startingAt(sampleRate, bpm, leadInFrames())
        committedFrames = 0L
        underruns = 0

        val started = runCatching {
            // Reset the frame counters left over from the previous session.
            track.stop()
            track.flush()
            // Prime the buffer before playback starts so the mixer's first request is met
            // from a full buffer. Only up to one block short of capacity: a write that could
            // not fit would block until playback began, and this runs on the caller's thread.
            var frame = 0L
            val block = ShortArray(blockFrames)
            val capacityFrames = bufferFrames(track) - blockFrames
            do {
                renderBlock(block, frame)
                val written = track.write(block, 0, block.size)
                if (written <= 0) break
                frame += written
            } while (frame + blockFrames <= capacityFrames)
            committedFrames = frame

            track.play()
            clock.seed(System.nanoTime() + START_LATENCY_ESTIMATE_NANOS)
            true
        }.onFailure { error ->
            Log.w(TAG, "Could not start the click stream", error)
        }.getOrDefault(false)

        if (!started) {
            grid = null
            this.clock = null
            return null
        }

        rendering = true
        // The track and the clock are handed over rather than looked up: the renderer must
        // never need the lock, or stopping — which holds it while joining this thread — would
        // have to wait for the timeout instead of for the thread.
        renderThread = Thread({ render(track, clock) }, "metronome-render").apply {
            isDaemon = true
            start()
        }
        return AudioSchedule(clock)
    }

    override fun setSoundEnabled(enabled: Boolean) {
        soundEnabled = enabled
    }

    override fun stop() {
        synchronized(lock) {
            if (!rendering) return
            rendering = false
            val track = this.track
            // Pausing releases the renderer from a blocking write straight away, so stopping
            // costs the caller a thread join and not a whole buffer of audio.
            runCatching { track?.pause() }
            renderThread?.join(RENDER_JOIN_MILLIS)
            renderThread = null
            runCatching {
                // Read before stopping: the platform resets the counter with the track.
                underruns = track?.underrunCount ?: 0
                track?.stop()
                track?.flush()
            }
            grid = null
        }
    }

    override fun release() {
        stop()
        synchronized(lock) {
            runCatching { track?.release() }
            track = null
            clock = null
        }
    }

    override fun diagnostics(): String {
        val clock = this.clock ?: return "audio clock: not started"
        return if (clock.isLocked) {
            "audio clock: locked, offset trimmed %.1f ms, %d underruns".format(
                java.util.Locale.US,
                clock.totalCorrectionNanos / 1_000_000.0,
                underruns,
            )
        } else {
            "audio clock: never locked (no platform timestamps), $underruns underruns"
        }
    }

    /** The frame beat [index] starts on, or 0 when no session is running. */
    private fun frameOf(index: Long): Long = grid?.frameOf(index) ?: 0L

    /**
     * Moves the grid to [bpm], hinged on the first beat that is still free to move.
     *
     * Audio that has been handed to the track cannot be taken back, so the hinge is pushed
     * past everything committed (plus a margin for the block being rendered right now). The
     * hinge beat keeps the frame it already had, so a rate change costs no short or long gap
     * — it simply takes effect a fraction of a second later than it was asked for.
     */
    private fun rebase(bpm: Int, fromBeat: Long) {
        synchronized(lock) {
            val grid = this.grid ?: return
            if (Bpm.clamp(bpm) == grid.bpm) return
            val freeFrame = committedFrames + blockFrames.toLong() * REBASE_MARGIN_BLOCKS
            val hinge = maxOf(fromBeat, grid.firstBeatAtOrAfter(freeFrame))
            this.grid = grid.withBpm(bpm, hinge)
        }
    }

    private fun render(track: AudioTrack, clock: AudioClock) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val block = ShortArray(blockFrames)
        val timestamp = AudioTimestamp()
        var frame = committedFrames
        var blocksSincePoll = 0

        while (rendering) {
            renderBlock(block, frame)
            // Blocking: the audio device's own consumption is what paces this loop, which is
            // why the renderer needs no timer of its own and cannot run ahead or fall behind.
            val written = runCatching { track.write(block, 0, block.size) }.getOrDefault(-1)
            if (written < 0) {
                Log.w(TAG, "Click stream write failed ($written); stopping the renderer")
                break
            }
            // A short write means playback was paused underneath us; the next block is
            // rendered from where this one actually got to, so the grid stays intact.
            frame += written
            committedFrames = frame

            if (++blocksSincePoll >= BLOCKS_PER_TIMESTAMP_POLL) {
                blocksSincePoll = 0
                // The one thing the platform can tell us that we cannot work out: which frame
                // is coming out of the speaker, and when.
                if (runCatching { track.getTimestamp(timestamp) }.getOrDefault(false)) {
                    // A frame presented moments ago should be timed moments ago. A reading
                    // that is not is a device reporting in some other clock, and taking it
                    // would put the vibration a long way from the click; ignore it and let
                    // the seeded estimate carry the session.
                    if (abs(timestamp.nanoTime - System.nanoTime()) < TIMESTAMP_SANITY_NANOS) {
                        clock.observe(timestamp.framePosition, timestamp.nanoTime)
                    }
                }
            }
        }
    }

    /**
     * Draws one block of the stream: silence, with a click stamped wherever a beat falls.
     *
     * Beats are looked up from the grid rather than counted, so a block is rendered correctly
     * no matter what came before it. The search starts one click-length early because a click
     * that began in an earlier block still has a tail to write into this one.
     */
    private fun renderBlock(block: ShortArray, fromFrame: Long) {
        block.fill(0)
        val grid = this.grid ?: return
        if (!soundEnabled) return

        var beat = grid.firstBeatAtOrAfter(fromFrame - click.size + 1)
        while (true) {
            val offset = grid.frameOf(beat) - fromFrame
            if (offset >= block.size) break
            // A negative offset is the tail of a click that started in an earlier block.
            var source = if (offset < 0L) (-offset).toInt() else 0
            var target = (offset + source).toInt()
            while (source < click.size && target < block.size) {
                block[target++] = click[source++]
            }
            beat++
        }
    }

    /** @return the track, or null if the platform refused to build it. */
    private fun buildTrack(bufferBytes: Int, lowLatency: Boolean): AudioTrack? =
        runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        // Alarm, so a silenced ringer cannot silence the metronome. See the
                        // class comment — this is what makes the click audible at all on a
                        // phone that is not on loud.
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .apply {
                    if (lowLatency) setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                }
                .build()
        }.onFailure { error ->
            // Audio is a nice-to-have: the pulse and the vibration still work without it.
            Log.w(TAG, "Could not create the click AudioTrack (lowLatency=$lowLatency)", error)
        }.getOrNull()

    private fun bufferFrames(track: AudioTrack): Int =
        runCatching { track.bufferSizeInFrames }.getOrDefault(0)
            .coerceAtLeast(blockFrames * 2)

    private fun leadInFrames(): Long = sampleRate.toLong() * LEAD_IN_MILLIS / 1_000L

    /** The device's preferred write size, when it advertises one. */
    private fun deviceBurstFrames(): Int? =
        audioManager
            ?.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
            ?.toIntOrNull()
            ?.takeIf { it in MIN_BLOCK_FRAMES..MAX_BLOCK_FRAMES }

    /** The beats as the rest of the app sees them: an instant per beat, and a tempo control. */
    private inner class AudioSchedule(private val clock: AudioClock) : BeatSchedule {

        override fun beatTimeNanos(index: Long): Long = clock.nanosFor(frameOf(index))

        override fun setBpm(bpm: Int, fromBeat: Long) = rebase(bpm, fromBeat)
    }

    private companion object {
        const val TAG = "ClickTrack"

        /** 16-bit mono. */
        const val BYTES_PER_FRAME = 2

        const val FALLBACK_SAMPLE_RATE = 48_000

        /** Block size when the device advertises no burst size: 10 ms. */
        const val DEFAULT_BLOCKS_PER_SECOND = 100

        const val MIN_BLOCK_FRAMES = 32
        const val MAX_BLOCK_FRAMES = 2_048

        /** Buffer depth in blocks. See [prepareLocked] for why this is not minimal. */
        const val BLOCKS_IN_BUFFER = 6

        /**
         * How far into the stream beat 0 sits (120 ms).
         *
         * Long enough for the stream to be running and the audio clock to have locked before
         * the first beat is dispatched, short enough that Start still feels immediate. It is
         * also the honest floor: nothing can be *heard* sooner than the pipeline can deliver
         * it, and a first beat that arrived before its own click would be worse than one that
         * waits.
         */
        const val LEAD_IN_MILLIS = 120L

        /**
         * Where frame 0 is assumed to be heard, until the platform reports otherwise (40 ms).
         *
         * A placeholder for the first few blocks only: the first real timestamp replaces it
         * outright. See [AudioClock].
         */
        const val START_LATENCY_ESTIMATE_NANOS = 40_000_000L

        /** Poll the platform timestamp every 5 blocks, roughly 20 times a second. */
        const val BLOCKS_PER_TIMESTAMP_POLL = 5

        /** How far from now a timestamp may claim to be before it is treated as nonsense. */
        const val TIMESTAMP_SANITY_NANOS = 2_000_000_000L

        /** Keep a rate change clear of the block being rendered right now. */
        const val REBASE_MARGIN_BLOCKS = 2

        /** Bounded because [stop] is called from the main thread; the pause unblocks it. */
        const val RENDER_JOIN_MILLIS = 200L
    }
}
