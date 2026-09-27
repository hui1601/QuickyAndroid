package com.hui1601.quickyandroid.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = qcyPrimaryLight,
    onPrimary = qcyOnPrimaryLight,
    primaryContainer = qcyPrimaryContainerLight,
    onPrimaryContainer = qcyOnPrimaryContainerLight,
    secondary = qcySecondaryLight,
    onSecondary = qcyOnSecondaryLight,
    secondaryContainer = qcySecondaryContainerLight,
    onSecondaryContainer = qcyOnSecondaryContainerLight,
    tertiary = qcyTertiaryLight,
    onTertiary = qcyOnTertiaryLight,
    tertiaryContainer = qcyTertiaryContainerLight,
    onTertiaryContainer = qcyOnTertiaryContainerLight,
    error = qcyErrorLight,
    onError = qcyOnErrorLight,
    errorContainer = qcyErrorContainerLight,
    onErrorContainer = qcyOnErrorContainerLight,
    background = qcyBackgroundLight,
    onBackground = qcyOnBackgroundLight,
    surface = qcySurfaceLight,
    onSurface = qcyOnSurfaceLight,
    surfaceVariant = qcySurfaceVariantLight,
    onSurfaceVariant = qcyOnSurfaceVariantLight,
    outline = qcyOutlineLight,
    outlineVariant = qcyOutlineVariantLight,
    scrim = qcyScrimLight,
    inverseSurface = qcyInverseSurfaceLight,
    inverseOnSurface = qcyInverseOnSurfaceLight,
    inversePrimary = qcyInversePrimaryLight,
    surfaceContainerLowest = qcySurfaceContainerLowestLight,
    surfaceContainerLow = qcySurfaceContainerLowLight,
    surfaceContainer = qcySurfaceContainerLight,
    surfaceContainerHigh = qcySurfaceContainerHighLight,
    surfaceContainerHighest = qcySurfaceContainerHighestLight
)

private val DarkColorScheme = darkColorScheme(
    primary = qcyPrimaryDark,
    onPrimary = qcyOnPrimaryDark,
    primaryContainer = qcyPrimaryContainerDark,
    onPrimaryContainer = qcyOnPrimaryContainerDark,
    secondary = qcySecondaryDark,
    onSecondary = qcyOnSecondaryDark,
    secondaryContainer = qcySecondaryContainerDark,
    onSecondaryContainer = qcyOnSecondaryContainerDark,
    tertiary = qcyTertiaryDark,
    onTertiary = qcyOnTertiaryDark,
    tertiaryContainer = qcyTertiaryContainerDark,
    onTertiaryContainer = qcyOnTertiaryContainerDark,
    error = qcyErrorDark,
    onError = qcyOnErrorDark,
    errorContainer = qcyErrorContainerDark,
    onErrorContainer = qcyOnErrorContainerDark,
    background = qcyBackgroundDark,
    onBackground = qcyOnBackgroundDark,
    surface = qcySurfaceDark,
    onSurface = qcyOnSurfaceDark,
    surfaceVariant = qcySurfaceVariantDark,
    onSurfaceVariant = qcyOnSurfaceVariantDark,
    outline = qcyOutlineDark,
    outlineVariant = qcyOutlineVariantDark,
    scrim = qcyScrimDark,
    inverseSurface = qcyInverseSurfaceDark,
    inverseOnSurface = qcyInverseOnSurfaceDark,
    inversePrimary = qcyInversePrimaryDark,
    surfaceContainerLowest = qcySurfaceContainerLowestDark,
    surfaceContainerLow = qcySurfaceContainerLowDark,
    surfaceContainer = qcySurfaceContainerDark,
    surfaceContainerHigh = qcySurfaceContainerHighDark,
    surfaceContainerHighest = qcySurfaceContainerHighestDark
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QuickyAndroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = QcyShapes,
        motionScheme = MotionScheme.expressive(),
        content = content
    )
}
