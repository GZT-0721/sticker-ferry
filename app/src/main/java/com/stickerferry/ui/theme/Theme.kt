package com.stickerferry.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** 品牌色 */
val QqBlue = Color(0xFF12B7F5)
val QqBlueDeep = Color(0xFF0B8FC4)
val WxGreen = Color(0xFF07C160)
val WxGreenDeep = Color(0xFF059A4C)
val Accent = Color(0xFF5B6CFF)

/** 语义色 */
val WarnAmber = Color(0xFFF5A524)
val DangerRed = Color(0xFFE5484D)
val OkGreen = Color(0xFF22A06B)

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = WxGreen,
    tertiary = QqBlue,
    background = Color(0xFFF2F5FB),
    surface = Color.White,
    onBackground = Color(0xFF1A2233),
    onSurface = Color(0xFF1A2233),
    error = DangerRed,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8B96FF),
    onPrimary = Color(0xFF0F1420),
    secondary = WxGreen,
    tertiary = QqBlue,
    background = Color(0xFF12161F),
    surface = Color(0xFF1B2029),
    onBackground = Color(0xFFE8EDF7),
    onSurface = Color(0xFFE8EDF7),
    error = DangerRed,
)

@Composable
fun StickerFerryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}
