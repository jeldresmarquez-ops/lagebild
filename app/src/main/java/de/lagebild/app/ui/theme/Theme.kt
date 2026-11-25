package de.lagebild.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import de.lagebild.app.data.settings.ThemeMode
import de.lagebild.app.domain.model.WarnLevel

private val Navy = Color(0xFF0B3D6E)
private val NavyLight = Color(0xFFA8C8F0)
private val Signal = Color(0xFFC62828)

private val LightColors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4F5),
    onPrimaryContainer = Color(0xFF041C33),
    secondary = Color(0xFF4F5D6E),
    tertiary = Signal,
    background = Color(0xFFF8F9FB),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE9EDF2),
    onSurfaceVariant = Color(0xFF4A525C),
    outlineVariant = Color(0xFFD5DAE0),
)

private val DarkColors = darkColorScheme(
    primary = NavyLight,
    onPrimary = Color(0xFF06223F),
    primaryContainer = Color(0xFF1C3A5C),
    onPrimaryContainer = Color(0xFFD6E4F5),
    secondary = Color(0xFFB7C3D1),
    tertiary = Color(0xFFFF8A80),
    background = Color(0xFF111418),
    surface = Color(0xFF171B20),
    surfaceVariant = Color(0xFF242A31),
    onSurfaceVariant = Color(0xFFBFC6CE),
    outlineVariant = Color(0xFF353C44),
)

/** Serifen-Überschriften wirken ruhig und „zeitungshaft“, der Rest bleibt in der Systemschrift. */
private val AppTypography = Typography().let { t ->
    t.copy(
        headlineSmall = t.headlineSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp),
        titleMedium = t.titleMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    )
}

@Composable
fun LagebildTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
}

/** Farben nach Warnstufe – angelehnt an die Farbskala des DWD. */
fun warnColor(level: WarnLevel): Color = when (level) {
    WarnLevel.MINOR -> Color(0xFFF2C200)
    WarnLevel.MODERATE -> Color(0xFFF57C00)
    WarnLevel.SEVERE -> Color(0xFFD32F2F)
    WarnLevel.EXTREME -> Color(0xFF7B1FA2)
}

fun warnColorArgb(level: WarnLevel): Int = warnColor(level).toArgb()

fun onWarnColor(level: WarnLevel): Color = if (level == WarnLevel.MINOR) Color(0xFF2B2300) else Color.White

fun parseHexColor(hex: String?, fallback: Color): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)
