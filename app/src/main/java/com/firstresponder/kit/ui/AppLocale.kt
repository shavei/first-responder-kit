package com.firstresponder.kit.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.text.TextUtils
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.firstresponder.kit.settings.AppLanguage
import java.util.Locale

/**
 * Draws [content] in the language the user picked in Settings.
 *
 * `stringResource` resolves against `LocalContext.current.resources`, so overriding that
 * context is all it takes to switch languages — no activity recreation, no restart, and
 * the change is visible on the screen the switch was made on. [LocalLayoutDirection] is
 * provided alongside it so a right-to-left language mirrors the layout as well as the text.
 */
@Composable
fun ProvideAppLanguage(language: AppLanguage, content: @Composable () -> Unit) {
    val baseContext = LocalContext.current
    val baseConfiguration = LocalConfiguration.current
    // SYSTEM keeps whatever the device is set to, which is also what the base configuration
    // already carries.
    val locale = language.locale ?: baseConfiguration.locales[0]

    val localizedContext = remember(baseContext, baseConfiguration, locale) {
        LocalizedContext(baseContext, baseConfiguration, locale)
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration,
        LocalLayoutDirection provides locale.layoutDirection(),
        content = content,
    )
}

/**
 * A context that answers with resources in [locale].
 *
 * It has to be a wrapper around the original context rather than the bare result of
 * `createConfigurationContext`: that result is detached from the activity, and code which
 * walks the context chain to reach the window — `KeepScreenOn`, for one — would quietly
 * stop finding it.
 */
private class LocalizedContext(
    base: Context,
    configuration: Configuration,
    locale: Locale,
) : ContextWrapper(base) {

    private val localizedResources: Resources = base
        .createConfigurationContext(
            Configuration(configuration).apply {
                setLocale(locale)
                setLayoutDirection(locale)
            },
        )
        .resources

    override fun getResources(): Resources = localizedResources
}

private fun Locale.layoutDirection(): LayoutDirection =
    if (TextUtils.getLayoutDirectionFromLocale(this) == View.LAYOUT_DIRECTION_RTL) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }
