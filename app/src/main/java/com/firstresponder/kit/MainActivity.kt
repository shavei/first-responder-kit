package com.firstresponder.kit

import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firstresponder.kit.settings.UserSettings
import com.firstresponder.kit.ui.navigation.KitNavHost
import com.firstresponder.kit.ui.theme.FirstResponderKitTheme

/**
 * The single activity.
 *
 * There is no splash screen and nothing blocking in `onCreate`: settings arrive
 * asynchronously and the UI simply starts from the defaults (dark theme), which match the
 * window background declared in the manifest theme. Cold start is a single frame of work.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // The click plays on the alarm stream (see AudioTrackClickPlayer), so that is the
        // stream the volume keys have to reach — otherwise pressing volume-up while the
        // metronome runs moves the media volume and the click stays exactly as quiet as it
        // was. Set for the whole activity rather than just the metronome screen: this app
        // makes one sound, and the keys should always be adjusting it.
        volumeControlStream = AudioManager.STREAM_ALARM

        val settingsFlow =(application as FirstResponderApp).container.settingsRepository.settings

        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = UserSettings())

            FirstResponderKitTheme(themeMode = settings.themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    KitNavHost(
                        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
                        defaultPatientType = settings.defaultPatientType,
                    )
                }
            }
        }
    }
}
