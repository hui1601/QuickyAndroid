@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.hui1601.quickyandroid.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun style(weight: FontWeight, size: Int, lineHeight: Int, letterSpacing: Float) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp
)

/**
 * M3 type scale: 15 baseline + 15 emphasized (Expressive) styles.
 * Emphasized styles are opt-in per usage — apply them to hero headlines,
 * section headers, selected/active states, and primary action labels.
 */
val Typography = Typography(
    displayLarge = style(FontWeight.Normal, 57, 64, -0.25f),
    displayMedium = style(FontWeight.Normal, 45, 52, 0f),
    displaySmall = style(FontWeight.Normal, 36, 44, 0f),
    headlineLarge = style(FontWeight.Normal, 32, 40, 0f),
    headlineMedium = style(FontWeight.Normal, 28, 36, 0f),
    headlineSmall = style(FontWeight.Normal, 24, 32, 0f),
    titleLarge = style(FontWeight.SemiBold, 22, 28, 0f),
    titleMedium = style(FontWeight.SemiBold, 16, 24, 0.15f),
    titleSmall = style(FontWeight.Medium, 14, 20, 0.1f),
    bodyLarge = style(FontWeight.Normal, 16, 24, 0.5f),
    bodyMedium = style(FontWeight.Normal, 14, 20, 0.25f),
    bodySmall = style(FontWeight.Normal, 12, 16, 0.4f),
    labelLarge = style(FontWeight.Medium, 14, 20, 0.1f),
    labelMedium = style(FontWeight.Medium, 12, 16, 0.5f),
    labelSmall = style(FontWeight.Medium, 11, 16, 0.5f),
    displayLargeEmphasized = style(FontWeight.Medium, 57, 64, -0.25f),
    displayMediumEmphasized = style(FontWeight.Medium, 45, 52, 0f),
    displaySmallEmphasized = style(FontWeight.Medium, 36, 44, 0f),
    headlineLargeEmphasized = style(FontWeight.Medium, 32, 40, 0f),
    headlineMediumEmphasized = style(FontWeight.Medium, 28, 36, 0f),
    headlineSmallEmphasized = style(FontWeight.Medium, 24, 32, 0f),
    titleLargeEmphasized = style(FontWeight.Bold, 22, 28, 0f),
    titleMediumEmphasized = style(FontWeight.Bold, 16, 24, 0.15f),
    titleSmallEmphasized = style(FontWeight.Bold, 14, 20, 0.1f),
    bodyLargeEmphasized = style(FontWeight.Medium, 16, 24, 0.5f),
    bodyMediumEmphasized = style(FontWeight.Medium, 14, 20, 0.25f),
    bodySmallEmphasized = style(FontWeight.Medium, 12, 16, 0.4f),
    labelLargeEmphasized = style(FontWeight.Bold, 14, 20, 0.1f),
    labelMediumEmphasized = style(FontWeight.Bold, 12, 16, 0.5f),
    labelSmallEmphasized = style(FontWeight.Bold, 11, 16, 0.5f)
)

/** Tabular (monospaced) figures for values that change in place: battery %, dB, volume. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")
