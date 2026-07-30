package com.firstresponder.kit.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log

/**
 * Low-latency [ClickPlayer] built on [AudioTrack] in static mode.
 *
 * Why not `MediaPlayer` or `SoundPool`:
 *  - `MediaPlayer` is built for streamed media; its start latency is tens of milliseconds
 *    and varies per call, which is fatal for a metronome.
 *  - `SoundPool` needs a decoded asset and gives no control over the output format.
 *
 * An `AudioTrack` in [AudioTrack.MODE_STATIC] holds the whole click in a buffer that is
 * written exactly once. Replaying it is then just "rewind and play", with no decoding,
 * no allocation and no file I/O on the timing path. The track is created at the device's
 * native output sample rate with [AudioTrack.PERFORMANCE_MODE_LOW_LATENCY], which are the
 * conditions the platform requires before it will grant the fast audio path.
 *
 * The click plays on the **alarm** stream. That is not a detail: sonification and
 * notification usages are routed to the system stream, which the platform force-mutes
 * whenever the ringer is set to silent or vibrate — so on the phone of anyone who keeps it
 * on silent (which is most people, and nearly everyone carrying it on a shift) the
 * metronome came out completely inaudible with no indication why. The alarm stream is
 * exempt from the ringer mode and from Do Not Disturb's default policy, and it carries its
 * own volume that the hardware keys reach from anywhere in the app; see
 * `MainActivity.volumeControlStream`.
 */
class AudioTrackClickPlayer : ClickPlayer {

    /** Guards [track] against concurrent access from the UI and timing threads. */
    private val lock = Any()

    private var track: AudioTrack? = null

    override fun prepare() {
        synchronized(lock) {
            if (track != null) return

            // Matching the native output rate avoids an internal resampler, which is both
            // a prerequisite for the fast path and one less source of latency.
            val sampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)
            val minBufferBytes = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(0)

            val pcm = ClickSynth.generate(sampleRate, minFrames = minBufferBytes / BYTES_PER_FRAME)
            val bufferBytes = pcm.size * BYTES_PER_FRAME

            // A handful of devices reject the low-latency performance mode; fall back to a
            // plain track rather than losing the click altogether.
            track = buildTrack(sampleRate, bufferBytes, lowLatency = true)
                ?: buildTrack(sampleRate, bufferBytes, lowLatency = false)
            track?.let { track ->
                track.write(pcm, 0, pcm.size)
                // The click is already synthesised with headroom, so the track itself runs
                // wide open: the only thing that should decide how loud this is is the
                // user's alarm volume.
                track.setVolume(AudioTrack.getMaxVolume())
            }
        }
    }

    /** @return the track, or null if the platform refused to build it. */
    private fun buildTrack(sampleRate: Int, bufferBytes: Int, lowLatency: Boolean): AudioTrack? =
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
                .setTransferMode(AudioTrack.MODE_STATIC)
                .apply {
                    if (lowLatency) setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                }
                .build()
        }.onFailure { error ->
            // Audio is a nice-to-have: the pulse and the vibration still work without it.
            Log.w(TAG, "Could not create the click AudioTrack (lowLatency=$lowLatency)", error)
        }.getOrNull()

    override fun click() {
        synchronized(lock) {
            val track = track ?: return
            runCatching {
                // Rewind and replay. `pause` is valid from any state, and reloadStaticData
                // moves the playback head back to the start of the buffer.
                track.pause()
                track.reloadStaticData()
                track.play()
            }.onFailure { error ->
                Log.w(TAG, "Click playback failed", error)
            }
        }
    }

    override fun release() {
        synchronized(lock) {
            track?.let { track ->
                runCatching {
                    track.pause()
                    track.release()
                }
            }
            track = null
        }
    }

    private companion object {
        const val TAG = "ClickPlayer"

        /** 16-bit mono. */
        const val BYTES_PER_FRAME = 2
    }
}
