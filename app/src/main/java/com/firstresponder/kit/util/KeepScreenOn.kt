package com.firstresponder.kit.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Holds the screen on while [enabled] is true.
 *
 * `FLAG_KEEP_SCREEN_ON` is used rather than a wake lock: it needs no permission and the
 * window manager clears it automatically if the app is killed, so the flag can never leak
 * and drain the battery. The effect keys on [enabled], so the flag is dropped the moment
 * the metronome stops or the screen is left.
 */
@Composable
fun KeepScreenOn(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(enabled, context) {
        val window = context.findActivity()?.window
        if (enabled) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

/** Walks the context wrappers to find the hosting [Activity], if there is one. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
