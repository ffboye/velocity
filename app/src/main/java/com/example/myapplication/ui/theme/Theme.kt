package com.example.myapplication.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme =
    darkColorScheme(
        primary = VelocityColors.Accent,
        onPrimary = androidx.compose.ui.graphics.Color(0xFF13243F),
        secondary = VelocityColors.Accent,
        background = VelocityColors.Background,
        onBackground = VelocityColors.Text,
        surface = VelocityColors.Surface,
        onSurface = VelocityColors.Text,
        onSurfaceVariant = VelocityColors.Muted,
        outline = VelocityColors.Outline,
        surfaceVariant = VelocityColors.Raised,
        primaryContainer = VelocityColors.Raised,
        onPrimaryContainer = VelocityColors.Accent,
        secondaryContainer = VelocityColors.Raised,
        onSecondaryContainer = VelocityColors.Accent,
        surfaceContainer = VelocityColors.Surface,
        surfaceContainerHigh = VelocityColors.Raised,
        surfaceContainerHighest = VelocityColors.Raised,
        surfaceTint = VelocityColors.Accent,
    )

private val LightColorScheme =
    lightColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF426CB1),
        secondary = androidx.compose.ui.graphics.Color(0xFF536782),
        background = androidx.compose.ui.graphics.Color(0xFFF6F8FC),
        surface = androidx.compose.ui.graphics.Color.White,
        onBackground = androidx.compose.ui.graphics.Color(0xFF17202D),
        onSurface = androidx.compose.ui.graphics.Color(0xFF17202D),
        onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF536175),
    )

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

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
