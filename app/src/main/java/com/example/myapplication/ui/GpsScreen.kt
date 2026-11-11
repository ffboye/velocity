package com.example.myapplication.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
        modifier.padding(horizontal = 20.dp).testTag("gps-content"),
        contentPadding = PaddingValues(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(AppSpace.Section),
    ) {
        item { Header("GPS connectivity") }
        item {
            GlassCard {
                PreferenceRow(
                    "Velocity tracking",
                    "Off pauses your journey. Resume manually.",
                    state.trackingEnabled,
                    onTracking,
                    "Velocity location tracking",
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
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                if (!state.hasSignal || state.locationQuality == LocationQuality.WEAK)
                    Text(
                        when {
                            !state.trackingEnabled -> "Journey tracking is paused."
                            !state.permission -> "Allow precise location."
                            !state.gpsEnabled -> "Turn on device location."
                            state.locationQuality == LocationQuality.WEAK ->
                                "Move to a clearer view of the sky."
                            else ->
                                state.locationProblem
                                    ?: "Try outdoors with a clear view of the sky."
                        },
                        color = Muted,
                        fontSize = 14.sp,
                    )
                HorizontalDivider(color = Border)
                StatsRow {
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
                ActionRow {
                    when {
                        !state.trackingEnabled ->
                            AppButton(onClick = { onTracking(true) }) { Text("Turn tracking on") }
                        !state.permission -> {
                            AppButton(onPermission) { Text("Allow precise location") }
                            AppTextButton(onAppSettings) { Text("App permissions") }
                        }
                        !state.gpsEnabled ->
                            AppButton(onLocationSettings) { Text("Turn on location") }
                        else -> {
                            AppOutlinedButton(onRetry) { Text("Retry GPS") }
                            AppTextButton(onLocationSettings) { Text("Location settings") }
                        }
                    }
                }
            }
        }
        item {
            GlassCard {
                Eyebrow("SATELLITE RECEIVER")
                StatsRow {
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
                        !state.permission -> "Precise location required"
                        !state.gpsEnabled -> "Device location is off"
                        state.gnssFresh && satellites.isNotEmpty() ->
                            "Live receiver data · ${satellites.map { it.system }.distinct().joinToString(" · ")}"
                        state.gnssFresh -> "No satellites reported"
                        state.gnss.status == ReceiverStatus.UNAVAILABLE ->
                            "Satellite details unavailable"
                        state.gnss.updatedAt != null -> "Satellite report expired"
                        else -> "Waiting for satellite report"
                    },
                    fontSize = 14.sp,
                    color = Muted,
                )
                if (satellites.isNotEmpty()) {
                    SkyView(satellites, Modifier.fillMaxWidth().height(240.dp))
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(AppSpace.Group, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(AppSpace.Related),
                    ) {
                        Text("● Used in fix", color = Green, fontSize = 14.sp)
                        Text("○ In view", color = Muted, fontSize = 14.sp)
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
                        Text("${satellites.size} satellites", color = Muted, fontSize = 14.sp)
                    }
                }
            }
            items(if (showAll) satellites else satellites.take(8), key = { it.key }) { satellite ->
                SatelliteSignal(satellite)
            }
            if (satellites.size > 8)
                item {
                    AppOutlinedButton(
                        onClick = { showAll = !showAll },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (showAll) "Show strongest 8"
                            else "Show all ${satellites.size} satellites"
                        )
                    }
                }
        }
        item {
            GlassCard {
                Eyebrow("LOCATION DETAILS")
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
                AppTextButton(onLocationSettings) { Text("Manage location services") }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, color = Muted, fontSize = 14.sp)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SatelliteSignal(satellite: Satellite) {
    val strength by
        animateFloatAsState(
            (satellite.cn0 / 60f).coerceIn(0f, 1f),
            tween(320),
            label = "Satellite signal strength",
        )
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Panel,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, Border),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp).semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(AppSpace.Group),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpace.Group),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(satellite.label, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text(
                        if (satellite.usedInFix) "Used in fix" else "In view",
                        color = if (satellite.usedInFix) Green else Muted,
                        fontSize = 14.sp,
                    )
                }
                Text(
                    "${satellite.cn0.roundToInt()} dB-Hz",
                    color = if (satellite.usedInFix) Green else Muted,
                    fontSize = 14.sp,
                )
            }
            LinearProgressIndicator(
                progress = { strength },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = if (satellite.usedInFix) Green else Pink,
                trackColor = Border,
            )
        }
    }
}

@Composable
private fun SkyView(satellites: List<Satellite>, modifier: Modifier) {
    val skyBorder = Border
    val skyGreen = Green
    val skyAccent = Pink
    val skyMuted = Muted
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.fillMaxHeight().aspectRatio(1f).padding(20.dp).semantics {
                contentDescription =
                    "Satellite sky view, north up. ${satellites.size} in view, ${satellites.count { it.usedInFix }} used in the receiver's fix. Full readings follow below."
            }
        ) {
            val radius = size.minDimension / 2
            for (fraction in listOf(1f, 2f / 3f, 1f / 3f)) drawCircle(
                skyBorder,
                radius * fraction,
                style = Stroke(1.dp.toPx()),
            )
            drawLine(skyBorder, Offset(center.x, 0f), Offset(center.x, size.height), 1.dp.toPx())
            drawLine(skyBorder, Offset(0f, center.y), Offset(size.width, center.y), 1.dp.toPx())
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
                        if (it.usedInFix) skyGreen.copy(alpha = .14f)
                        else skyAccent.copy(alpha = .12f),
                        9.dp.toPx(),
                        point,
                    )
                    drawCircle(
                        if (it.usedInFix) skyGreen else skyMuted,
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
