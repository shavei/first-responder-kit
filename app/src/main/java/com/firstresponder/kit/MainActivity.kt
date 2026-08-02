package com.firstresponder.kit

import android.content.Intent
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firstresponder.kit.settings.UserSettings
import com.firstresponder.kit.ui.ProvideAppLanguage
import com.firstresponder.kit.ui.navigation.KitNavHost
import com.firstresponder.kit.ui.theme.FirstResponderKitTheme
import com.firstresponder.kit.widget.KitWidget
import com.firstresponder.kit.widget.WidgetLaunch

/**
 * The single activity.
 *
 * There is no splash screen and nothing blocking in `onCreate`: settings arrive
 * asynchronously and the UI simply starts from the defaults (dark theme), which match the
 * window background declared in the manifest theme. Cold start is a single frame of work.
 */
class MainActivity : ComponentActivity() {

    /**
     * The screen a home-screen widget asked for, until the nav graph has opened it.
     *
     * Compose state rather than a plain field because the tap may arrive while the activity
     * is already running, through [onNewIntent], and the graph has to notice.
     */
    private var widgetRoute by mutableStateOf<String?>(null)

    /**
     * The last settings the UI saw, kept so [onStop] can bring the widgets in line without
     * a suspending read at exactly the moment the process may be about to stop running one.
     */
    private var latestSettings: UserSettings = UserSettings()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        widgetRoute = WidgetLaunch.routeFrom(intent)

        // The click plays on the alarm stream (see StreamingClickTrack), so that is the
        // stream the volume keys have to reach — otherwise pressing volume-up while the
        // metronome runs moves the media volume and the click stays exactly as quiet as it
        // was. Set for the whole activity rather than just the metronome screen: this app
        // makes one sound, and the keys should always be adjusting it.
        volumeControlStream = AudioManager.STREAM_ALARM

        val settingsFlow =(application as FirstResponderApp).container.settingsRepository.settings

        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = UserSettings())
            SideEffect { latestSettings = settings }

            ProvideAppLanguage(language = settings.language) {
                FirstResponderKitTheme(themeMode = settings.themeMode) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        KitNavHost(
                            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
                            defaultPatientType = settings.defaultPatientType,
                            widgetRoute = widgetRoute,
                            onWidgetRouteHandled = { widgetRoute = null },
                        )
                    }
                }
            }
        }
    }

    /** A widget tapped while the app was already open. `singleTop` routes it here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        widgetRoute = WidgetLaunch.routeFrom(intent)
    }

    /**
     * Redraws the home-screen widgets on the way out.
     *
     * This is the only moment they can have gone stale: a widget captioned in English has
     * to be in Hebrew after the language is switched, and one showing the default rate has
     * to show the new one. Done here rather than from a background collector because the
     * settings cannot change while the app is not in front of the user, and an offline tool
     * has no business keeping a coroutine alive to watch for something that cannot happen.
     */
    override fun onStop() {
        super.onStop()
        KitWidget.refreshAll(this, latestSettings)
    }
}
