package com.example.myapplication.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.LocalEasyReading

@Composable
internal fun PreferenceRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accessibilityLabel: String = title,
) {
    Row(
        Modifier.fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = accessibilityLabel }
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .heightIn(min = if (LocalEasyReading.current) 80.dp else 72.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle.isNotBlank())
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
