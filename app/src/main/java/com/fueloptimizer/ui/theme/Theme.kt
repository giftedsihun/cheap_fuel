package com.fueloptimizer.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val TossBlue = Color(0xFF1B4F8E)
val TossLightBlue = Color(0xFF4DABF7)
val TossDarkBlue = Color(0xFF0D2C54)
val TossSurface = Color(0xFFFAFBFD)
val TossBackground = Color(0xFFF5F7FA)
val TossGray = Color(0xFF6B7280)
val TossSuccess = Color(0xFF10B981)
val TossWarning = Color(0xFFF59E0B)
val TossError = Color(0xFFEF4444)
val TossSurfaceVariant = Color(0xFFE8EDF5)
val TossOnSurfaceVariant = Color(0xFF6B7280)
val TossOutline = Color(0xFFD1D5DB)

val TossTypography = Typography(
    headlineLarge = TextStyle(
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 40.sp
    ),
    headlineMedium = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 36.sp
    ),
    headlineSmall = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 24.sp
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 16.sp
    ),
)

val LightTossColorScheme = lightColorScheme(
    primary = TossBlue,
    onPrimary = Color.White,
    surface = TossSurface,
    onSurface = TossDarkBlue,
    surfaceVariant = TossSurfaceVariant,
    onSurfaceVariant = TossOnSurfaceVariant,
    outline = TossOutline,
    background = TossBackground,
    error = TossError,
    primaryContainer = TossLightBlue,
    secondary = TossGray,
)

@Composable
fun GasSmartTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightTossColorScheme,
        typography = TossTypography,
        content = content
    )
}