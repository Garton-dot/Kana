package com.garton.kana.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object KanaColors {
    val Sakura = Color(0xFFFF4F81)
    val SakuraDeep = Color(0xFFE0245E)
    val Indigo = Color(0xFF5B5FEF)
    val Gold = Color(0xFFFFB800)
    val Flame = Color(0xFFFF7A00)
    val Correct = Color(0xFF22B573)
    val Wrong = Color(0xFFE5484D)
    val Ice = Color(0xFF4FC3F7)
}

private val Light = lightColorScheme(
    primary = KanaColors.Sakura,
    onPrimary = Color.White,
    secondary = KanaColors.Indigo,
    onSecondary = Color.White,
    tertiary = KanaColors.Gold,
    background = Color(0xFFFFF6F8),
    onBackground = Color(0xFF231A22),
    surface = Color.White,
    onSurface = Color(0xFF231A22),
    surfaceVariant = Color(0xFFF6E8EE),
    onSurfaceVariant = Color(0xFF6F5B66),
)

private val Dark = darkColorScheme(
    primary = KanaColors.Sakura,
    onPrimary = Color.White,
    secondary = Color(0xFF8C8FFF),
    onSecondary = Color(0xFF14121F),
    tertiary = KanaColors.Gold,
    background = Color(0xFF14121F),
    onBackground = Color(0xFFF2ECF4),
    surface = Color(0xFF201C2E),
    onSurface = Color(0xFFF2ECF4),
    surfaceVariant = Color(0xFF2C2740),
    onSurfaceVariant = Color(0xFFB9B0C6),
)

private val Type = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Black, fontSize = 32.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 26.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp),
)

@Composable
fun KanaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, typography = Type, content = content)
}
