package com.example.korrent.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// dark colors from korrent1337x css
private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary, // button background
    onPrimary = DarkOnPrimary, // button text
    background = DarkBackground, // main background
    onBackground = DarkOnBackground, // main text
    surface = DarkSurface, // input/card backgrounds
    onSurface = DarkOnSurface, // input/card text
    surfaceVariant = DarkSurface, // use surface color for variants like dropdowns
    onSurfaceVariant = DarkOnSurfaceVariant, // text on variants (e.g., labels)
    outline = DarkOutline, // borders
    // define others if needed, maybe map secondary/tertiary
    secondary = DarkPrimary, // reuse primary for secondary for simplicity
    onSecondary = DarkOnPrimary,
    tertiary = DarkPrimary, // reuse primary for tertiary
    onTertiary = DarkOnPrimary,
    error = Pink80, // keep default error colors for now
    onError = Pink40
    // note: disabled colors handled by compose alpha, can customize more if needed
)

// light colors using material 3 baseline
private val LightColorScheme = lightColorScheme(
    primary = Purple40, // keep default light theme for now
    secondary = PurpleGrey40,
    tertiary = Pink40

)

@Composable
fun KorrentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // dynamic color available on android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb() // or customize status bar color
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme // use light icons on dark status bar
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography, // assumes typography.kt exists
        content = content
    )
}