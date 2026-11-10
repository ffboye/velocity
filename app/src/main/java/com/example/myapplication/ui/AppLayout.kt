package com.example.myapplication.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

internal object AppSpace {
    val Page = 20.dp
    val Section = 24.dp
    val Group = 12.dp
    val Related = 8.dp
}

internal val LocalStatLabelLines = staticCompositionLocalOf { 1 }

@Composable
internal fun ActionRow(
    modifier: Modifier = Modifier,
    content: @Composable FlowRowScope.() -> Unit,
) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpace.Related),
        verticalArrangement = Arrangement.spacedBy(AppSpace.Related),
        content = content,
    )
}

@Composable
internal fun StatsRow(modifier: Modifier = Modifier, content: @Composable FlowRowScope.() -> Unit) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = if (maxWidth < 220.dp || LocalDensity.current.fontScale > 1.3f) 1 else 2
        CompositionLocalProvider(
            LocalStatLabelLines provides if (columns == 2 && maxWidth < 300.dp) 2 else 1
        ) {
            FlowRow(
                Modifier.fillMaxWidth(),
                maxItemsInEachRow = columns,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
        }
    }
}
