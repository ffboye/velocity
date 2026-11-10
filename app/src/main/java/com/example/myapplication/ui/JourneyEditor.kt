package com.example.myapplication.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun JourneyEditor(
    title: String,
    initialName: String,
    initialNotes: String,
    action: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var notes by rememberSaveable { mutableStateOf(initialNotes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    name,
                    { name = it.take(100) },
                    label = { Text("Journey name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("${name.length}/100") },
                )
                OutlinedTextField(
                    notes,
                    { notes = it.take(2000) },
                    label = { Text("Notes (optional)") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("${notes.length}/2000") },
                )
            }
        },
        confirmButton = { AppTextButton(onClick = { onSave(name, notes) }) { Text(action) } },
        dismissButton = { AppTextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
