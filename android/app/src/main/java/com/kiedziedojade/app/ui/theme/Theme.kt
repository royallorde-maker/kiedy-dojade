package com.kiedziedojade.app.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.kiedziedojade.app.R

// Space Grotesk w całej apce (OFL, w res/font).
private val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
)

private val AppTypography = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.copy(fontFamily = SpaceGrotesk),
        displayMedium = t.displayMedium.copy(fontFamily = SpaceGrotesk),
        displaySmall = t.displaySmall.copy(fontFamily = SpaceGrotesk),
        headlineLarge = t.headlineLarge.copy(fontFamily = SpaceGrotesk),
        headlineMedium = t.headlineMedium.copy(fontFamily = SpaceGrotesk),
        headlineSmall = t.headlineSmall.copy(fontFamily = SpaceGrotesk),
        titleLarge = t.titleLarge.copy(fontFamily = SpaceGrotesk),
        titleMedium = t.titleMedium.copy(fontFamily = SpaceGrotesk),
        titleSmall = t.titleSmall.copy(fontFamily = SpaceGrotesk),
        bodyLarge = t.bodyLarge.copy(fontFamily = SpaceGrotesk),
        bodyMedium = t.bodyMedium.copy(fontFamily = SpaceGrotesk),
        bodySmall = t.bodySmall.copy(fontFamily = SpaceGrotesk),
        labelLarge = t.labelLarge.copy(fontFamily = SpaceGrotesk),
        labelMedium = t.labelMedium.copy(fontFamily = SpaceGrotesk),
        labelSmall = t.labelSmall.copy(fontFamily = SpaceGrotesk),
    )
}

/** Jedyny motyw aplikacji — Grafit. Ciemny, więc paski też są jasne. */
const val KD_WINDOW_BG: Long = 0xFF131316

// Grafit: miękka ciemna szarość. Ciemne bary.
private val GrafitScheme = darkColorScheme(
    primary = Color(0xFFE8E8EA),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF2E2E34),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFFA7A7B3),
    secondaryContainer = Color(0xFF26262C),
    onSecondaryContainer = Color(0xFFECECF1),
    tertiary = Color(0xFFBFC0C9),
    tertiaryContainer = Color(0xFF232329),
    onTertiaryContainer = Color(0xFFFFFFFF),
    background = Color(0xFF131316),
    onBackground = Color(0xFFECECF1),
    surface = Color(0xFF131316),
    onSurface = Color(0xFFECECF1),
    surfaceVariant = Color(0xFF1B1B1F),
    onSurfaceVariant = Color(0xFFA7A7B3),
    surfaceContainerLowest = Color(0xFF0C0C0E),
    surfaceContainerLow = Color(0xFF131316),
    surfaceContainer = Color(0xFF1B1B1F),
    surfaceContainerHigh = Color(0xFF232329),
    surfaceContainerHighest = Color(0xFF2C2C33),
    outline = Color(0xFF3A3A42),
    outlineVariant = Color(0xFF26262C),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFFE8E8EA),
    inverseSurface = Color(0xFFE8E8EA),
    inverseOnSurface = Color(0xFF131316),
    inversePrimary = Color(0xFF3A3A42),
    surfaceBright = Color(0xFF2C2C33),
    surfaceDim = Color(0xFF0C0C0E),
)

@Composable
fun KiedyDojadeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = GrafitScheme, typography = AppTypography) {
        // Kolor tekstu musi być ustawiony u samego źródła: pełnoekranowe
        // nakładki (prowadzenie, wybieracz) są poza Scaffoldem, więc bez tego
        // domyślny kolor Compose (czarny) zostawał czarny także w ciemnych
        // motywach i nagłówki znikały na ciemnym szkle.
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onBackground,
            content = content,
        )
    }
}
