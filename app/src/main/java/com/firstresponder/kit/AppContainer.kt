package com.firstresponder.kit

import android.content.Context
import com.firstresponder.kit.audio.AudioTrackClickPlayer
import com.firstresponder.kit.audio.MetronomeEngine
import com.firstresponder.kit.settings.DataStoreSettingsRepository
import com.firstresponder.kit.settings.SettingsRepository
import com.firstresponder.kit.util.HapticPlayer
import com.firstresponder.kit.util.SystemHapticPlayer

/**
 * Manual dependency container.
 *
 * A DI framework would add startup cost and build complexity for no benefit at this size;
 * everything here is created lazily, so opening the app costs nothing but the container
 * itself. Swap any of these for a fake in tests by constructing the view models directly.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(appContext)
    }

    val hapticPlayer: HapticPlayer by lazy { SystemHapticPlayer(appContext) }

    /**
     * One engine for the whole process. Only one metronome can be visible at a time, and
     * a single instance guarantees a single timing thread and a single audio track.
     */
    val metronomeEngine: MetronomeEngine by lazy {
        MetronomeEngine(
            clickPlayer = AudioTrackClickPlayer(),
            hapticPlayer = hapticPlayer,
        )
    }
}
