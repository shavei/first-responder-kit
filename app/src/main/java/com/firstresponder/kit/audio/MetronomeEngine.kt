package com.firstresponder.kit.audio

import android.os.Process
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
 * The drift-free beat scheduler.
 *
 * Design notes:
 *
 *  - **No drift.** Each beat time is derived by adding one period to the *ideal* time of
 *    the previous beat, never to the time the thread actually woke up. Wake-up jitter
 *    therefore stays bounded instead of accumulating: after ten minutes the beats are
 *    still aligned to the original grid.
 *  - **Never early, never crowded.** A beat that had to go out late does not get "made up
 *    for" by pulling the next one in — a short beat is what a user actually notices, far
 *    more than a long one. When a beat slips past its deadline by a perceptible amount the
 *    grid rebases onto where that beat really landed, so the spacing after a hiccup is a
 *    full period again rather than the remainder of one. See [runBeatLoop].
 *  - **Monotonic clock.** [System.nanoTime] is immune to wall-clock and timezone changes.
 *  - **Dedicated thread.** Beats are timed on their own `URGENT_AUDIO` priority thread, so
 *    a busy main thread (recomposition, GC on the UI thread) cannot delay a beat. Nothing
 *    on this thread allocates.
 *  - **Nothing blocking on the timing thread.** Starting a sound and starting a vibration
 *    both cross into the platform — a JNI hop into the audio stack, a binder call into the
 *    system server — and both take a variable few milliseconds. Run in sequence they would
 *    make the beat arrive at a slightly different moment every time, and the second one
 *    would always inherit the first one's delay. Each therefore fires on its own
 *    [BeatOutput] thread, woken in parallel, so the timing thread only ever stamps the
 *    deadline and moves on.
 *  - **Park, then spin.** Sleeping is accurate to a few milliseconds at best, so the
 *    thread parks until shortly before the beat and then yields in a tight loop for the
 *    last stretch. That buys sub-millisecond accuracy for a negligible amount of CPU.
 *
 * The engine owns no Android UI or context objects; it talks to the outside world through
 * [ClickPlayer], [HapticPlayer] and the [beats] flow.
 */
class MetronomeEngine(
    private val clickPlayer: ClickPlayer,
    private val hapticPlayer: HapticPlayer,
) {

    /** Emits the index of each beat as it fires. Used only to drive the pulse animation. */
    private val _beats = MutableSharedFlow<Long>(
        replay = 0,
        extraBufferCapacity = 1,
        // The UI missing a beat is fine; blocking the timing thread never is.
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

    /**
     * The output threads, alive only while the engine is running.
     *
     * Held as fields so the timing loop never has to build or look anything up on a beat.
     */
    private var clickOutput: BeatOutput? = null
    private var hapticOutput: BeatOutput? = null

    /** Handed to [hapticOutput] with the beat; see [BeatOutput] for why this is safe. */
    @Volatile
    private var pulseAmplitude: Int = VibrationStrength.DEFAULT

    /**
     * Allocates the audio resources ahead of time.
     *
     * Blocking (a few milliseconds), so call it off the main thread — typically once when
     * the metronome screen opens, so that pressing Start is instant.
     */
    fun prepare() = clickPlayer.prepare()

    /** Starts beating, immediately firing beat 0. No-op if already running. */
    @Synchronized
    fun start(config: MetronomeConfig) {
        this.config = config
        if (running) return

        running = true
        _isRunning.value = true
        // Both output threads are up and parked before the first beat is due, so beat 0
        // costs no more than any other beat.
        clickOutput = BeatOutput("metronome-click", clickPlayer::click)
        hapticOutput = BeatOutput("metronome-haptic") { hapticPlayer.pulse(pulseAmplitude) }
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
        clickOutput?.shutdown()
        hapticOutput?.shutdown()
        clickOutput = null
        hapticOutput = null
    }

    /** Applies new settings. Takes effect on the next beat; the beat grid is preserved. */
    fun updateConfig(config: MetronomeConfig) {
        this.config = config
    }

    /** Stops the engine and frees the audio resources. */
    @Synchronized
    fun release() {
        stop()
        clickPlayer.release()
    }

    private fun runBeatLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        // Defensive: normally the screen has already warmed the player up.
        clickPlayer.prepare()

        var beatIndex = 0L
        // The first beat fires as soon as the loop starts: pressing Start must feel instant.
        var beatAtNanos = System.nanoTime()

        while (running) {
            if (!sleepUntil(beatAtNanos)) break

            val config = this.config
            // Publish the amplitude before waking the haptic thread, so it reads the value
            // belonging to this beat.
            pulseAmplitude = config.vibrationAmplitude
            if (config.soundEnabled) clickOutput?.fire(beatAtNanos)
            if (config.vibrationEnabled) hapticOutput?.fire(beatAtNanos)
            _beats.tryEmit(beatIndex)
            beatIndex++

            // How late this beat actually went out, measured before the grid moves on.
            val lateNanos = System.nanoTime() - beatAtNanos

            // Advance from the ideal beat time, not from "now" — this is what keeps the
            // grid exact over long runs.
            val periodNanos = Bpm.periodNanos(config.bpm)
            beatAtNanos += periodNanos

            // Unless this beat actually went out late. Holding the grid after a hiccup
            // would shorten the *next* gap by however long the hiccup was, which is heard
            // as a rushed beat — the thing a metronome must never do. Rebasing onto the
            // beat that just fired costs a one-off shift in phase and keeps every gap from
            // here on a full period. It also means a long stall (device suspend, an extreme
            // GC pause) resumes cleanly instead of firing a burst of catch-up beats.
            if (lateNanos > RESYNC_THRESHOLD_NANOS) {
                beatAtNanos += lateNanos
            }
        }
    }

    /**
     * Parks until [targetNanos], then yields until the deadline is actually reached.
     *
     * @return false if the engine was stopped while waiting.
     */
    private fun sleepUntil(targetNanos: Long): Boolean {
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
         * Switch from parking to yielding this long before the beat (5 ms).
         *
         * Wide enough to absorb a park that overshoots — the kernel routinely returns a
         * couple of milliseconds late, and anything it overshoots by lands straight on the
         * beat. At one beat every half second the spin costs about 1% of one core.
         */
        const val SPIN_THRESHOLD_NANOS = 5_000_000L

        /**
         * How late a beat may go out before the grid rebases onto it (12 ms).
         *
         * Below this the error is not audible and the ideal grid is worth keeping, since
         * rebasing on ordinary jitter would let the tempo creep slow over a long session.
         * Above it, the beat is already visibly off and correcting the phase beats
         * shortening the next gap.
         */
        const val RESYNC_THRESHOLD_NANOS = 12_000_000L

        const val THREAD_JOIN_TIMEOUT_MILLIS = 250L
    }
}

/**
 * One output — a click, a vibration — fired on its own thread, one beat at a time.
 *
 * [fire] is called from the timing thread and must never block it: it sets a flag and
 * unparks, which is a handful of microseconds and, more importantly, takes the *same*
 * handful of microseconds every beat. The blocking platform call then happens over here,
 * where being a few milliseconds slow on one beat cannot push the next beat around.
 *
 * A beat is dropped rather than played out of position. Two things can put it out of
 * position: another beat arriving while this one is still being played, and the platform
 * call itself overrunning. Both are handled the same way — the beat carries the deadline it
 * was meant for, and a beat picked up too long after its deadline is thrown away instead of
 * played late, right on top of the next one. Missing a single cue is a smaller lie about the
 * pace than two cues in quick succession, and it keeps every gap the user feels at least a
 * full period. At CPR rates — roughly half a second between beats, against a platform call
 * measured in milliseconds — neither should happen in the first place.
 */
private class BeatOutput(name: String, private val play: () -> Unit) {

    /** True between [fire] and the thread picking the beat up. */
    private val pending = AtomicBoolean(false)

    /** The [System.nanoTime] the pending beat was due at. Written before [pending] is set. */
    @Volatile
    private var dueAtNanos = 0L

    @Volatile
    private var running = true

    private val thread = Thread(::loop, name).apply {
        isDaemon = true
        start()
    }

    /**
     * Asks for one output. Safe to call from the timing thread.
     *
     * @param dueAtNanos the beat's deadline, used to decide whether the beat is still worth
     *   playing by the time this thread gets to it.
     */
    fun fire(dueAtNanos: Long) {
        this.dueAtNanos = dueAtNanos
        if (pending.compareAndSet(false, true)) LockSupport.unpark(thread)
    }

    /**
     * Stops the thread, waiting briefly for a beat already in flight.
     *
     * The wait is what makes Stop mean stop: without it a beat dispatched microseconds
     * before could still reach the speaker after the engine reports itself stopped. It is
     * bounded because Stop is pressed on the main thread — a parked thread returns from
     * this immediately, and the timeout only matters if a platform call has wedged.
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
                if (System.nanoTime() - dueAtNanos <= STALE_BEAT_NANOS) play()
            } else {
                // A `fire` that lands between the check and the park leaves an unpark
                // permit behind, so this returns immediately and the loop picks the beat
                // up — no beat can be lost in the gap. Spurious wake-ups are harmless for
                // the same reason: the flag, not the wake-up, is what says there is work.
                LockSupport.park()
            }
        }
    }

    private companion object {
        /**
         * A beat picked up more than this long after it was due is dropped (50 ms).
         *
         * Roughly where a misplaced beat stops being a slightly late one and starts being
         * heard as part of the next.
         */
        const val STALE_BEAT_NANOS = 50_000_000L

        const val SHUTDOWN_JOIN_MILLIS = 100L
    }
}
