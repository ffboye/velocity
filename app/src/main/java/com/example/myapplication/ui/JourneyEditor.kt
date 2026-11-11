package com.example.myapplication.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myapplication.tracking.JourneyTags

@Composable
internal fun JourneyEditor(
    title: String,
    initialName: String,
    initialNotes: String,
    action: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    initialTags: List<String> = emptyList(),
    onSaveWithTags: ((String, String, List<String>) -> Unit)? = null,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var notes by rememberSaveable { mutableStateOf(initialNotes) }
    var tags by rememberSaveable { mutableStateOf(ArrayList(initialTags)) }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    name,
                    { name = it.take(100) },
                    label = { Text("Name (optional)") },
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
                Text("Tags", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    (JourneyTags.suggestions + initialTags).distinct().forEach { tag ->
                        FilterChip(
                            selected = tag in tags,
                            onClick = {
                                tags =
                                    ArrayList(if (tag in tags) tags - tag else (tags + tag).take(5))
                            },
                            label = { Text(tag) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            AppTextButton(
                onClick = {
                    if (onSaveWithTags != null) onSaveWithTags(name, notes, tags)
                    else onSave(name, notes)
                }
            ) {
                Text(action)
            }
        },
        dismissButton = { AppTextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
