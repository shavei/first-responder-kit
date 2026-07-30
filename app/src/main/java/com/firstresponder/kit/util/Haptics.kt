package com.firstresponder.kit.util

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Fires one haptic pulse per beat, at a caller-chosen strength.
 *
 * An interface so the metronome engine stays free of Android dependencies and can be
 * driven by a fake in tests.
 */
interface HapticPlayer {

    /** True when the device actually has a vibrator. */
    val isAvailable: Boolean

    /**
     * True when the motor can vary how hard it hits.
     *
     * When false the strength setting still works, but it lengthens the pulse instead of
     * driving the motor harder — see [SystemHapticPlayer].
     */
    val hasAmplitudeControl: Boolean

    /**
     * Emits a single short pulse.
     *
     * Called from the timing thread, so it must not block.
     *
     * @param amplitude strength on the [VibrationStrength] scale; values outside it are clamped.
     */
    fun pulse(amplitude: Int)
}

/**
 * [HapticPlayer] backed by the platform [Vibrator].
 *
 * The pulse is an explicit one-shot at a chosen amplitude, **not** `EFFECT_CLICK`. The
 * predefined effects are the platform's UI haptics: fixed strength, tuned for a tick in the
 * hand while looking at the screen, and far too faint to feel during compressions. A one-shot
 * is the only API that exposes the motor's full range.
 *
 * Devices without amplitude control ignore the amplitude argument entirely, so there the
 * strength maps to pulse *duration* instead — a longer buzz being the only remaining way to
 * make a beat more noticeable. Either way the slider does something on every device.
 */
class SystemHapticPlayer(context: Context) : HapticPlayer {

    private val vibrator: Vibrator? = resolveVibrator(context.applicationContext)

    override val isAvailable: Boolean
        get() = vibrator?.hasVibrator() == true

    override val hasAmplitudeControl: Boolean = vibrator?.hasAmplitudeControl() == true

    /**
     * Declaring the pulse as sonification keeps it working while the phone is in silent
     * mode — the same category the click sound uses.
     */
    private val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /** Guarded by `this`; see [effectFor]. */
    private var cachedAmplitude: Int = 0
    private var cachedEffect: VibrationEffect? = null

    override fun pulse(amplitude: Int) {
        val vibrator = vibrator ?: return
        if (!vibrator.hasVibrator()) return
        val effect = effectFor(VibrationStrength.clamp(amplitude))
        @Suppress("DEPRECATION") // The AudioAttributes overload is the only one on API 26.
        vibrator.vibrate(effect, attributes)
    }

    /**
     * Returns the effect for [amplitude], rebuilding it only when the strength changed.
     *
     * [pulse] runs on the timing thread, where nothing should allocate: within a session the
     * strength is constant, so every beat after the first reuses the cached effect. The lock
     * is here because the settings screen previews a pulse from the main thread; it never
     * wraps the `vibrate` call itself.
     */
    private fun effectFor(amplitude: Int): VibrationEffect = synchronized(this) {
        cachedEffect?.takeIf { cachedAmplitude == amplitude }
            ?: buildEffect(amplitude).also {
                cachedAmplitude = amplitude
                cachedEffect = it
            }
    }

    private fun buildEffect(amplitude: Int): VibrationEffect =
        if (hasAmplitudeControl) {
            VibrationEffect.createOneShot(PULSE_MILLIS, amplitude)
        } else {
            VibrationEffect.createOneShot(durationFor(amplitude), VibrationEffect.DEFAULT_AMPLITUDE)
        }

    /** Maps the strength scale onto a pulse length, for motors that only run at one level. */
    private fun durationFor(amplitude: Int): Long {
        val span = (VibrationStrength.MAX - VibrationStrength.MIN).toFloat()
        val fraction = (VibrationStrength.clamp(amplitude) - VibrationStrength.MIN) / span
        val extra = (LONGEST_PULSE_MILLIS - SHORTEST_PULSE_MILLIS) * fraction
        return SHORTEST_PULSE_MILLIS + extra.toLong()
    }

    private fun resolveVibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    private companion object {
        /**
         * Long enough to register as a distinct thump rather than a faint tick, short enough
         * to stay well clear of the next beat (417 ms apart at the 120 BPM maximum).
         */
        const val PULSE_MILLIS = 30L

        /** Duration range used only when the device has no amplitude control. */
        const val SHORTEST_PULSE_MILLIS = 12L
        const val LONGEST_PULSE_MILLIS = 55L
    }
}
