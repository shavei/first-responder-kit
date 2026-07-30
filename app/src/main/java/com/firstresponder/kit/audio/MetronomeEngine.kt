package com.firstresponder.kit.audio

import android.os.Process
import android.util.Log
import com.firstresponder.kit.util.Bpm
import com.firstresponder.kit.util.HapticPlayer
import com.firstresponder.kit.util.VibrationStrength
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.LockSupport
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the engine should do on each beat. Immutable, swapped atomically while running. */
data class MetronomeConfig(
    val bpm: Int = Bpm.DEFAULT,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val vibrationAmplitude: Int = VibrationStrength.DEFAULT,
)

/**
 * The beat scheduler: one grid, three cues, all landing together.
 *
 * A metronome for compressions has to get two different things right, and they are not the
 * same problem.
 *
 * **The tempo has to be exact.** That is [BeatSchedule]'s job. While the click stream is
 * running the schedule is locked to the audio hardware's clock — the beats are positions in
 * a stream, so their spacing is a property of a crystal rather than of thread scheduling, and
 * a busy CPU cannot change it. Without audio it falls back to [SystemClockSchedule], which
 * derives every beat from the start of the session rather than from the beat before it, so
 * wake-up jitter stays bounded instead of accumulating.
 *
 * **The three cues have to coincide.** This is what the engine itself is for. A click, a
 * vibration and a frame on screen are not delivered by the same machinery and do not take the
 * same time to arrive: audio crosses a buffered pipeline, a vibration is a binder call plus a
 * motor that needs milliseconds to reach full excursion, and a Compose animation cannot
 * appear before the next display frame. Fired at the same instant they arrive at three
 * different ones, and at 110 BPM — 545 ms a beat — a tenth of that is plainly perceptible as
 * a smeared double cue. So each output declares its own delivery lag and is dispatched that
 * much *early*, against the one instant the schedule says the beat belongs to.
 *
 * The mechanics that keep this honest:
 *
 *  - **Dedicated threads.** The beat is timed on its own `URGENT_AUDIO` thread, and each
 *    output waits and fires on another, so a busy main thread cannot delay a beat and one
 *    slow platform call cannot delay the other cue. Nothing on these threads allocates.
 *  - **Monotonic clock.** [System.nanoTime] is immune to wall-clock and timezone changes.
 *  - **Park, then spin.** Sleeping is accurate to a few milliseconds at best, so an output
 *    thread parks until shortly before its moment and then yields in a tight loop for the
 *    last stretch. That buys sub-millisecond accuracy for a negligible amount of CPU.
 *  - **Never crowded.** A cue that could not go out on time is dropped rather than played on
 *    top of the next one. Missing a single cue is a smaller lie about the pace than two cues
 *    in quick succession, and it keeps every gap the user feels at least a full period.
 *  - **Measured.** Every dispatch is compared against the instant it was meant for, and the
 *    session's error and delivered tempo are logged on stop. See [TimingMonitor].
 *
 * The engine owns no Android UI or context objects; it talks to the outside world through
 * [ClickTrack], [HapticPlayer] and the [beats] flow.
 */
class MetronomeEngine(
    private val clickTrack: ClickTrack,
    private val hapticPlayer: HapticPlayer,
) {

    /** Emits the index of each beat as it fires. Used only to drive the pulse animation. */
    private val _beats = MutableSharedFlow<Long>(
        replay = 0,
        extraBufferCapacity = 1,
        // The UI missing a beat is fine; blocking a timing thread never is.
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val beats: SharedFlow<Long> = _beats.asSharedFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    /** Read fresh on every beat, so config changes apply from the next beat onwards. */
    @Volatile
    private var config: MetronomeConfig = MetronomeConfig()

    /** Set to false to ask the timing thread to finish. */
    @Volatile
    private var running: Boolean = false

    private var thread: Thread? = null

    /** The grid the current session's beats sit on. Null when stopped. */
    @Volatile
    private var schedule: BeatSchedule? = null

    /**
     * The first beat the timing thread has not dispatched yet — the earliest one a tempo
     * change is allowed to move.
     */
    @Volatile
    private var nextBeat: Long = 0L

    /**
     * The output threads, alive only while the engine is running.
     *
     * Held as fields so the timing loop never has to build or look anything up on a beat.
     */
    private var hapticOutput: BeatOutput? = null
    private var pulseOutput: BeatOutput? = null

    /** Handed to [hapticOutput] with the beat; see [BeatOutput] for why this is safe. */
    @Volatile
    private var pulseAmplitude: Int = VibrationStrength.DEFAULT

    private val hapticTiming = TimingMonitor()
    private val pulseTiming = TimingMonitor()

    /** How the last session actually performed. Null until one has finished. */
    @Volatile
    var lastHapticTiming: TimingReport? = null
        private set

    /**
     * Allocates the audio resources ahead of time.
     *
     * Blocking (a few milliseconds), so call it off the main thread — typically once when
     * the metronome screen opens, so that pressing Start is prompt.
     */
    fun prepare() = clickTrack.prepare()

    /** Starts beating. No-op if already running. */
    @Synchronized
    fun start(config: MetronomeConfig) {
        this.config = config
        if (running) return

        running = true
        _isRunning.value = true
        nextBeat = 0L
        hapticTiming.reset()
        pulseTiming.reset()

        // The click stream is the timebase whenever it can be opened — including when the
        // sound is muted, since the vibration and the animation are timed against it too.
        schedule = clickTrack.start(config.bpm, config.soundEnabled)
            ?: SystemClockSchedule(
                startNanos = System.nanoTime() + FALLBACK_LEAD_IN_NANOS,
                bpm = config.bpm,
            )

        // Both output threads are up and parked before the first beat is due, so beat 0
        // costs no more than any other beat.
        hapticOutput = BeatOutput(
            name = "metronome-haptic",
            leadNanos = hapticPlayer.startLatencyNanos,
            monitor = hapticTiming,
        ) { hapticPlayer.pulse(pulseAmplitude) }
        pulseOutput = BeatOutput(
            name = "metronome-pulse",
            leadNanos = PULSE_LEAD_NANOS,
            monitor = pulseTiming,
        ) { index -> _beats.tryEmit(index) }

        thread = Thread(::runBeatLoop, "metronome-timer").apply {
            isDaemon = true
            start()
        }
    }

    /** Stops beating. No-op if already stopped. */
    @Synchronized
    fun stop() {
        if (!running) return

        running = false
        _isRunning.value = false
        thread?.let { thread ->
            // Wake it out of its park so it can observe `running` and exit promptly.
            LockSupport.unpark(thread)
            thread.join(THREAD_JOIN_TIMEOUT_MILLIS)
        }
        thread = null
        hapticOutput?.shutdown()
        pulseOutput?.shutdown()
        hapticOutput = null
        pulseOutput = null
        clickTrack.stop()
        schedule = null

        reportTiming()
    }

    /**
     * Applies new settings.
     *
     * A rate change hinges on the first beat that has not been dispatched yet, so the beats
     * already in flight keep the spacing they were promised and the change costs no short or
     * long gap. Muting the sound leaves the stream — and therefore the timebase — running.
     */
    fun updateConfig(config: MetronomeConfig) {
        val previous = this.config
        this.config = config
        if (!running) return
        if (config.bpm != previous.bpm) schedule?.setBpm(config.bpm, nextBeat)
        if (config.soundEnabled != previous.soundEnabled) {
            clickTrack.setSoundEnabled(config.soundEnabled)
        }
    }

    /** Stops the engine and frees the audio resources. */
    @Synchronized
    fun release() {
        stop()
        clickTrack.release()
    }

    private fun runBeatLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val schedule = this.schedule ?: return

        var beat = 0L
        while (running) {
            nextBeat = beat
            // Hand the beat to the outputs before it is due: each of them then waits out its
            // own remaining lead precisely. The head start is generous compared with the
            // largest lead, so ordinary jitter on this thread never eats into it.
            val dispatchAt = parkUntilDispatch(schedule, beat)
            if (dispatchAt == STOPPED) break

            val config = this.config
            val dueAt = schedule.beatTimeNanos(beat)
            // Publish the amplitude before waking the haptic thread, so it reads the value
            // belonging to this beat.
            pulseAmplitude = config.vibrationAmplitude
            if (config.vibrationEnabled) hapticOutput?.fire(beat, dueAt)
            pulseOutput?.fire(beat, dueAt)

            // Only a stall long enough to eat the whole head start can misplace a beat, and
            // only a schedule that owns its own clock can do anything about it.
            schedule.noteDispatch(beat, System.nanoTime() - dispatchAt)
            beat++
        }
    }

    /**
     * Parks until beat [beat] is ready to be handed to the outputs.
     *
     * The target is re-read on every pass rather than captured once: while the audio clock is
     * still settling onto the platform's first timestamps the instant a beat belongs to can
     * move by a few milliseconds, and a waiter that had already committed to the old value
     * would deliver the first beat of the session against a stale one.
     *
     * @return the instant it was waiting for, so the caller can tell how late it woke, or
     *   [STOPPED] if the engine was stopped while waiting.
     */
    private fun parkUntilDispatch(schedule: BeatSchedule, beat: Long): Long {
        while (running) {
            val target = schedule.beatTimeNanos(beat) - DISPATCH_LEAD_NANOS
            val remaining = target - System.nanoTime()
            if (remaining <= 0L) return target
            LockSupport.parkNanos(remaining)
        }
        return STOPPED
    }

    /**
     * Logs how the session went.
     *
     * Deliberately not silent: this is the only way to see, on a particular phone, that the
     * cues went out when they were meant to and that the delivered tempo was the requested
     * one. Runs on the caller of [stop] after both output threads have been joined.
     */
    private fun reportTiming() {
        val haptic = hapticTiming.snapshot()
        lastHapticTiming = haptic
        if (haptic.beats == 0 && pulseTiming.snapshot().beats == 0) return
        Log.i(
            TAG,
            "${haptic.describe("vibration")} | " +
                "${pulseTiming.snapshot().describe("pulse")} | ${clickTrack.diagnostics()}",
        )
    }

    private companion object {
        const val TAG = "Metronome"

        /** [parkUntilDispatch]'s "the engine was stopped" answer; no beat can fall here. */
        const val STOPPED = Long.MIN_VALUE

        /**
         * How far ahead of a beat the outputs are woken (40 ms).
         *
         * Comfortably more than the largest delivery lag any output declares, so every output
         * still has time to wait out its own lead precisely, and enough slack that a few
         * milliseconds of jitter on the timing thread never turns into a late cue.
         */
        const val DISPATCH_LEAD_NANOS = 40_000_000L

        /**
         * How early the animation is triggered (16 ms).
         *
         * A frame emitted now is composed and shown on the next display refresh, so the pulse
         * has to be asked for one frame before the beat if it is to be *seen* on the beat.
         */
        const val PULSE_LEAD_NANOS = 16_000_000L

        /**
         * Where beat 0 sits when there is no audio stream to hang it on (25 ms).
         *
         * Only has to cover getting the output threads up and parked.
         */
        const val FALLBACK_LEAD_IN_NANOS = 25_000_000L

        const val THREAD_JOIN_TIMEOUT_MILLIS = 250L
    }
}

/**
 * One cue — a vibration, a frame on screen — fired on its own thread, one beat at a time.
 *
 * [fire] is called from the timing thread and must never block it: it sets a flag and
 * unparks, which is a handful of microseconds and, more importantly, takes the *same* handful
 * of microseconds every beat. The waiting and the blocking platform call then happen over
 * here, where being a few milliseconds slow on one beat cannot push the next beat around.
 *
 * Each output has its own [leadNanos]: the time between asking for the cue and the user
 * receiving it. The thread is handed the instant the *beat* falls on and fires that much
 * earlier, which is what puts a vibration and a click on the skin and in the ear together
 * rather than a pipeline apart.
 *
 * A beat is dropped rather than played out of position: a cue picked up more than
 * [STALE_BEAT_NANOS] after its moment is thrown away instead of landing on top of the next
 * one. At CPR rates — half a second between beats, against a platform call measured in
 * milliseconds — that should never happen in the first place.
 */
private class BeatOutput(
    name: String,
    private val leadNanos: Long,
    private val monitor: TimingMonitor,
    private val play: (Long) -> Unit,
) {

    /** True between [fire] and the thread picking the beat up. */
    private val pending = AtomicBoolean(false)

    /** The pending beat, written before [pending] is set and read once it is. */
    @Volatile
    private var beatIndex = 0L

    @Volatile
    private var dueAtNanos = 0L

    @Volatile
    private var running = true

    private val thread = Thread(::loop, name).apply {
        isDaemon = true
        start()
    }

    /**
     * Asks for one cue. Safe to call from the timing thread.
     *
     * @param dueAtNanos the instant the beat itself falls on; this thread subtracts its own
     *   lead from it.
     */
    fun fire(index: Long, dueAtNanos: Long) {
        this.beatIndex = index
        this.dueAtNanos = dueAtNanos
        if (pending.compareAndSet(false, true)) LockSupport.unpark(thread)
    }

    /**
     * Stops the thread, waiting briefly for a beat already in flight.
     *
     * The wait is what makes Stop mean stop: without it a beat dispatched microseconds before
     * could still reach the motor after the engine reports itself stopped.
     */
    fun shutdown() {
        running = false
        LockSupport.unpark(thread)
        thread.join(SHUTDOWN_JOIN_MILLIS)
    }

    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        while (running) {
            if (pending.compareAndSet(true, false)) {
                // Read both before waiting: the next beat may be handed over while this one
                // is still on its way, and it must not be able to move this one.
                val index = beatIndex
                val fireAtNanos = dueAtNanos - leadNanos
                if (!parkUntil(fireAtNanos)) continue
                val now = System.nanoTime()
                if (now - fireAtNanos <= STALE_BEAT_NANOS) {
                    play(index)
                    monitor.record(index, fireAtNanos, now)
                } else {
                    monitor.recordDropped()
                }
            } else {
                // A `fire` that lands between the check and the park leaves an unpark permit
                // behind, so this returns immediately and the loop picks the beat up — no beat
                // can be lost in the gap. Spurious wake-ups are harmless for the same reason:
                // the flag, not the wake-up, is what says there is work.
                LockSupport.park()
            }
        }
    }

    /**
     * Parks until [targetNanos], then yields until the moment is actually reached.
     *
     * @return false if the output was shut down while waiting.
     */
    private fun parkUntil(targetNanos: Long): Boolean {
        while (running) {
            val remaining = targetNanos - System.nanoTime()
            if (remaining <= 0L) return true
            if (remaining > SPIN_THRESHOLD_NANOS) {
                LockSupport.parkNanos(remaining - SPIN_THRESHOLD_NANOS)
            } else {
                // Final approach: too short to sleep through accurately.
                Thread.yield()
            }
        }
        return false
    }

    private companion object {
        /**
         * Switch from parking to yielding this long before the cue (5 ms).
         *
         * Wide enough to absorb a park that overshoots — the kernel routinely returns a couple
         * of milliseconds late, and anything it overshoots by lands straight on the beat. At
         * one beat every half second the spin costs about 1% of one core.
         */
        const val SPIN_THRESHOLD_NANOS = 5_000_000L

        /**
         * A cue picked up more than this long after its moment is dropped (50 ms).
         *
         * Roughly where a misplaced cue stops being a slightly late one and starts being felt
         * as part of the next beat.
         */
        const val STALE_BEAT_NANOS = 50_000_000L

        const val SHUTDOWN_JOIN_MILLIS = 100L
    }
}
