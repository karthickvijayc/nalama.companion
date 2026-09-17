package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = NalamaDarkPrimary,
    onPrimary = Color(0xFF0F172A),
    primaryContainer = NalamaDarkPrimaryContainer,
    onPrimaryContainer = Color(0xFFDCFCE7),
    secondary = NalamaDarkSecondary,
    onSecondary = Color(0xFF082F49),
    secondaryContainer = NalamaDarkSecondaryContainer,
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = NalamaDarkTertiary,
    onTertiary = Color(0xFF042F2E),
    background = NalamaDarkBackground,
    onBackground = Color(0xFFF1F5F9),
    surface = NalamaDarkSurface,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = NalamaDarkSurfaceVariant,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = NalamaDarkOutline
)

private val LightColorScheme = lightColorScheme(
    primary = NalamaPrimary,
    onPrimary = Color.White,
    primaryContainer = NalamaPrimaryContainer,
    onPrimaryContainer = NalamaOnPrimaryContainer,
    secondary = NalamaSecondary,
    onSecondary = Color.White,
    secondaryContainer = NalamaSecondaryContainer,
    onSecondaryContainer = NalamaOnSecondaryContainer,
    tertiary = NalamaTertiary,
    onTertiary = Color.White,
    background = NalamaBackground,
    onBackground = Color(0xFF0F172A),
    surface = NalamaSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = NalamaSurfaceVariant,
    onSurfaceVariant = Color(0xFF475569),
    outline = NalamaOutline
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

@Composable
fun HealthConnectSyncTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MyApplicationTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
}

@Composable
fun NalamaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MyApplicationTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
}
