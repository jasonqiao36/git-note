package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.model.ThemeMode

@Immutable
data class PaperExtraColors(
    val codeBackground: Color,
    val quoteBar: Color,
    val badgeEditedBg: Color,
    val badgeEditedText: Color
)

val LocalPaperColors = staticCompositionLocalOf {
    PaperExtraColors(
        codeBackground = PaperCodeBgLight,
        quoteBar = PaperTerracottaLight,
        badgeEditedBg = PaperPrimaryContainerLight,
        badgeEditedText = PaperOnPrimaryContainerLight
    )
}

private val WarmPaperLightScheme = lightColorScheme(
    primary = PaperTerracottaLight,
    onPrimary = PaperOnPrimaryLight,
    primaryContainer = PaperPrimaryContainerLight,
    onPrimaryContainer = PaperOnPrimaryContainerLight,
    secondary = PaperSecondaryLight,
    onSecondary = PaperOnPrimaryLight,
    secondaryContainer = PaperSecondaryContainerLight,
    onSecondaryContainer = PaperOnSecondaryContainerLight,
    background = PaperBgLight,
    onBackground = PaperInkLight,
    surface = PaperBgLight,
    onSurface = PaperInkLight,
    surfaceVariant = PaperSurfaceMidLight,
    onSurfaceVariant = PaperInkMutedLight,
    surfaceContainerLowest = PaperCardLight,
    surfaceContainerLow = PaperSurfaceLowLight,
    surfaceContainer = PaperSurfaceMidLight,
    surfaceContainerHigh = PaperSurfaceHighLight,
    outline = PaperOutlineLight,
    outlineVariant = PaperOutlineVariantLight,
    error = PaperErrorLight,
    errorContainer = PaperErrorContainerLight
)

private val WarmInkDarkScheme = darkColorScheme(
    primary = PaperTerracottaDark,
    onPrimary = PaperOnPrimaryDark,
    primaryContainer = PaperPrimaryContainerDark,
    onPrimaryContainer = PaperOnPrimaryContainerDark,
    secondary = PaperSecondaryDark,
    onSecondary = PaperOnPrimaryDark,
    secondaryContainer = PaperSecondaryContainerDark,
    onSecondaryContainer = PaperOnSecondaryContainerDark,
    background = PaperBgDark,
    onBackground = PaperInkDark,
    surface = PaperBgDark,
    onSurface = PaperInkDark,
    surfaceVariant = PaperSurfaceMidDark,
    onSurfaceVariant = PaperInkMutedDark,
    surfaceContainerLowest = PaperCardDark,
    surfaceContainerLow = PaperSurfaceLowDark,
    surfaceContainer = PaperSurfaceMidDark,
    surfaceContainerHigh = PaperSurfaceHighDark,
    outline = PaperOutlineDark,
    outlineVariant = PaperOutlineVariantDark,
    error = PaperErrorDark,
    errorContainer = PaperErrorContainerDark
)

private val GitNoteShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun GitNoteTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (isDark) WarmInkDarkScheme else WarmPaperLightScheme
    val extraColors = if (isDark) {
        PaperExtraColors(
            codeBackground = PaperCodeBgDark,
            quoteBar = PaperTerracottaDark,
            badgeEditedBg = PaperPrimaryContainerDark,
            badgeEditedText = PaperOnPrimaryContainerDark
        )
    } else {
        PaperExtraColors(
            codeBackground = PaperCodeBgLight,
            quoteBar = PaperTerracottaLight,
            badgeEditedBg = PaperPrimaryContainerLight,
            badgeEditedText = PaperOnPrimaryContainerLight
        )
    }

    CompositionLocalProvider(LocalPaperColors provides extraColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = GitNoteTypography,
            shapes = GitNoteShapes,
            content = content
        )
    }
}
