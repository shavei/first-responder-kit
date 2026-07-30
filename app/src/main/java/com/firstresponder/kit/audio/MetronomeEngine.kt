package com.firstresponder.kit.audio

import android.os.Process
import com.firstresponder.kit.util.Bpm
import com.firstresponder.kit.util.HapticPlayer
import com.firstresponder.kit.util.VibrationStrength
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
 *  - **Monotonic clock.** [System.nanoTime] is immune to wall-clock and timezone changes.
 *  - **Dedicated thread.** Beats are dispatched on their own `URGENT_AUDIO` priority
 *    thread, so a busy main thread (recomposition, GC on the UI thread) cannot delay a
 *    beat. Nothing on this thread allocates.
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
            if (config.soundEnabled) clickPlayer.click()
            if (config.vibrationEnabled) hapticPlayer.pulse(config.vibrationAmplitude)
            _beats.tryEmit(beatIndex)
            beatIndex++

            // Advance from the ideal beat time, not from "now" — this is what keeps the
            // grid exact over long runs.
            val periodNanos = Bpm.periodNanos(config.bpm)
            beatAtNanos += periodNanos

            // If the thread was starved for longer than a whole beat (device suspend, an
            // extreme GC pause), re-anchor rather than firing a burst of catch-up beats.
            val now = System.nanoTime()
            if (now - beatAtNanos > periodNanos) {
                beatAtNanos = now + periodNanos
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
        /** Switch from parking to yielding this long before the beat (2 ms). */
        const val SPIN_THRESHOLD_NANOS = 2_000_000L

        const val THREAD_JOIN_TIMEOUT_MILLIS = 250L
    }
}
