package com.firstresponder.kit.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// A deliberately small, high-contrast palette. Emergency red for the primary action,
// green for "go", and near-neutral greys so nothing competes with them.
private val EmergencyRed = Color(0xFFFF5A5F)
private val EmergencyRedDeep = Color(0xFFB3252A)
private val GoGreen = Color(0xFF3DDC84)
private val GoGreenDeep = Color(0xFF11663C)

/** Also mirrored in `colors.xml` as the window background, to avoid a cold-start flash. */
internal val DarkBackground = Color(0xFF101314)

/** The default. Dark is easier on night-adapted eyes and saves power on OLED panels. */
val DarkColorScheme = darkColorScheme(
    primary = EmergencyRed,
    onPrimary = Color(0xFF2A0709),
    primaryContainer = Color(0xFF4A1417),
    onPrimaryContainer = Color(0xFFFFDAD8),
    secondary = Color(0xFFB9C4C5),
    onSecondary = Color(0xFF232B2C),
    tertiary = GoGreen,
    onTertiary = Color(0xFF06301B),
    background = DarkBackground,
    onBackground = Color(0xFFECEFEF),
    surface = Color(0xFF181C1D),
    onSurface = Color(0xFFECEFEF),
    surfaceVariant = Color(0xFF262B2C),
    onSurfaceVariant = Color(0xFFC3C9CA),
    outline = Color(0xFF4A5152),
    error = EmergencyRed,
    onError = Color(0xFF2A0709),
)

val LightColorScheme = lightColorScheme(
    primary = EmergencyRedDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD8),
    onPrimaryContainer = Color(0xFF410004),
    secondary = Color(0xFF4E5859),
    onSecondary = Color.White,
    tertiary = GoGreenDeep,
    onTertiary = Color.White,
    background = Color(0xFFFAFDFD),
    onBackground = Color(0xFF191C1D),
    surface = Color(0xFFF1F4F4),
    onSurface = Color(0xFF191C1D),
    surfaceVariant = Color(0xFFDCE4E5),
    onSurfaceVariant = Color(0xFF3F4849),
    outline = Color(0xFF6F797A),
    error = EmergencyRedDeep,
    onError = Color.White,
)
