package io.oniimai.kanade

import android.animation.ValueAnimator
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView

/**
 * True when the system's animator scale is off ("remove animations"); setup motion then shows still frames.
 * The setting is read too, so turning animations off is seen even before this process hears about it.
 */
internal fun reducedMotion(context: Context): Boolean =
    !ValueAnimator.areAnimatorsEnabled() ||
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/**
 * [reducedMotion], kept current while setup stays open: read again whenever setup's window regains focus
 * (the way back from the system settings) and whenever the animator scale setting changes.
 */
@Composable
internal fun motionStill(): Boolean {
    val view = LocalView.current
    val context = view.context
    var still by remember(view) { mutableStateOf(reducedMotion(context)) }
    DisposableEffect(view) {
        val focus = ViewTreeObserver.OnWindowFocusChangeListener { if (it) still = reducedMotion(context) }
        val scale = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { still = reducedMotion(context) }
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener(focus)
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, scale)
        onDispose {
            if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnWindowFocusChangeListener(focus)
            context.contentResolver.unregisterContentObserver(scale)
        }
    }
    return still
}

/**
 * Whether setup's window has focus. Looping motion stops when it does not: the game in the background, a
 * system dialog on top, or the screen off.
 */
@Composable
internal fun motionActive(): Boolean {
    val view = LocalView.current
    var focused by remember(view) { mutableStateOf(view.hasWindowFocus()) }
    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnWindowFocusChangeListener { focused = it }
        view.viewTreeObserver.addOnWindowFocusChangeListener(listener)
        onDispose { if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnWindowFocusChangeListener(listener) }
    }
    return focused
}
