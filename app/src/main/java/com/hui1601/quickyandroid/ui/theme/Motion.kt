package com.hui1601.quickyandroid.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween

/**
 * Screen-transition easings and durations (M3 emphasized legacy system).
 * Component motion (press, toggle, switch, slider…) must NOT use these —
 * read springs from `MaterialTheme.motionScheme` instead
 * (`fastSpatialSpec` / `fastEffectsSpec` for small controls).
 */
object QuickyMotion {

    /** Emphasized decelerate — used for elements entering the screen. */
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Emphasized accelerate — used for elements exiting the screen permanently. */
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** Standard easing — used for elements that begin and end on screen. */
    val StandardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Standard decelerate — fallback for simple enter transitions. */
    val StandardDecelerate = CubicBezierEasing(0f, 0f, 0f, 1f)

    /** Standard accelerate — fallback for simple exit transitions. */
    val StandardAccelerate = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    /** Forward enter transition tween spec (slide + fade). */
    fun <T> transitionEnterSpatial() = tween<T>(durationMillis = 400, easing = EmphasizedDecelerate)

    /** Forward exit transition tween spec (slide + fade). */
    fun <T> transitionExitSpatial() = tween<T>(durationMillis = 200, easing = EmphasizedAccelerate)

    /** Fade tween used alongside spatial transitions. */
    fun <T> transitionFade() = tween<T>(durationMillis = 250, easing = StandardEasing)
}
