package com.firstresponder.kit.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firstresponder.kit.FirstResponderApp
import com.firstresponder.kit.settings.UserSettings
import com.firstresponder.kit.ui.ProvideAppLanguage
import com.firstresponder.kit.ui.screens.WidgetConfigScreen
import com.firstresponder.kit.ui.theme.FirstResponderKitTheme
import kotlinx.coroutines.flow.first

/**
 * Where a widget is set up, both when it is placed and whenever it is edited afterwards.
 *
 * The launcher starts this activity with the id of the widget it has just created and
 * takes a cancelled result as "the user changed their mind", removing the placeholder
 * again — so the result is set to cancelled up front and only turned into a success by
 * [save]. That is also what makes backing out of a widget that already exists safe: not
 * saving leaves both the stored configuration and what is on the home screen untouched.
 *
 * The screen keeps the configuration being edited in memory. Rotating the phone would
 * otherwise lose a half-finished tile, which is why this activity declares the same
 * `configChanges` as the main one.
 */
class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            // Started by something that is not a widget host. Nothing to configure.
            finish()
            return
        }

        val store = WidgetStore(this)
        val isNewWidget = !store.isConfigured(appWidgetId)
        val stored = store.load(appWidgetId)
        val settingsFlow = (application as FirstResponderApp).container.settingsRepository.settings

        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = UserSettings())
            var config by remember { mutableStateOf(stored) }

            // A brand new widget starts on the patient the responder has told Settings they
            // usually work with. Applied only while the config is still untouched, so it
            // can never overwrite something typed in the moment the settings arrive.
            LaunchedEffect(Unit) {
                if (isNewWidget) {
                    val saved = settingsFlow.first()
                    if (config == stored) {
                        config = config.copy(patientType = saved.defaultPatientType)
                    }
                }
            }

            ProvideAppLanguage(language = settings.language) {
                FirstResponderKitTheme(themeMode = settings.themeMode) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        WidgetConfigScreen(
                            config = config,
                            defaultBpm = settings.defaultBpm,
                            isNewWidget = isNewWidget,
                            onConfigChange = { changed -> config = changed },
                            onSave = { save(config, settings) },
                            onCancel = ::finish,
                            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
                        )
                    }
                }
            }
        }
    }

    /** Stores the configuration, draws the widget with it, and hands the id back. */
    private fun save(config: WidgetConfig, settings: UserSettings) {
        val store = WidgetStore(this)
        store.save(appWidgetId, config.sanitized())
        // The first widget on the device is drawn before the app has ever been backgrounded,
        // so the settings it needs are mirrored here rather than only in KitWidget.refreshAll.
        store.saveAppState(language = settings.language, defaultBpm = settings.defaultBpm)

        KitWidget.update(this, AppWidgetManager.getInstance(this), appWidgetId)
        setResult(
            RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
        )
        finish()
    }
}
