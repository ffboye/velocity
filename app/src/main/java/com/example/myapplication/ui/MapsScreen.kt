package com.example.myapplication.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.maps.*
import com.example.myapplication.tracking.*
import java.util.Locale
import org.maplibre.android.geometry.LatLng

@Composable
internal fun MapsScreen(
    state: DriveState,
    maps: MapState,
    controller: OfflineMaps,
    selectedId: String?,
    onJourney: (String?) -> Unit,
    modifier: Modifier,
) {
    var chooser by remember { mutableStateOf(false) }
    var area by remember { mutableStateOf<MapArea?>(null) }
    var focusArea by remember { mutableStateOf<MapArea?>(null) }
    var areaName by rememberSaveable { mutableStateOf("") }
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selected = state.journeys.firstOrNull { it.id == selectedId }
    val points =
        selected?.points
            ?: if (state.recording != Recording.IDLE) state.points
            else state.journeys.firstOrNull()?.points.orEmpty()
    val title =
        selected?.displayName
            ?: if (state.recording != Recording.IDLE) "Current journey"
            else state.journeys.firstOrNull()?.displayName ?: "Explore the map"
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Header("Your map")
        Box {
            OutlinedButton(
                onClick = { chooser = true },
                enabled = state.journeys.isNotEmpty() || state.recording != Recording.IDLE,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text(title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("⌄", Modifier.padding(start = 12.dp))
            }
            DropdownMenu(
                expanded = chooser,
                onDismissRequest = { chooser = false },
                modifier = Modifier.heightIn(max = 320.dp),
            ) {
                if (state.recording != Recording.IDLE)
                    DropdownMenuItem(
                        text = { Text("Current journey") },
                        onClick = {
                            onJourney(null)
                            focusArea = null
                            chooser = false
                        },
                    )
                state.journeys.forEach { journey ->
                    DropdownMenuItem(
                        text = { Text(journey.displayName) },
                        onClick = {
                            onJourney(journey.id)
                            focusArea = null
                            chooser = false
                        },
                    )
                }
            }
        }
        RouteMap(
            points,
            Modifier.fillMaxWidth().height(420.dp).clip(RoundedCornerShape(18.dp)),
            focus = state.fix?.let { LatLng(it.latitude, it.longitude) },
            focusArea = focusArea,
            cameraKey = selected?.id ?: "current",
            onArea = { area = it },
            onFit = { focusArea = null },
        )
        HorizontalDivider(color = Border)
        PreferenceRow(
            "Offline maps",
            "",
            maps.enabled,
            controller::enable,
            "Enable offline map downloads",
        )
        if (maps.enabled) {
            PreferenceRow(
                "Download over Wi-Fi only",
                "",
                maps.wifiOnly,
                controller::wifiOnly,
                "Download maps over Wi-Fi only",
            )
            OutlinedTextField(
                areaName,
                { areaName = it.take(60) },
                label = { Text("Area name (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            val candidate = area
            Text(
                if (candidate?.downloadable == true)
                    "~${mb(candidate.estimatedBytes)} MB · ${mb(candidate.estimatedBytes + 50_000_000)} MB free required"
                else "Zoom in · 40 MB maximum per area",
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = { candidate?.let { controller.download(it, areaName) } },
                enabled = candidate?.downloadable == true && !maps.loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (maps.loading) "Preparing maps…" else "Download visible area")
            }
            maps.areas.forEach { saved ->
                Surface(
                    color = Panel,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(saved.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${mb(saved.bytes)} MB · ${if (saved.complete) "Available offline" else if (saved.downloading) "Downloading ${(saved.progress * 100).toInt()}%" else saved.message ?: "Paused"}",
                            color = if (saved.complete) Green else Muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (!saved.complete)
                            LinearProgressIndicator(
                                progress = { saved.progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        FlowRow {
                            TextButton(onClick = { focusArea = saved.area }) { Text("View area") }
                            if (!saved.complete)
                                TextButton(
                                    onClick = {
                                        if (saved.downloading) controller.pause(saved.id)
                                        else controller.resume(saved.id)
                                    }
                                ) {
                                    Text(if (saved.downloading) "Pause download" else "Resume")
                                }
                            TextButton(onClick = { deleteId = saved.id }) { Text("Delete map") }
                        }
                    }
                }
            }
        } else if (maps.areas.isNotEmpty())
            Text(
                "Downloaded areas kept. Enable offline maps to manage them.",
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
            )
        maps.message?.let { Text(it, color = Amber, style = MaterialTheme.typography.bodyMedium) }
    }
    deleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Delete downloaded map?") },
            text = {
                Text("Only the downloaded map area is removed. Your journeys and routes are kept.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        controller.delete(id)
                        deleteId = null
                    }
                ) {
                    Text("Delete map")
                }
            },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Keep map") } },
        )
    }
}

internal fun mb(bytes: Long) = String.format(Locale.getDefault(), "%.1f", bytes / 1_000_000.0)
