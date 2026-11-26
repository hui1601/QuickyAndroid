package com.hui1601.quickyandroid.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

data class PaletteRoles(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color
)

/**
 * Accent palettes for the non-wallpaper theme. [QCY] keeps the branded
 * navy/yellow scheme untouched; the others replace only the primary and
 * secondary role families.
 */
data class ThemePalette(
    val id: String,
    val label: String,
    val swatch: Color,
    val light: PaletteRoles?,
    val dark: PaletteRoles?
) {
    fun applyTo(scheme: ColorScheme, dark: Boolean): ColorScheme {
        val roles = (if (dark) this.dark else light) ?: return scheme
        return scheme.copy(
            primary = roles.primary,
            onPrimary = roles.onPrimary,
            primaryContainer = roles.primaryContainer,
            onPrimaryContainer = roles.onPrimaryContainer,
            secondary = roles.secondary,
            onSecondary = roles.onSecondary,
            secondaryContainer = roles.secondaryContainer,
            onSecondaryContainer = roles.onSecondaryContainer,
            inversePrimary = roles.primary
        )
    }

    companion object {
        val QCY = ThemePalette(
            id = "qcy",
            label = "QCY",
            swatch = QcyYellow,
            light = null,
            dark = null
        )

        val OCEAN = ThemePalette(
            id = "ocean",
            label = "Ocean",
            swatch = Color(0xFF2E5BFF),
            light = PaletteRoles(
                primary = Color(0xFF1D4ED8),
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFDCE5FF),
                onPrimaryContainer = Color(0xFF001A41),
                secondary = Color(0xFF3B5BA9),
                onSecondary = Color(0xFFFFFFFF),
                secondaryContainer = Color(0xFFDDE6FF),
                onSecondaryContainer = Color(0xFF001A41)
            ),
            dark = PaletteRoles(
                primary = Color(0xFFB3C5FF),
                onPrimary = Color(0xFF002E69),
                primaryContainer = Color(0xFF003EA8),
                onPrimaryContainer = Color(0xFFDCE5FF),
                secondary = Color(0xFFB3C5FF),
                onSecondary = Color(0xFF002E69),
                secondaryContainer = Color(0xFF2A4A8F),
                onSecondaryContainer = Color(0xFFDCE5FF)
            )
        )

        val MINT = ThemePalette(
            id = "mint",
            label = "Mint",
            swatch = Color(0xFF16A34A),
            light = PaletteRoles(
                primary = Color(0xFF0A7B3E),
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFB9F5CE),
                onPrimaryContainer = Color(0xFF00210E),
                secondary = Color(0xFF3E6650),
                onSecondary = Color(0xFFFFFFFF),
                secondaryContainer = Color(0xFFC1EBD5),
                onSecondaryContainer = Color(0xFF00210E)
            ),
            dark = PaletteRoles(
                primary = Color(0xFF7ADBA0),
                onPrimary = Color(0xFF00391C),
                primaryContainer = Color(0xFF00522A),
                onPrimaryContainer = Color(0xFFB9F5CE),
                secondary = Color(0xFFA5D0B5),
                onSecondary = Color(0xFF0B3821),
                secondaryContainer = Color(0xFF254F35),
                onSecondaryContainer = Color(0xFFC1EBD5)
            )
        )

        val GRAPE = ThemePalette(
            id = "grape",
            label = "Grape",
            swatch = Color(0xFF7C4DFF),
            light = PaletteRoles(
                primary = Color(0xFF6A3AB2),
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFEADDFF),
                onPrimaryContainer = Color(0xFF25005A),
                secondary = Color(0xFF625B71),
                onSecondary = Color(0xFFFFFFFF),
                secondaryContainer = Color(0xFFE8DEF8),
                onSecondaryContainer = Color(0xFF1D192B)
            ),
            dark = PaletteRoles(
                primary = Color(0xFFD0BCFF),
                onPrimary = Color(0xFF3C0091),
                primaryContainer = Color(0xFF5516A0),
                onPrimaryContainer = Color(0xFFEADDFF),
                secondary = Color(0xFFCCC2DC),
                onSecondary = Color(0xFF332D41),
                secondaryContainer = Color(0xFF4A4458),
                onSecondaryContainer = Color(0xFFE8DEF8)
            )
        )

        val ROSE = ThemePalette(
            id = "rose",
            label = "Rose",
            swatch = Color(0xFFE5484D),
            light = PaletteRoles(
                primary = Color(0xFFB3261E),
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFFFDAD6),
                onPrimaryContainer = Color(0xFF410002),
                secondary = Color(0xFF775651),
                onSecondary = Color(0xFFFFFFFF),
                secondaryContainer = Color(0xFFFFDAD6),
                onSecondaryContainer = Color(0xFF2C1512)
            ),
            dark = PaletteRoles(
                primary = Color(0xFFFFB4AB),
                onPrimary = Color(0xFF690005),
                primaryContainer = Color(0xFF93000A),
                onPrimaryContainer = Color(0xFFFFDAD6),
                secondary = Color(0xFFE7BDB7),
                onSecondary = Color(0xFF442925),
                secondaryContainer = Color(0xFF5D3F3B),
                onSecondaryContainer = Color(0xFFFFDAD6)
            )
        )

        val all = listOf(QCY, OCEAN, MINT, GRAPE, ROSE)

        fun fromId(id: String): ThemePalette =
            all.firstOrNull { it.id == id } ?: QCY
    }
}
