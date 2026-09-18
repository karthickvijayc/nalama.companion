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
    onPrimary = NalamaDarkOnPrimary,
    primaryContainer = NalamaDarkPrimaryContainer,
    onPrimaryContainer = NalamaDarkOnPrimaryContainer,
    secondary = NalamaDarkSecondary,
    onSecondary = NalamaDarkOnSecondary,
    secondaryContainer = NalamaDarkSecondaryContainer,
    onSecondaryContainer = NalamaDarkOnSecondaryContainer,
    tertiary = NalamaDarkTertiary,
    onTertiary = NalamaDarkOnTertiary,
    tertiaryContainer = NalamaDarkTertiaryContainer,
    onTertiaryContainer = NalamaDarkOnTertiaryContainer,
    background = NalamaDarkBackground,
    onBackground = NalamaDarkOnSurface,
    surface = NalamaDarkSurface,
    onSurface = NalamaDarkOnSurface,
    surfaceVariant = NalamaDarkSurfaceVariant,
    onSurfaceVariant = NalamaDarkOnSurfaceVariant,
    outline = NalamaDarkOutline,
    outlineVariant = NalamaDarkOutlineVariant
)

private val LightColorScheme = lightColorScheme(
    primary = NalamaPrimary,
    onPrimary = NalamaOnPrimary,
    primaryContainer = NalamaPrimaryContainer,
    onPrimaryContainer = NalamaOnPrimaryContainer,
    secondary = NalamaSecondary,
    onSecondary = NalamaOnSecondary,
    secondaryContainer = NalamaSecondaryContainer,
    onSecondaryContainer = NalamaOnSecondaryContainer,
    tertiary = NalamaTertiary,
    onTertiary = NalamaOnTertiary,
    tertiaryContainer = NalamaTertiaryContainer,
    onTertiaryContainer = NalamaOnTertiaryContainer,
    background = NalamaBackground,
    onBackground = NalamaOnSurface,
    surface = NalamaSurface,
    onSurface = NalamaOnSurface,
    surfaceVariant = NalamaSurfaceVariant,
    onSurfaceVariant = NalamaOnSurfaceVariant,
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
