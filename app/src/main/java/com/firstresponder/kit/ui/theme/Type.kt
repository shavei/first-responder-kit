package com.firstresponder.kit.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Default = Typography()

/**
 * Material 3 typography with the few styles this app leans on made bigger and bolder —
 * everything must stay readable at arm's length.
 */
val KitTypography = Typography(
    displayLarge = Default.displayLarge.copy(
        fontSize = 104.sp,
        lineHeight = 108.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-2).sp,
    ),
    headlineLarge = Default.headlineLarge.copy(fontWeight = FontWeight.Bold),
    headlineMedium = Default.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Default.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Default.labelLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = Default.bodyLarge.copy(fontSize = 17.sp),
)
