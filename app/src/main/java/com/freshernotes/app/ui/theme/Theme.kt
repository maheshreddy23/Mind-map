package com.freshernotes.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freshernotes.app.R

/**
 * Design tokens from docs/design-spec.md §2. Screens read colours only from [Mind.colors] (or MaterialTheme,
 * which is derived from it); no screen hard-codes a colour. Every text/background pair used is ≥ 4.5:1.
 */
@Immutable
data class MindColors(
    val ground: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val fieldLine: Color,
    val accent: Color,
    /** Text/icons on [accent]. */
    val onAccent: Color,
    val accentSoft: Color,
    val amberText: Color,
    val amberSoft: Color,
    val amberTint: Color,
    val amberBorder: Color,
    val violetSoft: Color,
    val violetText: Color,
    val neutralChip: Color,
    val neutralChipText: Color,
    val record: Color,
    /** Text on [ink] (dark primary buttons, the user's bubble). */
    val onInk: Color,
    /** Book-spine colours, rotated per topic; white text on each is ≥ 7:1. */
    val spines: List<Color>,
    /** Title text on a spine, and secondary text. */
    val onSpine: Color,
    val onSpineMuted: Color,
    /** The darker 6 dp inset on a spine's edge. */
    val spineInset: Color,
    val isDark: Boolean,
)

val LightMindColors = MindColors(
    ground = Color(0xFFF7F5F0), surface = Color(0xFFFFFFFF), ink = Color(0xFF1C1B19), muted = Color(0xFF6B6760),
    line = Color(0xFFE4E0D8), fieldLine = Color(0xFFD6D1C7), accent = Color(0xFF1F5F5B), onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFE6EFEE), amberText = Color(0xFF8A4712), amberSoft = Color(0xFFF3E7D7), amberTint = Color(0xFFFBEBDD),
    amberBorder = Color(0xFFD9A45C), violetSoft = Color(0xFFECE7F5), violetText = Color(0xFF4B3A78),
    neutralChip = Color(0xFFECEAE5), neutralChipText = Color(0xFF4A4640), record = Color(0xFFB3261E), onInk = Color(0xFFFFFFFF),
    spines = listOf(Color(0xFF1F5F5B), Color(0xFF8A4712), Color(0xFF4B3A78), Color(0xFF3F4A55)),
    onSpine = Color(0xFFFFFFFF), onSpineMuted = Color(0xFFE6E2DA), spineInset = Color(0x2E000000), isDark = false,
)

val DarkMindColors = MindColors(
    ground = Color(0xFF14171A), surface = Color(0xFF1C2024), ink = Color(0xFFECE9E3), muted = Color(0xFFA3A09A),
    line = Color(0xFF3A4146), fieldLine = Color(0xFF4A5258), accent = Color(0xFF7FC4BC), onAccent = Color(0xFF0E2B28),
    accentSoft = Color(0xFF1E3634), amberText = Color(0xFFE8B47E), amberSoft = Color(0xFF33281C), amberTint = Color(0xFF3A2A1C),
    amberBorder = Color(0xFFA97A3C), violetSoft = Color(0xFF2A2440), violetText = Color(0xFFC7B8F0),
    neutralChip = Color(0xFF2A2F33), neutralChipText = Color(0xFFD2CEC7), record = Color(0xFFB3261E), onInk = Color(0xFF14171A),
    spines = listOf(Color(0xFF1F5F5B), Color(0xFF8A4712), Color(0xFF4B3A78), Color(0xFF3F4A55)),
    onSpine = Color(0xFFFFFFFF), onSpineMuted = Color(0xFFE6E2DA), spineInset = Color(0x2E000000), isDark = true,
)

/** Session focus mode (§2): always dark, whatever the system theme. */
@Immutable
object FocusColors {
    val bg = Color(0xFF14171A)
    val text = Color(0xFFECE9E3)
    val muted = Color(0xFFA3A09A)
    val controlBg = Color(0xFF22272B)
    val line = Color(0xFF3A4146)
    val accent = Color(0xFF7FC4BC)
    val questionRule = Color(0xFF2F5F5A)
    val idleMic = Color(0xFF1F5F5B)
    val record = Color(0xFFB3261E)
    val onMic = Color(0xFFFFFFFF)
    val offSegment = Color(0xFFC9C5BE)
    val transparent = Color(0x00000000)
}

val Newsreader = FontFamily(
    Font(R.font.newsreader_400, FontWeight.Normal),
    Font(R.font.newsreader_500, FontWeight.Medium),
)
val Inter = FontFamily(
    Font(R.font.inter_400, FontWeight.Normal),
    Font(R.font.inter_500, FontWeight.Medium),
    Font(R.font.inter_600, FontWeight.SemiBold),
)

/** The type scale of §2. */
@Immutable
data class MindType(
    val screenTitle: TextStyle = TextStyle(fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 30.sp, lineHeight = 35.sp),
    val cardTitle: TextStyle = TextStyle(fontFamily = Newsreader, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 25.sp),
    val spineTitle: TextStyle = TextStyle(fontFamily = Newsreader, fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 22.sp),
    val question: TextStyle = TextStyle(fontFamily = Newsreader, fontWeight = FontWeight.Normal, fontSize = 20.sp, lineHeight = 28.sp),
    val reading: TextStyle = TextStyle(fontFamily = Newsreader, fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 27.sp),
    val body: TextStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    val bodyLarge: TextStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 25.sp),
    val label: TextStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
    val button: TextStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    val caption: TextStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    val chip: TextStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp),
    val overline: TextStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.5.sp),
)

/** Spacing and shape (§2). */
object MindDimens {
    val screenPadding = 20.dp
    val blockGap = 16.dp
    val cardRadius = 16.dp
    val pillHeight = 46.dp
    val touch = 44.dp
    val talkButton = 62.dp
    val micButton = 76.dp
}

private val LocalMindColors = staticCompositionLocalOf { LightMindColors }
private val LocalMindType = staticCompositionLocalOf { MindType() }

object Mind {
    val colors: MindColors @Composable @ReadOnlyComposable get() = LocalMindColors.current
    val type: MindType @Composable @ReadOnlyComposable get() = LocalMindType.current
}

private fun MindColors.toScheme() = if (isDark) {
    darkColorScheme(
        primary = accent, onPrimary = onAccent, primaryContainer = accentSoft, onPrimaryContainer = ink,
        secondary = amberText, secondaryContainer = amberSoft, onSecondaryContainer = ink,
        background = ground, onBackground = ink, surface = surface, onSurface = ink, surfaceVariant = neutralChip,
        onSurfaceVariant = muted, outline = fieldLine, outlineVariant = line, error = record, errorContainer = amberTint,
        onErrorContainer = amberText, surfaceContainer = surface, surfaceContainerHigh = surface, surfaceContainerLow = ground,
    )
} else {
    lightColorScheme(
        primary = accent, onPrimary = onAccent, primaryContainer = accentSoft, onPrimaryContainer = ink,
        secondary = amberText, secondaryContainer = amberSoft, onSecondaryContainer = ink,
        background = ground, onBackground = ink, surface = surface, onSurface = ink, surfaceVariant = neutralChip,
        onSurfaceVariant = muted, outline = fieldLine, outlineVariant = line, error = record, errorContainer = amberTint,
        onErrorContainer = amberText, surfaceContainer = surface, surfaceContainerHigh = surface, surfaceContainerLow = ground,
    )
}

private fun MindType.toTypography() = Typography(
    headlineLarge = screenTitle, headlineMedium = screenTitle.copy(fontSize = 26.sp), headlineSmall = cardTitle.copy(fontSize = 22.sp),
    titleLarge = cardTitle, titleMedium = label.copy(fontSize = 16.sp), titleSmall = label,
    bodyLarge = bodyLarge, bodyMedium = body, bodySmall = caption,
    labelLarge = button, labelMedium = label.copy(fontSize = 12.sp), labelSmall = chip,
)

/** The app theme: Mind tokens plus a Material 3 scheme derived from them (no dynamic colour). */
@Composable
fun MindTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (dark) DarkMindColors else LightMindColors
    val type = MindType()
    CompositionLocalProvider(LocalMindColors provides colors, LocalMindType provides type) {
        MaterialTheme(colorScheme = colors.toScheme(), typography = type.toTypography(), content = content)
    }
}
