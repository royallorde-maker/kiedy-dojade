package com.kiedziedojade.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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

/** Motywy do wyboru w Ustawieniach (+ Auto = jasny/ciemny wg systemu). */
enum class AppTheme(val label: String, val darkBars: Boolean, val windowBg: Long) {
    SYSTEM("Systemowy", false, 0xFF0A1120),
    MONO("Mono", true, 0xFF0A1120),
    GRAFIT("Grafit", true, 0xFF131316),
    MLEKO("Mleko", false, 0xFFF1F3F8),
    CANDY("Cukierek", true, 0xFF1A0E14),
    BURSZTYN("Bursztyn", true, 0xFF171208),
    LAS("Las", true, 0xFF0C1510),
    LAWENDA("Lawenda", true, 0xFF14101C),
}

fun appThemeOf(name: String?): AppTheme = try {
    AppTheme.valueOf(name ?: "SYSTEM")
} catch (_: Exception) {
    AppTheme.SYSTEM
}

// Mono: granat z niebieskim akcentem — identyfikacja KiedyDojadę (jak w web).
private val MonoScheme = darkColorScheme(
    primary = Color(0xFF5B8CFF),
    onPrimary = Color(0xFF08142E),
    primaryContainer = Color(0xFF1B2F5C),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFF93A4BD),
    secondaryContainer = Color(0xFF1A2740),
    onSecondaryContainer = Color(0xFFE8EDF6),
    tertiary = Color(0xFF7FD1E8),
    tertiaryContainer = Color(0xFF123543),
    onTertiaryContainer = Color(0xFFD9F2FA),
    background = Color(0xFF0A1120),
    onBackground = Color(0xFFE8EDF6),
    surface = Color(0xFF0A1120),
    onSurface = Color(0xFFE8EDF6),
    surfaceVariant = Color(0xFF131D31),
    onSurfaceVariant = Color(0xFF93A4BD),
    surfaceContainerLowest = Color(0xFF070C16),
    surfaceContainerLow = Color(0xFF0D1526),
    surfaceContainer = Color(0xFF131D31),
    surfaceContainerHigh = Color(0xFF1A2740),
    surfaceContainerHighest = Color(0xFF22304A),
    outline = Color(0xFF2C3E5E),
    outlineVariant = Color(0xFF1E2C46),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFF5B8CFF),
    inverseSurface = Color(0xFFE8EDF6),
    inverseOnSurface = Color(0xFF0A1120),
    inversePrimary = Color(0xFF2456E0),
    surfaceBright = Color(0xFF22304A),
    surfaceDim = Color(0xFF070C16),
)

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

// Mleko: jasne odwrócenie mono — granatowa pigułka na kości słoniowej.
private val MlekoScheme = lightColorScheme(
    primary = Color(0xFF2456E0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF0A1120),
    secondary = Color(0xFF5A6B7A),
    secondaryContainer = Color(0xFFE4E9F2),
    onSecondaryContainer = Color(0xFF0F172A),
    tertiary = Color(0xFF0E7C8A),
    tertiaryContainer = Color(0xFFD5EEF2),
    onTertiaryContainer = Color(0xFF062A2F),
    background = Color(0xFFF1F3F8),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFF1F3F8),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE7EBF3),
    onSurfaceVariant = Color(0xFF5A6B7A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAFD),
    // Karty muszą odcinać się od tła (F1F3F8) — wcześniej prawie znikały.
    surfaceContainer = Color(0xFFE9EDF5),
    surfaceContainerHigh = Color(0xFFDFE6F1),
    surfaceContainerHighest = Color(0xFFD5DDEC),
    outline = Color(0xFFB9C2D0),
    outlineVariant = Color(0xFFD5DDEC),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFF2456E0),
    inverseSurface = Color(0xFF0F172A),
    inverseOnSurface = Color(0xFFF1F3F8),
    inversePrimary = Color(0xFFDCE6FF),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDCE1EC),
)

// Cukierek: ciemne cukierkowe tło + różowy akcent.
private val CandyScheme = darkColorScheme(
    primary = Color(0xFFFFB1C8),
    onPrimary = Color(0xFF4A0E20),
    primaryContainer = Color(0xFF5C1A30),
    onPrimaryContainer = Color(0xFFFFD9E3),
    secondary = Color(0xFFE08AA5),
    secondaryContainer = Color(0xFF3A1421),
    onSecondaryContainer = Color(0xFFFFD9E3),
    tertiary = Color(0xFFDD9DF2),
    tertiaryContainer = Color(0xFF38163F),
    onTertiaryContainer = Color(0xFFF5D9FF),
    background = Color(0xFF1A0E14),
    onBackground = Color(0xFFFFE9EF),
    surface = Color(0xFF1A0E14),
    onSurface = Color(0xFFFFE9EF),
    surfaceVariant = Color(0xFF241219),
    onSurfaceVariant = Color(0xFFE0A9BB),
    surfaceContainerLowest = Color(0xFF12070C),
    surfaceContainerLow = Color(0xFF1A0E14),
    surfaceContainer = Color(0xFF241219),
    surfaceContainerHigh = Color(0xFF2F1620),
    surfaceContainerHighest = Color(0xFF3D1C2A),
    outline = Color(0xFF5C2E40),
    outlineVariant = Color(0xFF3A1B28),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFFFFB1C8),
    inverseSurface = Color(0xFFFFE9EF),
    inverseOnSurface = Color(0xFF1A0E14),
    inversePrimary = Color(0xFF7A2A45),
    surfaceBright = Color(0xFF3D1C2A),
    surfaceDim = Color(0xFF12070C),
)

// Bursztyn: miodowy akcent na prawie czarnej kawie.
private val BursztynScheme = darkColorScheme(
    primary = Color(0xFFFFC15C),
    onPrimary = Color(0xFF3A2200),
    primaryContainer = Color(0xFF4A2E0A),
    onPrimaryContainer = Color(0xFFFFE3B3),
    secondary = Color(0xFFE0A44A),
    secondaryContainer = Color(0xFF2E1F0C),
    onSecondaryContainer = Color(0xFFFFE3B3),
    tertiary = Color(0xFFD8B36A),
    tertiaryContainer = Color(0xFF33230F),
    onTertiaryContainer = Color(0xFFFFEFD4),
    background = Color(0xFF171208),
    onBackground = Color(0xFFFFEFD4),
    surface = Color(0xFF171208),
    onSurface = Color(0xFFFFEFD4),
    surfaceVariant = Color(0xFF201812),
    onSurfaceVariant = Color(0xFFC9AE7E),
    surfaceContainerLowest = Color(0xFF0F0B05),
    surfaceContainerLow = Color(0xFF171208),
    surfaceContainer = Color(0xFF201812),
    surfaceContainerHigh = Color(0xFF2A2013),
    surfaceContainerHighest = Color(0xFF362917),
    outline = Color(0xFF5C4A2E),
    outlineVariant = Color(0xFF33270F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFFFFC15C),
    inverseSurface = Color(0xFFFFEFD4),
    inverseOnSurface = Color(0xFF171208),
    inversePrimary = Color(0xFF7A5210),
    surfaceBright = Color(0xFF362917),
    surfaceDim = Color(0xFF0F0B05),
)

// Las: leśna zieleń na ciemnym mchu.
private val LasScheme = darkColorScheme(
    primary = Color(0xFF9BE2A8),
    onPrimary = Color(0xFF0B3D1F),
    primaryContainer = Color(0xFF1D4A2A),
    onPrimaryContainer = Color(0xFFD2F2D6),
    secondary = Color(0xFF8CC49A),
    secondaryContainer = Color(0xFF122019),
    onSecondaryContainer = Color(0xFFD2F2D6),
    tertiary = Color(0xFFA9C7AE),
    tertiaryContainer = Color(0xFF1A2B20),
    onTertiaryContainer = Color(0xFFE4F2E6),
    background = Color(0xFF0C1510),
    onBackground = Color(0xFFE4F2E6),
    surface = Color(0xFF0C1510),
    onSurface = Color(0xFFE4F2E6),
    surfaceVariant = Color(0xFF122019),
    onSurfaceVariant = Color(0xFFA9C7AE),
    surfaceContainerLowest = Color(0xFF070D09),
    surfaceContainerLow = Color(0xFF0C1510),
    surfaceContainer = Color(0xFF122019),
    surfaceContainerHigh = Color(0xFF182720),
    surfaceContainerHighest = Color(0xFF1F3129),
    outline = Color(0xFF33473A),
    outlineVariant = Color(0xFF1B2A20),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFF9BE2A8),
    inverseSurface = Color(0xFFE4F2E6),
    inverseOnSurface = Color(0xFF0C1510),
    inversePrimary = Color(0xFF2E6B3C),
    surfaceBright = Color(0xFF1F3129),
    surfaceDim = Color(0xFF070D09),
)

// Lawenda: fiolet na ciemnej śliwce.
private val LawendaScheme = darkColorScheme(
    primary = Color(0xFFD3B8FF),
    onPrimary = Color(0xFF35145E),
    primaryContainer = Color(0xFF472A76),
    onPrimaryContainer = Color(0xFFEBDDFF),
    secondary = Color(0xFFBFA3EC),
    secondaryContainer = Color(0xFF221A33),
    onSecondaryContainer = Color(0xFFEBDDFF),
    tertiary = Color(0xFFE0A6D5),
    tertiaryContainer = Color(0xFF3A1E38),
    onTertiaryContainer = Color(0xFFF5D9FF),
    background = Color(0xFF14101C),
    onBackground = Color(0xFFF0E9FB),
    surface = Color(0xFF14101C),
    onSurface = Color(0xFFF0E9FB),
    surfaceVariant = Color(0xFF1B1626),
    onSurfaceVariant = Color(0xFFC2B3D9),
    surfaceContainerLowest = Color(0xFF0D0A13),
    surfaceContainerLow = Color(0xFF14101C),
    surfaceContainer = Color(0xFF1B1626),
    surfaceContainerHigh = Color(0xFF241D33),
    surfaceContainerHighest = Color(0xFF2F2642),
    outline = Color(0xFF4A3D63),
    outlineVariant = Color(0xFF2A2138),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFFD3B8FF),
    inverseSurface = Color(0xFFF0E9FB),
    inverseOnSurface = Color(0xFF14101C),
    inversePrimary = Color(0xFF5F3FA3),
    surfaceBright = Color(0xFF2F2642),
    surfaceDim = Color(0xFF0D0A13),
)

@Composable
fun schemeFor(theme: AppTheme): androidx.compose.material3.ColorScheme = when (theme) {
    AppTheme.MONO -> MonoScheme
    AppTheme.GRAFIT -> GrafitScheme
    AppTheme.MLEKO -> MlekoScheme
    AppTheme.CANDY -> CandyScheme
    AppTheme.BURSZTYN -> BursztynScheme
    AppTheme.LAS -> LasScheme
    AppTheme.LAWENDA -> LawendaScheme
    AppTheme.SYSTEM -> if (isSystemInDarkTheme()) MonoScheme else MlekoScheme
}

@Composable
fun KiedyDojadeTheme(theme: AppTheme = AppTheme.SYSTEM, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = schemeFor(theme), typography = AppTypography, content = content)
}
