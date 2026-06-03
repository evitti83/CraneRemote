package com.craneremote.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// ─── Colors ───────────────────────────────────────────────────────────────────

val Primary        = Color(0xFFFF5722)
val PrimaryDark    = Color(0xFFE64A19)
val Secondary      = Color(0xFF607D8B)
val Background     = Color(0xFF0A0A0A)
val Surface        = Color(0xFF1A1A1A)
val SurfaceVariant = Color(0xFF242424)
val Connected      = Color(0xFF4CAF50)
val Disconnected   = Color(0xFFF44336)
val Warning        = Color(0xFFFF9800)
val Info           = Color(0xFF2196F3)

// Cores semânticas de operação
val EmergencyRed   = Color(0xFFD32F2F)
val MoveBlue       = Color(0xFF1565C0)
val HoistGreen     = Color(0xFF2E7D32)
val ActiveAmber    = Color(0xFFF57F17)
val JoystickBg     = Color(0xFF1E1E1E)
val JoystickTrack  = Color(0xFF333333)
val JoystickBtn    = Color(0xFF37474F)
val JoystickActive = Color(0xFF00BCD4)

// ─── Typography ───────────────────────────────────────────────────────────────

val AppTypography = Typography(
    headlineLarge  = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,     fontSize = 28.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    headlineSmall  = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleLarge     = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,   fontSize = 18.sp),
    titleMedium    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,   fontSize = 14.sp),
    bodyLarge      = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,   fontSize = 14.sp),
    bodyMedium     = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,   fontSize = 12.sp),
    bodySmall      = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,   fontSize = 11.sp),
    labelLarge     = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,   fontSize = 12.sp),
    labelMedium    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,   fontSize = 11.sp),
    labelSmall     = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,   fontSize = 10.sp)
)

// ─── Theme ────────────────────────────────────────────────────────────────────

private val DarkColors = darkColorScheme(
    primary        = Primary,
    onPrimary      = Color.White,
    secondary      = Secondary,
    onSecondary    = Color.White,
    background     = Background,
    onBackground   = Color.White,
    surface        = Surface,
    onSurface      = Color.White,
    surfaceVariant = SurfaceVariant,
    error          = Disconnected
)

@Composable
fun CraneRemoteTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(colorScheme = DarkColors, typography = AppTypography, content = content)
}
