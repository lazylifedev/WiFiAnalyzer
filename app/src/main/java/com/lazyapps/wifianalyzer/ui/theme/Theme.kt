package com.lazyapps.wifianalyzer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun WifiAnalyzerTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    accent: AccentColor = AccentColor.BLUE,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val primary = if (darkTheme) accent.darkSeed() else accent.lightSeed()
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = primary,
            onPrimary = Navy950,
            primaryContainer = primary.copy(alpha = .20f).compositeOver(Navy800),
            onPrimaryContainer = primary,
            background = Navy950,
            onBackground = Slate200,
            surface = Navy900,
            onSurface = Slate200,
            surfaceVariant = Navy800,
            onSurfaceVariant = Color(0xFFB7C4D8),
            outline = Slate700,
            error = Color(0xFFFFB4AB),
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = primary.copy(alpha = .12f).compositeOver(Color(0xFFF7F9FE)),
            onPrimaryContainer = primary,
            background = Color(0xFFF7F9FE),
            onBackground = Color(0xFF121A27),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF121A27),
            surfaceVariant = Color(0xFFEDF1F8),
            onSurfaceVariant = Color(0xFF495568),
            outline = Color(0xFFBEC7D6),
            error = Color(0xFFBA1A1A),
        )
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            WindowCompat.getInsetsController(view.context.findActivity().window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
}

private tailrec fun android.content.Context.findActivity(): android.app.Activity =
    when (this) {
        is android.app.Activity -> this
        is android.content.ContextWrapper -> baseContext.findActivity()
        else -> error("Activity context required")
    }
