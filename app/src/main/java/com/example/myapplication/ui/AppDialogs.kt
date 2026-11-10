package com.example.myapplication.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Dialog windows create fresh Android composition locals; keep the app's reading density. */
@Composable
internal fun AppDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        CompositionLocalProvider(LocalDensity provides density, content = content)
    }
}

@Composable
internal fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
) {
    val density = LocalDensity.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { CompositionLocalProvider(LocalDensity provides density, content = title) },
        text = { CompositionLocalProvider(LocalDensity provides density, content = text) },
        confirmButton = {
            CompositionLocalProvider(LocalDensity provides density, content = confirmButton)
        },
        dismissButton =
            dismissButton?.let { button ->
                { CompositionLocalProvider(LocalDensity provides density, content = button) }
            },
    )
}
