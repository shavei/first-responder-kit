package com.firstresponder.kit

import android.app.Application

/**
 * Application entry point.
 *
 * `onCreate` deliberately does almost nothing — no logging framework, no analytics, no
 * eager I/O — which is most of what keeps cold start under a second.
 */
class FirstResponderApp : Application() {

    /** Created lazily on first access, i.e. after the first frame is already on its way. */
    val container: AppContainer by lazy { AppContainer(this) }
}
