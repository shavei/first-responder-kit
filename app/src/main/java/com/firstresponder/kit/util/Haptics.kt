package com.firstresponder.kit.util

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Fires one haptic pulse per beat.
 *
 * An interface so the metronome engine stays free of Android dependencies and can be
 * driven by a fake in tests.
 */
interface HapticPlayer {

    /** True when the device actually has a vibrator. */
    val isAvailable: Boolean

    /** Emits a single short pulse. Called from the timing thread, so it must not block. */
    fun pulse()
}

/** [HapticPlayer] backed by the platform [Vibrator]. */
class SystemHapticPlayer(context: Context) : HapticPlayer {

    private val vibrator: Vibrator? = resolveVibrator(context.applicationContext)

    override val isAvailable: Boolean
        get() = vibrator?.hasVibrator() == true

    /**
     * A crisp tick rather than a buzz. `EFFECT_CLICK` is the platform's own tuned tick and
     * feels identical to system UI haptics; older devices get a short one-shot instead.
     */
    private val effect: VibrationEffect =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        } else {
            VibrationEffect.createOneShot(PULSE_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE)
        }

    /**
     * Declaring the pulse as sonification keeps it working while the phone is in silent
     * mode — the same category the click sound uses.
     */
    private val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    override fun pulse() {
        val vibrator = vibrator ?: return
        if (!vibrator.hasVibrator()) return
        @Suppress("DEPRECATION") // The AudioAttributes overload is the only one on API 26.
        vibrator.vibrate(effect, attributes)
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
        const val PULSE_MILLIS = 25L
    }
}
