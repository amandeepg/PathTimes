package ca.amandeep.path.ui

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.with
import androidx.compose.runtime.Composable

/**
 * A vertical swap animation that fades in/out and slides in/out.
 */
@ExperimentalAnimationApi
@get:Composable
val verticalSwapAnimation: AnimatedContentScope<String>.() -> ContentTransform
    get() = {
        val contentTransform: ContentTransform =
            slideInVertically(animationSpec = tween(durationMillis = 800)) { height -> height } + fadeIn(
                animationSpec = tween(durationMillis = 800)
            ) with slideOutVertically(animationSpec = tween(durationMillis = 800)) { height -> -height } + fadeOut(
                animationSpec = tween(durationMillis = 800)
            )
        contentTransform.using(SizeTransform(clip = false))
    }
