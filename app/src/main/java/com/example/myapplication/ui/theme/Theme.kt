package com.example.myapplication.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

private val DarkColorScheme =
    darkColorScheme(
        primary = Color(0xFF74BBFF),
        onPrimary = Color(0xFF041626),
        secondary = VelocityColors.Accent,
        background = VelocityColors.Background,
        onBackground = VelocityColors.Text,
        surface = VelocityColors.Surface,
        onSurface = VelocityColors.Text,
        onSurfaceVariant = VelocityColors.Muted,
        outline = VelocityColors.ControlOutline,
        outlineVariant = VelocityColors.Outline,
        surfaceVariant = VelocityColors.Raised,
        primaryContainer = VelocityColors.Raised,
        onPrimaryContainer = VelocityColors.Accent,
        secondaryContainer = VelocityColors.Raised,
        onSecondaryContainer = VelocityColors.Accent,
        surfaceContainer = VelocityColors.Surface,
        surfaceContainerHigh = VelocityColors.Raised,
        surfaceContainerHighest = VelocityColors.Raised,
        surfaceTint = VelocityColors.Accent,
        error = Color(0xFFFF9E9A),
        surfaceContainerLow = VelocityColors.Navigation,
    )

private val LightColorScheme =
    lightColorScheme(
        primary = Color(0xFF064DB2),
        onPrimary = Color.White,
        secondary = Color(0xFF064DB2),
        onSecondary = Color.White,
        background = Color(0xFFFFFDF8),
        onBackground = Color(0xFF101827),
        surface = Color.White,
        onSurface = Color(0xFF101827),
        onSurfaceVariant = Color(0xFF354152),
        outline = Color(0xFF526175),
        outlineVariant = Color(0xFF9CA8B8),
        surfaceVariant = Color(0xFFE6ECF4),
        surfaceContainer = Color.White,
        surfaceContainerLow = Color(0xFFF2F5FA),
        surfaceContainerHigh = Color(0xFFEAF0F8),
        surfaceContainerHighest = Color(0xFFE1E9F5),
        primaryContainer = Color(0xFFDAE8FF),
        onPrimaryContainer = Color(0xFF063A84),
        secondaryContainer = Color(0xFFDAE8FF),
        onSecondaryContainer = Color(0xFF063A84),
        error = Color(0xFFB42318),
        surfaceTint = Color(0xFF064DB2),
    )

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    easyReading: Boolean = false,
    daylight: Boolean = false,
    tapHaptics: Boolean = true,
    tripHaptics: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            daylight -> LightColorScheme
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    val systemDensity = LocalDensity.current
    val readingDensity =
        remember(systemDensity, easyReading) {
            if (easyReading) Density(systemDensity.density, systemDensity.fontScale * 1.15f)
            else systemDensity
        }
    CompositionLocalProvider(
        LocalDensity provides readingDensity,
        LocalEasyReading provides easyReading,
        LocalDaylight provides daylight,
        LocalTapHaptics provides tapHaptics,
        LocalTripHaptics provides tripHaptics,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes =
                Shapes(
                    extraSmall = RoundedCornerShape(8.dp),
                    small = RoundedCornerShape(12.dp),
                    medium = RoundedCornerShape(16.dp),
                    large = RoundedCornerShape(20.dp),
                    extraLarge = RoundedCornerShape(24.dp),
                ),
            content = content,
        )
    }
}

internal val LocalEasyReading = staticCompositionLocalOf { false }
internal val LocalDaylight = staticCompositionLocalOf { false }
internal val LocalTapHaptics = staticCompositionLocalOf { true }
internal val LocalTripHaptics = staticCompositionLocalOf { true }
