package com.rajnikant.moneybrain.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.rajnikant.moneybrain.R

/*
 * The Modernist visual system (design_handoff_moneybrain_ui): Archivo, flat surfaces,
 * zero corner radius, 2px rules, one red accent used sparingly, tabular numerals.
 */

// Light tokens
private val Paper = Color(0xFFF3F2F2)
private val Ink = Color(0xFF201E1D)
private val SurfaceLight = Color(0xFFEAE9E9)
private val Accent = Color(0xFFEC3013)       // fills
private val AccentDeep = Color(0xFFAE1800)   // text-size red
private val AccentTint = Color(0xFFFFE0D9)
private val AccentTintSoft = Color(0xFFFFF2EF)
private val TrackLight = Color(0xFFD7D3D3)

// Dark tokens
private val AccentDark = Color(0xFFFF563C)
private val AccentTextDark = Color(0xFFFF9783)
private val AttentionDark = Color(0xFF4D170E)
private val AttentionSoftDark = Color(0xFF3A1610)
private val TrackDark = Color(0xFF444141)
private val SurfaceDark = Color(0xFF2A2827)

/** Tokens the M3 color scheme has no slots for. Read via LocalModernist / mb(). */
@Immutable
data class ModernistColors(
    val ink: Color,          // primary text; light fill on dark
    val paper: Color,        // page background
    val surface: Color,      // card background
    val accent: Color,       // red fills
    val accentDeep: Color,   // red text
    val accentInverse: Color, // red text on an INVERTED (ink-coloured) card
    val accentTint: Color,   // attention strip / AUTO tag background
    val accentTintSoft: Color, // split-card background
    val track: Color,        // bar track
    val rule: Color,         // 2px section rules and strong borders
    val ruleFaint: Color,    // 1px row rules
    val cardBorder: Color,   // 1px card outline
    val muted: Color,        // 50-55% text
    val faint: Color,        // 45-50% text
)

private val LightModernist = ModernistColors(
    ink = Ink,
    paper = Paper,
    surface = SurfaceLight,
    accent = Accent,
    accentDeep = AccentDeep,
    accentInverse = AccentTextDark,
    accentTint = AccentTint,
    accentTintSoft = AccentTintSoft,
    track = TrackLight,
    rule = Ink.copy(alpha = 0.4f),
    ruleFaint = Ink.copy(alpha = 0.15f),
    cardBorder = Ink.copy(alpha = 0.25f),
    muted = Ink.copy(alpha = 0.55f),
    faint = Ink.copy(alpha = 0.5f),
)

private val DarkModernist = ModernistColors(
    ink = Paper,
    paper = Ink,
    surface = SurfaceDark,
    accent = AccentDark,
    accentDeep = AccentTextDark,
    accentInverse = AccentDeep,
    accentTint = AttentionDark,
    accentTintSoft = AttentionSoftDark,
    track = TrackDark,
    rule = Paper.copy(alpha = 0.35f),
    ruleFaint = Paper.copy(alpha = 0.15f),
    cardBorder = Paper.copy(alpha = 0.25f),
    muted = Paper.copy(alpha = 0.5f),
    faint = Paper.copy(alpha = 0.45f),
)

val LocalModernist = staticCompositionLocalOf { LightModernist }

private val LightScheme = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = AccentTint,
    onPrimaryContainer = AccentDeep,
    secondary = Ink,
    onSecondary = Paper,
    secondaryContainer = Ink,          // selected chips: solid ink
    onSecondaryContainer = Paper,
    tertiary = AccentDeep,
    onTertiary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = SurfaceLight,
    onSurfaceVariant = Ink.copy(alpha = 0.55f),
    surfaceContainerLowest = SurfaceLight,
    surfaceContainerLow = SurfaceLight,
    surfaceContainer = SurfaceLight,
    surfaceContainerHigh = SurfaceLight,
    surfaceContainerHighest = SurfaceLight,
    error = AccentDeep,
    onError = Color.White,
    errorContainer = AccentTint,
    onErrorContainer = AccentDeep,
    outline = Ink.copy(alpha = 0.4f),
    outlineVariant = Ink.copy(alpha = 0.15f),
)

private val DarkScheme = darkColorScheme(
    primary = AccentDark,
    onPrimary = Ink,
    primaryContainer = AttentionDark,
    onPrimaryContainer = AccentTextDark,
    secondary = Paper,
    onSecondary = Ink,
    secondaryContainer = Paper,        // selected chips invert
    onSecondaryContainer = Ink,
    tertiary = AccentTextDark,
    onTertiary = Ink,
    background = Ink,
    onBackground = Paper,
    surface = Ink,
    onSurface = Paper,
    surfaceVariant = SurfaceDark,
    onSurfaceVariant = Paper.copy(alpha = 0.5f),
    surfaceContainerLowest = SurfaceDark,
    surfaceContainerLow = SurfaceDark,
    surfaceContainer = SurfaceDark,
    surfaceContainerHigh = SurfaceDark,
    surfaceContainerHighest = SurfaceDark,
    error = AccentTextDark,
    onError = Ink,
    errorContainer = AttentionDark,
    onErrorContainer = AccentTextDark,
    outline = Paper.copy(alpha = 0.35f),
    outlineVariant = Paper.copy(alpha = 0.15f),
)

private val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val archivo = GoogleFont("Archivo")

/** Archivo 400/600/800, fetched via the system font provider; falls back to sans-serif. */
val ArchivoFamily = FontFamily(
    Font(googleFont = archivo, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = archivo, fontProvider = fontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = archivo, fontProvider = fontProvider, weight = FontWeight.ExtraBold),
)

private const val TNUM = "tnum" // tabular figures — every number column lines up

private val ModernistTypography = Typography(
    // hero number (46/800, tight)
    displaySmall = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.ExtraBold, fontSize = 46.sp, lineHeight = 48.sp, letterSpacing = (-0.02).em, fontFeatureSettings = TNUM),
    // card money (26/800)
    headlineMedium = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 30.sp, fontFeatureSettings = TNUM),
    // screen titles (22/800)
    headlineSmall = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.01).em, fontFeatureSettings = TNUM),
    // stat-cell numbers (17/800)
    titleLarge = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, lineHeight = 21.sp, fontFeatureSettings = TNUM),
    // section headers (13/800 caps at the call site)
    titleMedium = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, lineHeight = 17.sp, letterSpacing = 0.06.em, fontFeatureSettings = TNUM),
    // row titles (14.5/600)
    titleSmall = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, lineHeight = 19.sp, fontFeatureSettings = TNUM),
    bodyLarge = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 19.sp, fontFeatureSettings = TNUM),
    bodyMedium = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Normal, fontSize = 13.5.sp, lineHeight = 18.sp, fontFeatureSettings = TNUM),
    // row meta (11/400)
    bodySmall = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 15.sp, fontFeatureSettings = TNUM),
    // buttons (12/800 caps-ish)
    labelLarge = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.06.em, fontFeatureSettings = TNUM),
    // kickers (11/800 wide)
    labelMedium = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.1.em, fontFeatureSettings = TNUM),
    // tags + bottom nav (10/800)
    labelSmall = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, lineHeight = 13.sp, letterSpacing = 0.05.em, fontFeatureSettings = TNUM),
)

private val ZeroShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp),
)

@Composable
fun MoneyBrainTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalModernist provides if (darkTheme) DarkModernist else LightModernist) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = ModernistTypography,
            shapes = ZeroShapes,
            content = content,
        )
    }
}
