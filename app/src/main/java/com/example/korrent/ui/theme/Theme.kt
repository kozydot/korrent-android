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

// Define the Dark Color Scheme using colors from Korrent1337x CSS
private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary, // Button background
    onPrimary = DarkOnPrimary, // Button text
    background = DarkBackground, // Main background
    onBackground = DarkOnBackground, // Main text
    surface = DarkSurface, // Input/Card backgrounds
    onSurface = DarkOnSurface, // Input/Card text
    surfaceVariant = DarkSurface, // Use surface color for variants like dropdowns
    onSurfaceVariant = DarkOnSurfaceVariant, // Text on variants (e.g., labels)
    outline = DarkOutline, // Borders
    // Define other colors as needed, potentially mapping secondary/tertiary if applicable
    secondary = DarkPrimary, // Re-use primary for secondary elements for simplicity
    onSecondary = DarkOnPrimary,
    tertiary = DarkPrimary, // Re-use primary for tertiary elements
    onTertiary = DarkOnPrimary,
    error = Pink80, // Keep default error colors for now
    onError = Pink40
    // Note: Disabled colors are handled by Compose components automatically based on alpha,
    // but can be customized further in component themes if needed.
)

// Define the Light Color Scheme using Material 3 baseline
private val LightColorScheme = lightColorScheme(
    primary = Purple40, // Keep default light theme for now
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun KorrentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
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
            window.statusBarColor = colorScheme.primary.toArgb() // Or customize status bar color
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme // Use light icons on dark status bar
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography, // Assumes Typography.kt exists (standard Compose setup)
        content = content
    )
}