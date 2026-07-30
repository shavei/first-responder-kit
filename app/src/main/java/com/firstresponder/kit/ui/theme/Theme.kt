package com.firstresponder.kit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.firstresponder.kit.settings.ThemeMode

/**
 * The app theme.
 *
 * Dynamic (wallpaper) colour is intentionally not used: an emergency tool should look
 * identical on every device, and the red/green action colours carry meaning that a
 * wallpaper-derived palette would destroy.
 */
@Composable
fun FirstResponderKitTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColorScheme else LightColorScheme,
        typography = KitTypography,
        content = content,
    )
}
