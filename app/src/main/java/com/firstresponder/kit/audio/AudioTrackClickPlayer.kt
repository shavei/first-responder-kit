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
            track?.write(pcm, 0, pcm.size)
        }
    }

    /** @return the track, or null if the platform refused to build it. */
    private fun buildTrack(sampleRate: Int, bufferBytes: Int, lowLatency: Boolean): AudioTrack? =
        runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        // Sonification, not media: this is a UI-style cue, so it should not
                        // be treated as music by the system or by other apps.
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
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
