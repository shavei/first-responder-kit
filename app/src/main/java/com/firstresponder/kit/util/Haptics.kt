package com.firstresponder.kit.util

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
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
     * Called on the engine's haptic output thread, never on the thread that times the beats,
     * so the binder call into the system server may block for the few milliseconds it
     * usually takes without pushing the beat grid around.
     *
     * @param amplitude strength on the [VibrationStrength] scale; values outside it are clamped.
     */
    fun pulse(amplitude: Int)
}

/**
 * [HapticPlayer] backed by the platform [Vibrator].
 *
 * Three things decide how hard a beat actually lands, and this class pushes all of them:
 *
 *  - **A one-shot, not `EFFECT_CLICK`.** The predefined effects are the platform's UI
 *    haptics: fixed strength, tuned for a tick in the hand while looking at the screen, and
 *    far too faint to feel during compressions. A one-shot is the only API that exposes the
 *    motor's full range.
 *  - **Alarm usage.** The system scales haptics by the usage they declare, and a
 *    *sonification* pulse is treated as a UI tick: attenuated by the touch-feedback
 *    intensity setting, and suppressed outright under Do Not Disturb on some devices. Alarm
 *    usage is scaled by the alarm intensity instead and is exempt from that suppression, so
 *    the same amplitude arrives at the motor considerably stronger. A CPR pace cue is an
 *    alarm in every sense that matters here.
 *  - **Pulse length.** A vibration motor takes 10–20 ms just to spin up, so a very short
 *    one-shot never reaches full excursion no matter what amplitude it asks for. The pulse
 *    therefore grows with the strength setting as well — at maximum it is long enough to
 *    read as a solid thump, while still leaving most of the beat silent.
 *
 * Devices without amplitude control ignore the amplitude argument entirely; there the
 * duration alone carries the strength setting, so the slider still does something.
 */
class SystemHapticPlayer(context: Context) : HapticPlayer {

    private val vibrator: Vibrator? = resolveVibrator(context.applicationContext)

    override val isAvailable: Boolean
        get() = vibrator?.hasVibrator() == true

    override val hasAmplitudeControl: Boolean = vibrator?.hasAmplitudeControl() == true

    /** Alarm usage, on API 33+ where the vibrator takes it directly. See the class docs. */
    private val vibrationAttributes: VibrationAttributes? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM)
        } else {
            null
        }

    /**
     * The pre-API-33 route to the same place: the platform maps an alarm [AudioAttributes]
     * usage onto the alarm vibration usage, which keeps the pulse at full strength and
     * keeps it firing while the phone is silent.
     */
    private val audioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /** Guarded by `this`; see [effectFor]. */
    private var cachedAmplitude: Int = 0
    private var cachedEffect: VibrationEffect? = null

    override fun pulse(amplitude: Int) {
        val vibrator = vibrator ?: return
        if (!vibrator.hasVibrator()) return
        val effect = effectFor(VibrationStrength.clamp(amplitude))
        val vibrationAttributes = this.vibrationAttributes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && vibrationAttributes != null) {
            vibrator.vibrate(effect, vibrationAttributes)
        } else {
            @Suppress("DEPRECATION") // The AudioAttributes overload is the only one before 33.
            vibrator.vibrate(effect, audioAttributes)
        }
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

    private fun buildEffect(amplitude: Int): VibrationEffect {
        val millis = durationFor(amplitude)
        return if (hasAmplitudeControl) {
            VibrationEffect.createOneShot(millis, amplitude)
        } else {
            VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE)
        }
    }

    /**
     * Maps the strength scale onto a pulse length.
     *
     * Applied on every device, not just the ones without amplitude control: a motor needs
     * time to reach the amplitude it was asked for, so length and strength go together.
     */
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
         * How long the pulse lasts, from the weakest setting to the strongest.
         *
         * The top end is a deliberate thump rather than a tick: it is what makes maximum
         * strength feel like maximum. It still ends long before the next beat, which is
         * 500 ms away at the 120 BPM maximum, so beats never run into one another.
         */
        const val SHORTEST_PULSE_MILLIS = 15L
        const val LONGEST_PULSE_MILLIS = 70L
    }
}
