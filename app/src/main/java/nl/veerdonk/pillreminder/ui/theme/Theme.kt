package nl.veerdonk.pillreminder.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/*
 * A calm health-app palette: a neutral base, dark text, and the launcher's teal as the accent,
 * with its coral as a second accent for "needs you now" moments. Dynamic (wallpaper) colour is
 * off by default so the app always looks the same.
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E6B5E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3EDE5),
    onPrimaryContainer = Color(0xFF0B2A23),
    secondary = Color(0xFF4A635D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0EBE7),
    onSecondaryContainer = Color(0xFF17201D),
    tertiary = Color(0xFF9A4A44),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFCE3E0),
    onTertiaryContainer = Color(0xFF3B0A07),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF5F7F6),
    onBackground = Color(0xFF191C1B),
    surface = Color(0xFFF5F7F6),
    onSurface = Color(0xFF191C1B),
    surfaceVariant = Color(0xFFE3E9E6),
    onSurfaceVariant = Color(0xFF59635F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F3F1),
    surfaceContainer = Color(0xFFEAEFEC),
    surfaceContainerHigh = Color(0xFFE4EAE7),
    surfaceContainerHighest = Color(0xFFDEE5E1),
    outline = Color(0xFF89938F),
    outlineVariant = Color(0xFFD6DEDA),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD3C1),
    onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF16503F),
    onPrimaryContainer = Color(0xFFB2EFDD),
    secondary = Color(0xFFB1CCC4),
    onSecondary = Color(0xFF1C352F),
    secondaryContainer = Color(0xFF2E4540),
    onSecondaryContainer = Color(0xFFCDE8E0),
    tertiary = Color(0xFFF2B8B5),
    onTertiary = Color(0xFF561E1B),
    tertiaryContainer = Color(0xFF5E2A26),
    onTertiaryContainer = Color(0xFFFFDAD7),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF7A1F19),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1513),
    onBackground = Color(0xFFDEE4E1),
    surface = Color(0xFF0F1513),
    onSurface = Color(0xFFDEE4E1),
    surfaceVariant = Color(0xFF3B4542),
    onSurfaceVariant = Color(0xFFBAC4C0),
    surfaceContainerLowest = Color(0xFF0A0F0E),
    surfaceContainerLow = Color(0xFF171D1B),
    surfaceContainer = Color(0xFF1B2220),
    surfaceContainerHigh = Color(0xFF252C2A),
    surfaceContainerHighest = Color(0xFF303735),
    outline = Color(0xFF89938F),
    outlineVariant = Color(0xFF3B4542),
)

/** Two weights only: regular for reading, semibold for headings and values. */
private val AppTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Tabular figures, so times and counts line up and don't jiggle as they change. */
val Tabular = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun PillReminderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, typography = AppTypography, shapes = AppShapes, content = content)
}
