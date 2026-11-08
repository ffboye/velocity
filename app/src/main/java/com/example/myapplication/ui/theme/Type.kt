package com.example.myapplication.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum",
    )

val Typography =
    Typography(
        displayLarge = style(64, 72, FontWeight.Light),
        displayMedium = style(48, 56, FontWeight.Light),
        displaySmall = style(36, 44, FontWeight.Medium),
        headlineLarge = style(30, 38, FontWeight.SemiBold),
        headlineMedium = style(26, 34, FontWeight.SemiBold),
        headlineSmall = style(23, 30, FontWeight.Medium),
        titleLarge = style(20, 28, FontWeight.Medium),
        titleMedium = style(16, 24, FontWeight.Medium),
        titleSmall = style(14, 20, FontWeight.Medium),
        bodyLarge = style(16, 24),
        bodyMedium = style(14, 21),
        bodySmall = style(12, 18),
        labelLarge = style(14, 20, FontWeight.Medium),
        labelMedium = style(12, 18, FontWeight.Medium),
        labelSmall = style(11, 16, FontWeight.Medium),
    )
