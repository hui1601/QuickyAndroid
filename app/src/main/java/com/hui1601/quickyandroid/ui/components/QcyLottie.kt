package com.hui1601.quickyandroid.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty

/**
 * Renders a Lottie asset bundled under `assets/lottie/`.
 *
 * Every stroke/fill named "accent" inside the JSON is recolored to [accent]
 * (defaults to the theme primary) via dynamic properties, so the same
 * animation adapts to light/dark and dynamic color schemes. Pass
 * [iterations] = 1 for one-shot animations; they hold their final frame.
 */
@Composable
fun QcyLottie(
    asset: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    iterations: Int = LottieConstants.IterateForever,
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.Asset(asset))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = iterations
    )
    // Lottie color properties must be ARGB ints; a Compose Color crashes at draw.
    // Gradients take one Integer per gradient stop (all bundled assets use two).
    val accentArgb = accent.toArgb()
    val gradientStops = remember(accentArgb) { arrayOf(accentArgb, accentArgb) }
    val dynamicProperties = rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(LottieProperty.STROKE_COLOR, accentArgb, "**", "accent"),
        rememberLottieDynamicProperty(LottieProperty.COLOR, accentArgb, "**", "accent"),
        rememberLottieDynamicProperty(LottieProperty.GRADIENT_COLOR, gradientStops, "**", "accent")
    )
    LottieAnimation(
        composition = composition,
        progress = { progress },
        dynamicProperties = dynamicProperties,
        modifier = modifier
    )
}
