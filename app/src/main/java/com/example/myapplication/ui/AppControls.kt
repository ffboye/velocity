package com.example.myapplication.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.LocalEasyReading

@Composable
internal fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(14.dp),
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier =
            modifier
                .heightIn(min = if (LocalEasyReading.current) 72.dp else 64.dp)
                .widthIn(min = 80.dp),
        enabled = enabled,
        shape = shape,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        content = {
            ProvideTextStyle(LocalTextStyle.current.copy(textAlign = TextAlign.Center)) {
                content()
            }
        },
    )
}

@Composable
internal fun AppOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(14.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier =
            modifier
                .heightIn(min = if (LocalEasyReading.current) 72.dp else 64.dp)
                .widthIn(min = 80.dp),
        enabled = enabled,
        shape = shape,
        contentPadding = contentPadding,
        border =
            BorderStroke(
                1.5.dp,
                if (enabled) MaterialTheme.colorScheme.outline
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            ),
        content = {
            ProvideTextStyle(LocalTextStyle.current.copy(textAlign = TextAlign.Center)) {
                content()
            }
        },
    )
}

@Composable
internal fun AppTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier =
            modifier
                .heightIn(min = if (LocalEasyReading.current) 64.dp else 56.dp)
                .widthIn(min = 72.dp),
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        content = {
            ProvideTextStyle(LocalTextStyle.current.copy(textAlign = TextAlign.Center)) {
                content()
            }
        },
    )
}
