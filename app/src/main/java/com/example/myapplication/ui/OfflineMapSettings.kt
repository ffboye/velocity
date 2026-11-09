package com.example.myapplication.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.myapplication.maps.*
import com.example.myapplication.tracking.*
import com.example.myapplication.ui.theme.VelocityColors
import java.util.Locale
import org.maplibre.android.geometry.LatLng

private val MapAreaSaver =
    Saver<MapArea?, List<Double>>(
        save = { area ->
            area?.let { listOf(it.north, it.east, it.south, it.west) } ?: emptyList()
        },
        restore = { values ->
            if (values.size == 4) MapArea(values[0], values[1], values[2], values[3]) else null
        },
    )

@Composable
internal fun OfflineMapSettings(state: DriveState, maps: MapState, controller: OfflineMaps) {
    var help by rememberSaveable { mutableStateOf(false) }
    var picker by rememberSaveable { mutableStateOf(false) }
    var focusArea by remember { mutableStateOf<MapArea?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    GlassCard(Modifier.testTag("offline-map-settings")) {
        Text(
            "Offline maps",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "Journeys record without internet. Download an area to see its streets offline.",
            color = Muted,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        PreferenceRow(
            "Save maps offline",
            "",
            maps.enabled,
            controller::enable,
            "Enable offline map downloads",
        )
        AnimatedVisibility(visible = maps.enabled) {
            Column {
                HorizontalDivider(color = Border.copy(alpha = 0.55f))
                PreferenceRow(
                    "Wi-Fi downloads only",
                    "",
                    maps.wifiOnly,
                    controller::wifiOnly,
                    "Download maps over Wi-Fi only",
                )
                OutlinedButton(
                    onClick = {
                        focusArea = null
                        picker = true
                    },
                    enabled = !maps.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (maps.loading) "Loading maps…" else "Choose area to download")
                }
            }
        }
        TextButton(onClick = { help = !help }) {
            Text(if (help) "Hide offline guide" else "How offline maps work")
        }
        AnimatedVisibility(visible = help) {
            Text(
                "1. Enable offline maps, then choose an area. Pan and zoom until the area is small enough to download.\n\n" +
                    "2. Tap Download area and keep Velocity open until it says Available offline. Wi-Fi is required unless you turn Wi-Fi downloads only off.\n\n" +
                    "3. Open a journey. Its route uses downloaded streets automatically, even without a connection. GPS still needs location permission and device location turned on.\n\n" +
                    "Downloads pause when you leave the app or lose the allowed connection. After reopening Velocity, tap Resume. Turning downloads off keeps saved areas usable. Delete an area to free its storage; your journeys stay saved.\n\n" +
                    "Outside downloaded areas, streets need internet. Very close zoom detail may also need a connection. Journey backups include routes, not downloaded street maps.",
                color = Muted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (maps.areas.isNotEmpty()) {
            HorizontalDivider(
                Modifier.padding(vertical = 12.dp),
                color = Border.copy(alpha = 0.55f),
            )
            Text("Downloaded areas", style = MaterialTheme.typography.titleSmall)
            Text(
                "${maps.areas.size} ${if (maps.areas.size == 1) "area" else "areas"} · about ${mb(maps.areas.sumOf { it.bytes })} MB",
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            maps.areas.forEach { saved ->
                Column(
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(saved.name, style = MaterialTheme.typography.titleSmall)
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
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = {
                                focusArea = saved.area
                                picker = true
                            }
                        ) {
                            Text("View area")
                        }
                        if (!saved.complete)
                            TextButton(
                                onClick = {
                                    if (saved.downloading) controller.pause(saved.id)
                                    else controller.resume(saved.id)
                                },
                                enabled = maps.enabled && !maps.loading,
                            ) {
                                Text(if (saved.downloading) "Pause download" else "Resume")
                            }
                        TextButton(onClick = { deleteId = saved.id }) { Text("Delete map") }
                    }
                }
            }
        }
        maps.message?.let {
            Text(
                it,
                color = Amber,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
    if (picker) {
        OfflineAreaPicker(state, maps, focusArea, { picker = false }) { area, name ->
            controller.download(area, name)
            picker = false
        }
    }
    deleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Delete downloaded map?") },
            text = {
                Text(
                    "Only these downloaded streets are removed. Your journeys and routes stay saved."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        controller.delete(id)
                        deleteId = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Keep map") } },
        )
    }
}

@Composable
private fun OfflineAreaPicker(
    state: DriveState,
    maps: MapState,
    focusArea: MapArea?,
    onDismiss: () -> Unit,
    onDownload: (MapArea, String) -> Unit,
) {
    var area by rememberSaveable(stateSaver = MapAreaSaver) { mutableStateOf<MapArea?>(null) }
    // Restore a rotated selection once; camera-idle updates must not refit the map repeatedly.
    val initialArea = remember { area ?: focusArea }
    var name by rememberSaveable { mutableStateOf("") }
    var resetArea by remember { mutableStateOf(false) }
    val location =
        state.fix?.let { LatLng(it.latitude, it.longitude) }
            ?: state.journeys
                .firstOrNull { it.points.isNotEmpty() }
                ?.points
                ?.lastOrNull()
                ?.let { LatLng(it.latitude, it.longitude) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            Modifier.fillMaxSize().systemBarsPadding(),
            color = VelocityColors.Background,
            contentColor = VelocityColors.Text,
        ) {
            Column(
                Modifier.fillMaxSize().imePadding().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Choose offline area",
                        modifier = Modifier.weight(1f).semantics { heading() },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    TextButton(onDismiss) { Text("Done") }
                }
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Pan and zoom to frame the streets you want to keep.",
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    RouteMap(
                        emptyList(),
                        Modifier.fillMaxWidth().height(340.dp).clip(RoundedCornerShape(18.dp)),
                        focus = location,
                        focusArea = if (resetArea) null else initialArea,
                        cameraKey = "offline-area",
                        onArea = { area = it },
                        onFit = { resetArea = true },
                        fitLabel = "Reset view",
                        description =
                            "Street map for choosing an offline download. Pan or use the zoom controls to choose an area.",
                    )
                    if (maps.enabled) {
                        OutlinedTextField(
                            name,
                            { name = it.take(60) },
                            label = { Text("Area name (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val candidate = area
                        Text(
                            if (candidate?.downloadable == true)
                                "About ${mb(candidate.estimatedBytes)} MB · ${mb(candidate.estimatedBytes + 50_000_000)} MB free needed"
                            else "Zoom in to choose a smaller area · 40 MB limit",
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(
                            onClick = { candidate?.let { onDownload(it, name) } },
                            enabled = candidate?.downloadable == true && !maps.loading,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Download area")
                        }
                    }
                }
            }
        }
    }
}

internal fun mb(bytes: Long) = String.format(Locale.getDefault(), "%.1f", bytes / 1_000_000.0)
