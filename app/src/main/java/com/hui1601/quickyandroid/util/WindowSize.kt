package com.hui1601.quickyandroid.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

enum class WindowSizeClass { Compact, Medium, Expanded }

@Composable
fun rememberWindowSizeClass(): WindowSizeClass {
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp.dp
    return when {
        widthDp < 600.dp -> WindowSizeClass.Compact
        widthDp < 840.dp -> WindowSizeClass.Medium
        else -> WindowSizeClass.Expanded
    }
}

@Composable
fun rememberIsTablet(): Boolean {
    return rememberWindowSizeClass() == WindowSizeClass.Expanded
}
