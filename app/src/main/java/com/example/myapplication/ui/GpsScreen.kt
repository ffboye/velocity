package com.example.myapplication.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.tracking.*
import java.util.Locale
import kotlin.math.*

@Composable
internal fun GpsScreen(
    state: DriveState,
    modifier: Modifier,
    onPermission: () -> Unit,
    onLocationSettings: () -> Unit,
    onAppSettings: () -> Unit,
    onRetry: () -> Unit,
    onTracking: (Boolean) -> Unit = {},
) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    val satellites = if (state.gnssFresh) state.gnss.satellites else emptyList()
    LazyColumn(
        modifier.padding(horizontal = 22.dp).testTag("gps-content"),
        contentPadding = PaddingValues(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Header("GPS connectivity", "Location quality and satellite reception.", state) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Velocity tracking", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Turning it off pauses your journey. Resume manually when ready.",
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    state.trackingEnabled,
                    onCheckedChange = onTracking,
                    modifier =
                        Modifier.semantics { contentDescription = "Velocity location tracking" },
                )
            }
        }
        item {
            GlassCard {
                Eyebrow("LOCATION STATUS")
                Text(
                    when {
                        !state.trackingEnabled -> "Tracking is off"
                        !state.permission -> "Precise location needed"
                        !state.gpsEnabled -> "Device location is off"
                        state.locationQuality == LocationQuality.WEAK -> "Weak location signal"
                        state.hasSignal -> "Ready for your journey"
                        state.searchAccuracy != null -> "Improving accuracy"
                        else -> "Finding your location"
                    },
                    fontSize = 23.sp,
                    fontWeight = FontWeight.SemiBold,
                    color =
                        if (state.locationQuality == LocationQuality.WEAK) Amber
                        else if (state.hasSignal) Green else MaterialTheme.colorScheme.onSurface,
                    modifier =
                        Modifier.padding(top = 10.dp).semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                )
                Text(
                    when {
                        !state.trackingEnabled ->
                            "Velocity isn't requesting location. Android's device-wide location setting is unchanged."
                        !state.permission ->
                            "Allow precise location while using Velocity to measure speed and see satellite details."
                        !state.gpsEnabled ->
                            "Turn on location in Android, then return here. Velocity will reconnect automatically."
                        state.locationQuality == LocationQuality.WEAK ->
                            "Location is less accurate or getting old. Move to a clearer view of the sky. Only fresh fixes within 35 m are used for recording."
                        state.hasSignal ->
                            "Your phone is providing a fresh location for live speed and journey recording."
                        else ->
                            state.locationProblem
                                ?: "Move outdoors with a clear view of the sky. Buildings and indoor spaces can weaken satellite signals."
                    },
                    color = Muted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
                HorizontalDivider(Modifier.padding(vertical = 18.dp), color = Border)
                Row(Modifier.fillMaxWidth()) {
                    Stat(
                        "ACCURACY",
                        if (state.hasSignal) "±${state.fix!!.accuracy.roundToInt()}"
                        else state.searchAccuracy?.let { "±${it.roundToInt()}" } ?: "—",
                        "metres",
                        Modifier.weight(1f),
                    )
                    Stat(
                        "LAST FIX",
                        state.fixAgeMillis?.let { if (it < 1000) "<1" else (it / 1000).toString() }
                            ?: "—",
                        "seconds ago",
                        Modifier.weight(1f),
                    )
                }
                FlowRow(
                    Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    when {
                        !state.trackingEnabled ->
                            Button(onClick = { onTracking(true) }) { Text("Turn tracking on") }
                        !state.permission -> {
                            Button(onPermission) { Text("Allow precise location") }
                            TextButton(onAppSettings) { Text("App permissions") }
                        }
                        !state.gpsEnabled -> Button(onLocationSettings) { Text("Turn on location") }
                        else -> {
                            OutlinedButton(onRetry) { Text("Retry GPS") }
                            TextButton(onLocationSettings) { Text("Location settings") }
                        }
                    }
                }
            }
        }
        item {
            GlassCard {
                Eyebrow("SATELLITE RECEIVER")
                Row(Modifier.fillMaxWidth().padding(top = 18.dp)) {
                    Stat(
                        "IN VIEW",
                        if (state.gnssFresh) satellites.size.toString() else "—",
                        "unique satellites",
                        Modifier.weight(1f).semantics(mergeDescendants = true) {
                            contentDescription =
                                "Satellites in view: ${if (state.gnssFresh) satellites.size.toString() else "unavailable"}"
                        },
                    )
                    Stat(
                        "USED IN FIX",
                        if (state.gnssFresh) state.gnss.usedCount.toString() else "—",
                        "receiver's latest fix",
                        Modifier.weight(1f).semantics(mergeDescendants = true) {
                            contentDescription =
                                "Satellites used in fix: ${if (state.gnssFresh) state.gnss.usedCount.toString() else "unavailable"}"
                        },
                    )
                }
                Text(
                    when {
                        !state.permission ->
                            "Satellite details require precise location permission."
                        !state.gpsEnabled ->
                            "Satellite reception resumes when device location is on."
                        state.gnssFresh && satellites.isNotEmpty() ->
                            "Live receiver data · ${satellites.map { it.system }.distinct().joinToString(" · ")}"
                        state.gnssFresh ->
                            "The receiver currently reports no satellites. Try a clearer view of the sky."
                        state.gnss.status == ReceiverStatus.UNAVAILABLE ->
                            "This device isn't reporting satellite details. Fused location can still work."
                        state.gnss.updatedAt != null ->
                            "Satellite data is out of date. Waiting for a new receiver report."
                        else ->
                            "Waiting for the phone's satellite report. A fused location may be available before satellite details arrive."
                    },
                    fontSize = 13.sp,
                    color = Muted,
                    modifier = Modifier.padding(top = 18.dp),
                )
                if (satellites.isNotEmpty()) {
                    SkyView(satellites, Modifier.fillMaxWidth().height(240.dp).padding(top = 18.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Text("● Used in fix", color = Green, fontSize = 12.sp)
                        Spacer(Modifier.width(18.dp))
                        Text("○ In view", color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
        if (satellites.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Satellite signals",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "${satellites.size} satellites · higher bars mean stronger signals",
                            color = Muted,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            items(if (showAll) satellites else satellites.take(8), key = { it.key }) { satellite ->
                SatelliteSignal(satellite)
            }
            if (satellites.size > 8)
                item {
                    OutlinedButton(
                        onClick = { showAll = !showAll },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (showAll) "Show strongest 8"
                            else "Show all ${satellites.size} satellites"
                        )
                    }
                }
            item {
                Text(
                    "C/N₀ is signal strength relative to noise, shown on a 0–60 dB-Hz scale. Green means the satellite was used in the receiver's latest fix. Signal strength alone doesn't determine location accuracy.",
                    color = Muted,
                    fontSize = 12.sp,
                )
            }
        }
        item {
            GlassCard {
                Eyebrow("HOW VELOCITY FINDS YOU")
                DetailRow(
                    "Location source",
                    if (state.hasSignal) state.fix!!.source.label else "Waiting for a fresh fix",
                )
                DetailRow("Precise permission", if (state.permission) "Allowed" else "Not allowed")
                DetailRow("Device location", if (state.gpsEnabled) "On" else "Off")
                if (state.gnssFresh)
                    state.gnss.strongest?.let {
                        DetailRow("Strongest satellite", "${it.roundToInt()} dB-Hz")
                    }
                state.gnss.firstFixMillis?.let {
                    DetailRow(
                        "Time to first satellite fix",
                        String.format(Locale.getDefault(), "%.1f s", it / 1000.0),
                    )
                }
                Text(
                    "Android can combine GPS, Wi-Fi, mobile networks and sensors. It doesn't report each source's contribution to Velocity. Satellite details describe the receiver, while accuracy describes the location used by the app.",
                    color = Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 14.dp),
                )
                TextButton(onLocationSettings) { Text("Manage location services") }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, color = Muted, fontSize = 13.sp)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SatelliteSignal(satellite: Satellite) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Panel,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, Border),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp).semantics(mergeDescendants = true) {}) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(satellite.label, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text(
                        if (satellite.usedInFix) "Used in fix" else "In view",
                        color = if (satellite.usedInFix) Green else Muted,
                        fontSize = 12.sp,
                    )
                }
                Text(
                    "${satellite.cn0.roundToInt()} dB-Hz",
                    color = if (satellite.usedInFix) Green else Muted,
                    fontSize = 14.sp,
                )
            }
            LinearProgressIndicator(
                progress = { (satellite.cn0 / 60f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(6.dp),
                color = if (satellite.usedInFix) Green else Pink,
                trackColor = Border,
            )
        }
    }
}

@Composable
private fun SkyView(satellites: List<Satellite>, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.fillMaxHeight().aspectRatio(1f).padding(20.dp).semantics {
                contentDescription =
                    "Satellite sky view, north up. ${satellites.size} in view, ${satellites.count { it.usedInFix }} used in the receiver's fix. Full readings follow below."
            }
        ) {
            val radius = size.minDimension / 2
            for (fraction in listOf(1f, 2f / 3f, 1f / 3f)) drawCircle(
                Border,
                radius * fraction,
                style = Stroke(1.dp.toPx()),
            )
            drawLine(Border, Offset(center.x, 0f), Offset(center.x, size.height), 1.dp.toPx())
            drawLine(Border, Offset(0f, center.y), Offset(size.width, center.y), 1.dp.toPx())
            satellites
                .filter { it.elevation >= 0f }
                .forEach {
                    val angle = Math.toRadians(it.azimuth.toDouble())
                    val distance = radius * (1 - it.elevation / 90f)
                    val point =
                        center +
                            Offset(
                                (sin(angle) * distance).toFloat(),
                                (-cos(angle) * distance).toFloat(),
                            )
                    drawCircle(
                        if (it.usedInFix) Green.copy(alpha = .14f) else Pink.copy(alpha = .12f),
                        9.dp.toPx(),
                        point,
                    )
                    drawCircle(
                        if (it.usedInFix) Green else Muted,
                        4.dp.toPx(),
                        point,
                        style =
                            if (it.usedInFix) androidx.compose.ui.graphics.drawscope.Fill
                            else Stroke(1.5.dp.toPx()),
                    )
                }
        }
        Text("N", color = Muted, fontSize = 11.sp, modifier = Modifier.align(Alignment.TopCenter))
        Text(
            "S",
            color = Muted,
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        Text(
            "W",
            color = Muted,
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp),
        )
        Text(
            "E",
            color = Muted,
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp),
        )
    }
}
