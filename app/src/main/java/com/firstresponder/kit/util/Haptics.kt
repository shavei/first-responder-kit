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
     * When false the strength setting still works, but it lengthens and multiplies the hits
     * instead of driving the motor harder — see [SystemHapticPlayer].
     */
    val hasAmplitudeControl: Boolean

    /**
     * Emits one beat's worth of vibration — a burst of hits, see [VibrationPattern].
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
 *  - **A hand-built waveform, not `EFFECT_CLICK`.** The predefined effects are the
 *    platform's UI haptics: fixed strength, tuned for a tick in the hand while looking at
 *    the screen, and far too faint to feel during compressions. Only an explicit waveform
 *    exposes the motor's full range and lets the beat be shaped rather than merely fired.
 *  - **Alarm usage.** The system scales haptics by the usage they declare, and a
 *    *sonification* pulse is treated as a UI tick: attenuated by the touch-feedback
 *    intensity setting, and suppressed outright under Do Not Disturb on some devices. Alarm
 *    usage is scaled by the alarm intensity instead and is exempt from that suppression, so
 *    the same amplitude arrives at the motor considerably stronger. A CPR pace cue is an
 *    alarm in every sense that matters here.
 *  - **A burst, not a pulse.** Amplitude tops out at 255, and past that the only way to make
 *    a beat hit harder is to hit more often: the motor is slammed several times per beat,
 *    each hit held long enough to reach full excursion, with a short silence in between so
 *    the mass swings back and is thrown again. That is what turns a hum into a rattle you
 *    can hear across the room. [VibrationPattern] holds the shape and the reasoning.
 *
 * Devices without amplitude control ignore the amplitude argument entirely; there the burst
 * — more hits, held longer — carries the strength setting on its own, so the slider still
 * does something.
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
        val timings = VibrationPattern.timingsFor(amplitude)
        return if (hasAmplitudeControl) {
            VibrationEffect.createWaveform(
                timings,
                VibrationPattern.amplitudesFor(amplitude),
                NO_REPEAT,
            )
        } else {
            // Without an amplitudes array the platform reads the timings as off/on/off/…,
            // starting off, so a leading zero puts the first hit at the top of the beat.
            VibrationEffect.createWaveform(longArrayOf(0L, *timings), NO_REPEAT)
        }
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
        /** Play the burst once per beat; the engine, not the vibrator, keeps the tempo. */
        const val NO_REPEAT = -1
    }
}
