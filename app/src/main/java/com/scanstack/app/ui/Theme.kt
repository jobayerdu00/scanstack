package com.scanstack.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scanstack.app.R

// ============================================================================
// Design tokens. Change colors, type and spacing here, not in screens.
// ============================================================================

/** Brand palette (deep blue + teal on a soft, light background). */
object AppColors {
    val Primary = Color(0xFF2457D6)        // primary buttons, key actions, active navigation
    val PrimaryDark = Color(0xFF1B43A8)    // text on subtle blue backgrounds
    val Teal = Color(0xFF12B8A6)           // selected highlights, accents, progress
    val TealDark = Color(0xFF0B7F72)       // text/icons on subtle teal backgrounds
    val Background = Color(0xFFF7F9FC)     // screen background
    val Card = Color(0xFFFFFFFF)           // cards, sheets, bars
    val Text = Color(0xFF172033)           // headings, important text, standard icons
    val TextSecondary = Color(0xFF64748B)  // supporting text
    val TextMuted = Color(0xFF94A3B8)      // placeholders, disabled hints
    val Border = Color(0xFFE2E8F0)         // card borders, dividers
    val Canvas = Color(0xFFEDF1F7)         // backdrop behind document pages
    val BlueSubtle = Color(0xFFEEF4FF)     // subtle blue fill (icon tiles, selected rows)
    val TealSubtle = Color(0xFFE8FAF7)     // subtle teal fill
    val Success = Color(0xFF16A34A)
    val Warning = Color(0xFFF59E0B)
    val Error = Color(0xFFDC2626)
    val ErrorSubtle = Color(0xFFFEF2F2)
}

/** Spacing scale on a 4 dp grid. */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** Corner radii. Kept modest so the UI doesn't look bubbly. */
object Radius {
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
}

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private val AppColorScheme = lightColorScheme(
    primary = AppColors.Primary,
    onPrimary = Color.White,
    primaryContainer = AppColors.BlueSubtle,
    onPrimaryContainer = AppColors.PrimaryDark,
    inversePrimary = Color(0xFFB4C8FF),
    secondary = AppColors.Teal,
    onSecondary = Color.White,
    secondaryContainer = AppColors.TealSubtle,
    onSecondaryContainer = AppColors.TealDark,
    tertiary = AppColors.Teal,
    onTertiary = Color.White,
    tertiaryContainer = AppColors.TealSubtle,
    onTertiaryContainer = AppColors.TealDark,
    background = AppColors.Background,
    onBackground = AppColors.Text,
    surface = AppColors.Background,
    onSurface = AppColors.Text,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = AppColors.TextSecondary,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = AppColors.Card,
    surfaceContainerLow = AppColors.Card,
    surfaceContainer = AppColors.Card,
    surfaceContainerHigh = AppColors.Card,
    surfaceContainerHighest = AppColors.Card,
    surfaceBright = AppColors.Card,
    surfaceDim = Color(0xFFE5EAF1),
    inverseSurface = AppColors.Text,
    inverseOnSurface = Color.White,
    outline = Color(0xFFCBD5E1),
    outlineVariant = AppColors.Border,
    error = AppColors.Error,
    onError = Color.White,
    errorContainer = AppColors.ErrorSubtle,
    onErrorContainer = Color(0xFF991B1B),
    scrim = Color(0xFF0F172A),
)

private fun style(size: Int, line: Int, weight: FontWeight, spacing: Float = 0f) = TextStyle(
    fontFamily = Inter,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
)

private val AppTypography = Typography(
    displaySmall = style(32, 40, FontWeight.Bold, -0.5f),
    headlineMedium = style(26, 34, FontWeight.Bold, -0.4f),
    headlineSmall = style(22, 30, FontWeight.Bold, -0.3f),
    titleLarge = style(20, 28, FontWeight.SemiBold, -0.2f),
    titleMedium = style(16, 24, FontWeight.SemiBold),
    titleSmall = style(14, 20, FontWeight.SemiBold),
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(14, 20, FontWeight.Normal),
    bodySmall = style(12, 18, FontWeight.Normal),
    labelLarge = style(14, 20, FontWeight.SemiBold),
    labelMedium = style(12, 16, FontWeight.Medium),
    labelSmall = style(11, 16, FontWeight.Medium, 0.1f),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(Radius.s),
    medium = RoundedCornerShape(Radius.m),
    large = RoundedCornerShape(Radius.l),
    extraLarge = RoundedCornerShape(20.dp),
)

/** The app always uses its light brand theme. */
@Composable
fun ScanStackTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
