package com.example.myapplication.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import com.example.myapplication.BuildConfig
import com.example.myapplication.maps.*
import com.example.myapplication.tracking.*
import com.example.myapplication.ui.theme.VelocityColors
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

internal val Pink = VelocityColors.Accent
internal val Muted = VelocityColors.Muted
internal val Green = VelocityColors.Positive
internal val Amber = VelocityColors.Warning
internal val Panel = VelocityColors.Surface
internal val Border = VelocityColors.Outline

@Composable
fun VelocityApp(
    state: DriveState,
    page: Int,
    onPage: (Int) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onFinish: (String, String) -> Unit,
    onEnableLocation: () -> Unit,
    onGpsSettings: () -> Unit,
    onAppSettings: () -> Unit,
    onUnit: (SpeedUnit) -> Unit,
    onAwake: (Boolean) -> Unit,
    onLimit: (Double) -> Unit,
    onDelete: (String) -> Unit,
    onExport: (Journey) -> Unit,
    onDismissMessage: () -> Unit,
    onRetrySave: () -> Unit,
    onRetryGps: () -> Unit = {},
    updates: @Composable () -> Unit = {},
    onBackup: () -> Unit = {},
    onRestore: () -> Unit = {},
    onEditJourney: (String, String, String) -> Unit = { _, _, _ -> },
    onTracking: (Boolean) -> Unit = {},
    mapState: MapState = MapState(),
    mapController: OfflineMaps? = null,
) {
    val snackbar = remember { SnackbarHostState() }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var finishDialog by rememberSaveable { mutableStateOf(false) }
    var mapJourney by rememberSaveable { mutableStateOf<String?>(null) }
    val pageState = rememberSaveableStateHolder()
    BackHandler(enabled = selected != null || finishDialog || page != 0) {
        when {
            finishDialog -> finishDialog = false
            selected != null -> selected = null
            else -> onPage(0)
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let { message ->
            val retry = message.startsWith("Couldn't save")
            val result =
                snackbar.showSnackbar(
                    message,
                    if (retry) "Retry save" else null,
                    withDismissAction = true,
                )
            onDismissMessage()
            if (result == SnackbarResult.ActionPerformed && retry) onRetrySave()
        }
    }
    Scaffold(
        containerColor = VelocityColors.Background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (state.saveFailed || !state.storageReady)
                Surface(color = Panel, contentColor = Amber) {
                    Row(
                        Modifier.statusBarsPadding()
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (state.saveFailed)
                                "Journey changes haven't been saved. Free some space and retry."
                            else "Journey storage couldn't be read. Recording is unavailable.",
                            Modifier.weight(1f),
                            fontSize = 13.sp,
                        )
                        if (state.saveFailed) TextButton(onRetrySave) { Text("Retry save") }
                    }
                }
        },
        bottomBar = {
            Column(Modifier.background(VelocityColors.Navigation)) {
                if (state.recording != Recording.IDLE)
                    FlowRow(
                        Modifier.fillMaxWidth()
                            .clickable { onPage(0) }
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Text(
                            if (state.recording == Recording.PAUSED) "Journey paused"
                            else "Recording journey",
                            color = if (state.recording == Recording.PAUSED) Amber else Green,
                            fontSize = 12.sp,
                        )
                        Text(
                            "${decimal(state.distanceMeters / state.unit.distanceDivisor)} ${state.unit.distanceLabel} · ${duration(state.durationMillis)}",
                            color = Muted,
                            fontSize = 12.sp,
                        )
                    }
                if (page == 0 || state.recording != Recording.IDLE)
                    Box(Modifier.padding(horizontal = 22.dp, vertical = 12.dp)) {
                        if (state.recording == Recording.IDLE) {
                            PrimaryButton(
                                if (state.loading) "Loading journeys…" else "Start journey",
                                onStart,
                                enabled =
                                    !state.loading && state.storageReady && state.trackingEnabled,
                            )
                        } else
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(
                                    onClick = onPause,
                                    enabled =
                                        state.recording != Recording.PAUSED ||
                                            state.trackingEnabled,
                                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                                    shape = RoundedCornerShape(18.dp),
                                ) {
                                    Text(
                                        if (state.recording == Recording.PAUSED) "Resume"
                                        else "Pause"
                                    )
                                }
                                Button(
                                    onClick = { finishDialog = true },
                                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                                    shape = RoundedCornerShape(18.dp),
                                ) {
                                    Text("Finish & save")
                                }
                            }
                    }
                NavigationBar(containerColor = VelocityColors.Navigation, tonalElevation = 0.dp) {
                    listOf("Speed", "Journeys", "Map", "GPS", "Settings").forEachIndexed {
                        index,
                        label ->
                        NavigationBarItem(
                            selected = page == index,
                            onClick = {
                                selected = null
                                onPage(index)
                            },
                            icon = { NavGlyph(index, page == index) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors =
                                NavigationBarItemDefaults.colors(
                                    selectedIconColor = Pink,
                                    selectedTextColor = Pink,
                                    indicatorColor = Pink.copy(alpha = 0.12f),
                                    unselectedIconColor = Muted,
                                    unselectedTextColor = Muted,
                                ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        val content = Modifier.fillMaxSize().padding(padding)
        pageState.SaveableStateProvider(page) {
            when (page) {
                1 -> JourneysScreen(state, content) { selected = it.id }
                2 ->
                    if (mapController != null)
                        MapsScreen(
                            state,
                            mapState,
                            mapController,
                            mapJourney,
                            { mapJourney = it },
                            content,
                        )
                3 ->
                    GpsScreen(
                        state,
                        content,
                        onEnableLocation,
                        onGpsSettings,
                        onAppSettings,
                        onRetryGps,
                        onTracking,
                    )
                4 ->
                    SettingsScreen(
                        state,
                        content,
                        onUnit,
                        onAwake,
                        onLimit,
                        onAppSettings,
                        updates,
                        onBackup,
                        onRestore,
                    )
                else ->
                    Dashboard(
                        state,
                        content,
                        onEnableLocation,
                        onGpsSettings,
                        onAppSettings,
                        onRetryGps,
                        onConnectivity = { onPage(3) },
                    )
            }
        }
    }
    state.journeys
        .firstOrNull { it.id == selected }
        ?.let { journey ->
            JourneyDetails(
                journey,
                state.unit,
                onDismiss = { selected = null },
                onDelete = {
                    onDelete(journey.id)
                    selected = null
                },
                onExport = { onExport(journey) },
                onEdit = { name, notes -> onEditJourney(journey.id, name, notes) },
                onMap = {
                    mapJourney = journey.id
                    selected = null
                    onPage(2)
                },
            )
        }
    if (finishDialog)
        JourneyEditor(
            "Finish this journey?",
            "",
            "",
            "Save journey",
            { finishDialog = false },
            { name, notes ->
                finishDialog = false
                onFinish(name, notes)
            },
        )
}

@Composable
private fun Dashboard(
    state: DriveState,
    modifier: Modifier,
    onLocation: () -> Unit,
    onGps: () -> Unit,
    onAppSettings: () -> Unit,
    onRetryGps: () -> Unit,
    onConnectivity: () -> Unit,
) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        Header("Speedometer", "Live speed and recorded distance.", state, onConnectivity)
        if (!state.trackingEnabled)
            GlassCard {
                Text("Tracking is off", style = MaterialTheme.typography.titleMedium)
                Text("Turn tracking on in GPS when you're ready to record.", color = Muted)
                TextButton(onConnectivity) { Text("Open GPS controls") }
            }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Spacer(Modifier.height(6.dp))
            Eyebrow(
                when (state.recording) {
                    Recording.RECORDING -> "JOURNEY IN PROGRESS"
                    Recording.PAUSED -> "JOURNEY PAUSED"
                    else -> "READY TO RECORD"
                }
            )
            SpeedDial(state)
            val detail =
                when {
                    !state.trackingEnabled -> "Tracking is off"
                    !state.permission -> "Precise location needed"
                    !state.gpsEnabled -> "GPS is turned off"
                    !state.hasSignal ->
                        if (state.searchAccuracy != null) "Improving location accuracy…"
                        else "Finding your GPS signal…"
                    state.speedLimit > 0 && state.speed > state.speedLimit ->
                        "Above your speed reminder"
                    else ->
                        "${state.locationQuality.label} signal · ±${state.fix?.accuracy?.roundToInt() ?: 0} m"
                }
            Text(
                detail,
                color =
                    if (state.speedLimit > 0 && state.speed > state.speedLimit) Amber else Muted,
                fontSize = 12.sp,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (state.trackingEnabled && state.permission && state.gpsEnabled && !state.hasSignal)
            GlassCard {
                Text("Waiting for accurate location", fontWeight = FontWeight.SemiBold)
                Text(
                    state.locationProblem
                        ?: state.searchAccuracy?.let {
                            "Location found · ±${it.roundToInt()} m. Waiting for an accuracy of 35 m or better before measuring speed."
                        }
                        ?: "No fresh location yet. Try outdoors with a clear view of the sky.",
                    color = Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
                FlowRow {
                    TextButton(onClick = onRetryGps) { Text("Retry GPS") }
                    TextButton(onClick = onConnectivity) { Text("View GPS status") }
                }
            }
        GlassCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat(
                    "DISTANCE",
                    decimal(state.distanceMeters / state.unit.distanceDivisor),
                    state.unit.distanceLabel,
                    Modifier.weight(1f),
                )
                Box(Modifier.height(42.dp).width(1.dp).background(Border))
                Stat(
                    "RECORDED TIME",
                    duration(state.durationMillis),
                    "excludes pauses",
                    Modifier.weight(1f),
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 18.dp), color = Border)
            Row(Modifier.fillMaxWidth()) {
                Stat(
                    "AVERAGE",
                    decimal(state.averageSpeed * state.unit.multiplier, 0),
                    state.unit.label,
                    Modifier.weight(1f),
                )
                Stat(
                    "TOP SPEED",
                    decimal(state.maxSpeed * state.unit.multiplier, 0),
                    state.unit.label,
                    Modifier.weight(1f),
                )
            }
        }
        if (!state.permission)
            GlassCard {
                Text("Let the journey begin.", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text(
                    "Allow precise location to see your real speed and record your route. Your journeys stay on this device.",
                    fontSize = 13.sp,
                    color = Muted,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onLocation) { Text("Enable GPS access") }
                    TextButton(onClick = onAppSettings) { Text("Permissions") }
                }
            }
        else if (!state.gpsEnabled)
            GlassCard {
                Text("Switch on GPS", fontWeight = FontWeight.SemiBold)
                Text(
                    "Device location is off. Turn it on to measure your speed.",
                    color = Muted,
                    fontSize = 13.sp,
                )
                TextButton(onClick = onGps) { Text("Open location settings") }
            }
        if (state.points.isNotEmpty())
            GlassCard {
                Eyebrow("YOUR ROUTE")
                RouteTrace(
                    state.points,
                    Modifier.fillMaxWidth().height(145.dp).padding(top = 12.dp),
                )
                Text("Offline route trace · north up", color = Muted, fontSize = 11.sp)
            }
        Text(
            if (state.recording == Recording.IDLE)
                "Average = distance ÷ recorded time. Manual pauses are excluded; stationary recording time counts."
            else
                "Recording continues with your screen off. Manual pauses exclude time and distance.",
            fontSize = 11.sp,
            color = Muted,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SpeedDial(state: DriveState) {
    val speed = state.speed * state.unit.multiplier
    val maximum = if (state.unit == SpeedUnit.KMH) 200.0 else 120.0
    val fraction by
        animateFloatAsState((speed / maximum).coerceIn(0.0, 1.0).toFloat(), label = "Live speed")
    val accent = if (state.speedLimit > 0 && state.speed > state.speedLimit) Amber else Pink
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val diameter = minOf(maxWidth, 316.dp)
        Box(
            Modifier.size(diameter).semantics(mergeDescendants = true) {
                contentDescription = "Current speed ${speed.roundToInt()} ${state.unit.label}"
            },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize().padding(12.dp)) {
                val inset = 22.dp.toPx()
                val radius = (size.minDimension - inset * 2) / 2
                val center = Offset(size.width / 2, size.height / 2)
                val origin = center - Offset(radius, radius)
                val arcSize = Size(radius * 2, radius * 2)
                drawArc(
                    Color(0xFF322635),
                    135f,
                    270f,
                    false,
                    origin,
                    arcSize,
                    style = Stroke(7.dp.toPx(), cap = StrokeCap.Round),
                )
                if (fraction > 0)
                    drawArc(
                        Brush.sweepGradient(listOf(accent, Color(0xFFB769FF), accent), center),
                        135f,
                        270f * fraction,
                        false,
                        origin,
                        arcSize,
                        style = Stroke(7.dp.toPx(), cap = StrokeCap.Round),
                    )
                for (index in 0..40) {
                    val angle = Math.toRadians(135.0 + index * 6.75)
                    val major = index % 10 == 0
                    val outer = radius + 12.dp.toPx()
                    val inner = outer - (if (major) 8.dp.toPx() else 4.dp.toPx())
                    drawLine(
                        if (index / 40f <= fraction) accent else Color(0xFF55435B),
                        center + Offset(cos(angle).toFloat() * inner, sin(angle).toFloat() * inner),
                        center + Offset(cos(angle).toFloat() * outer, sin(angle).toFloat() * outer),
                        1.5.dp.toPx(),
                    )
                }
                val tip = Math.toRadians(135.0 + 270 * fraction)
                val dot = center + Offset(cos(tip).toFloat() * radius, sin(tip).toFloat() * radius)
                drawCircle(accent, 4.dp.toPx(), dot)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (state.hasSignal) speed.roundToInt().toString() else "0",
                    fontSize = 88.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.Default,
                    letterSpacing = (-5).sp,
                    lineHeight = 100.sp,
                )
                Text(
                    state.unit.label.uppercase(Locale.getDefault()),
                    color = Pink,
                    fontSize = 13.sp,
                    letterSpacing = 3.sp,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    if (state.hasSignal && speed < 1) "STATIONARY"
                    else if (state.hasSignal) "LIVE SPEED" else "WAITING FOR GPS",
                    fontSize = 11.sp,
                    letterSpacing = 1.5.sp,
                    color = Muted,
                )
            }
            Row(
                Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp)
                    .width(diameter * 0.68f),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("0", fontSize = 10.sp, color = Muted)
                Text(maximum.toInt().toString(), fontSize = 10.sp, color = Muted)
            }
        }
    }
}

@Composable
private fun JourneysScreen(state: DriveState, modifier: Modifier, select: (Journey) -> Unit) {
    val summary = JourneyTotals.from(state.journeys, System.currentTimeMillis())
    LazyColumn(
        modifier.padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 20.dp),
    ) {
        item { Header("Your journeys", "Your routes and progress, saved on this device.", state) }
        if (state.journeys.isEmpty())
            item {
                GlassCard {
                    Spacer(Modifier.height(22.dp))
                    NavGlyph(1, true, Modifier.size(38.dp).align(Alignment.CenterHorizontally))
                    Text(
                        if (state.loading) "Loading journeys…" else "No journeys yet",
                        fontSize = 21.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 20.dp),
                    )
                    Text(
                        "Record a journey to keep its route and statistics here.",
                        color = Muted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 14.dp),
                    )
                }
            }
        if (state.journeys.isNotEmpty())
            item {
                GlassCard {
                    Row {
                        Stat(
                            "JOURNEYS",
                            state.journeys.size.toString(),
                            "saved on device",
                            Modifier.weight(1f),
                        )
                        Stat(
                            "TOTAL DISTANCE",
                            decimal(
                                state.journeys.sumOf { it.distanceMeters } /
                                    state.unit.distanceDivisor
                            ),
                            state.unit.distanceLabel,
                            Modifier.weight(1f),
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Border)
                    Row {
                        Stat(
                            "AVERAGE SPEED",
                            decimal(summary.averageSpeed * state.unit.multiplier, 0),
                            state.unit.label,
                            Modifier.weight(1f),
                        )
                        Stat(
                            "TOP SPEED",
                            decimal(summary.maxSpeed * state.unit.multiplier, 0),
                            state.unit.label,
                            Modifier.weight(1f),
                        )
                    }
                    Text(
                        "Last 7 days: ${decimal(summary.recentDistance / state.unit.distanceDivisor)} ${state.unit.distanceLabel} · Recorded time: ${duration(summary.recordedMillis)}",
                        color = Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Text(
                        "Average uses total distance ÷ total recorded time, excluding manual pauses.",
                        color = Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        items(state.journeys, key = { it.id }) { journey ->
            GlassCard(
                Modifier.clickable { select(journey) }
                    .semantics {
                        contentDescription =
                            "Journey ${date(journey.startedAt)}, ${decimal(journey.distanceMeters / state.unit.distanceDivisor)} ${state.unit.distanceLabel}"
                    }
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(journey.displayName, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (journey.interrupted) "Recovered recording"
                            else "${date(journey.startedAt)} · ${time(journey.startedAt)}",
                            color = Muted,
                            fontSize = 12.sp,
                        )
                    }
                    Text("View →", color = Pink, fontSize = 12.sp)
                }
                RouteTrace(
                    journey.points,
                    Modifier.fillMaxWidth().height(105.dp).padding(vertical = 10.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text(
                        "${decimal(journey.distanceMeters / state.unit.distanceDivisor)} ${state.unit.distanceLabel}",
                        color = Pink,
                    )
                    Text(duration(journey.durationMillis), color = Muted)
                    Text(
                        "${decimal(journey.averageSpeed * state.unit.multiplier, 0)} ${state.unit.label} avg",
                        color = Muted,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    state: DriveState,
    modifier: Modifier,
    onUnit: (SpeedUnit) -> Unit,
    onAwake: (Boolean) -> Unit,
    onLimit: (Double) -> Unit,
    onAppSettings: () -> Unit,
    updates: @Composable () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Header("Settings", "Your preferences, privacy and app updates.", state)
        GlassCard {
            Eyebrow("SPEED & DISTANCE")
            Text("Measurement units", modifier = Modifier.padding(top = 14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SpeedUnit.entries.forEach { unit ->
                    FilterChip(
                        selected = state.unit == unit,
                        onClick = { onUnit(unit) },
                        label = { Text(if (unit == SpeedUnit.KMH) "km/h · km" else "mph · mi") },
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Border)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Keep screen awake")
                    Text("While the speedometer is open", fontSize = 12.sp, color = Muted)
                }
                Switch(
                    state.keepAwake,
                    onCheckedChange = onAwake,
                    modifier = Modifier.semantics { contentDescription = "Keep screen awake" },
                )
            }
        }
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Speed reminder")
                    Text("A visual nudge above your limit", fontSize = 12.sp, color = Muted)
                }
                Switch(
                    state.speedLimit > 0,
                    onCheckedChange = { onLimit(if (it) 50.0 / 3.6 else 0.0) },
                    modifier = Modifier.semantics { contentDescription = "Speed reminder" },
                )
            }
            if (state.speedLimit > 0) {
                Text(
                    "${decimal(state.speedLimit * state.unit.multiplier, 0)} ${state.unit.label}",
                    color = Pink,
                    fontSize = 26.sp,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Slider(
                    value = (state.speedLimit * state.unit.multiplier).toFloat(),
                    onValueChange = { onLimit(it / state.unit.multiplier) },
                    valueRange = if (state.unit == SpeedUnit.KMH) 10f..200f else 5f..125f,
                    modifier = Modifier.semantics { contentDescription = "Speed reminder limit" },
                )
            }
        }
        GlassCard {
            Eyebrow("LOCATION & PRIVACY")
            Text(
                if (state.permission) "Precise location enabled"
                else "Precise location not enabled",
                modifier = Modifier.padding(top = 14.dp),
            )
            Text(
                "Journeys and satellite readings stay on this device. OpenFreeMap supplies the map area you view; your route is drawn locally. Android may use Google Location Accuracy. GitHub is used for updates. Velocity never uploads your journeys.",
                color = Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            TextButton(onClick = onAppSettings) { Text("Manage Android permissions") }
        }
        GlassCard {
            Eyebrow("JOURNEY BACKUP")
            Text("Keep your journeys", modifier = Modifier.padding(top = 12.dp))
            Text(
                "Save a private backup with all routes and statistics. Restore it after changing phones or switching from a test build.",
                color = Muted,
                fontSize = 12.sp,
            )
            TextButton(onBackup, enabled = !state.loading && state.recording == Recording.IDLE) {
                Text("Back up journeys")
            }
            TextButton(onRestore, enabled = !state.loading && state.recording == Recording.IDLE) {
                Text("Restore journeys")
            }
            if (state.recording != Recording.IDLE)
                Text(
                    "Finish your journey before backing up or restoring.",
                    color = Muted,
                    fontSize = 11.sp,
                )
        }
        updates()
        Text(
            "VELOCITY  ${BuildConfig.VERSION_NAME}\nBuilt for the journey.",
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 20.sp,
        )
    }
}

@Composable
private fun JourneyDetails(
    journey: Journey,
    unit: SpeedUnit,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onEdit: (String, String) -> Unit,
    onMap: () -> Unit,
) {
    var delete by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = Panel,
            contentColor = VelocityColors.Text,
            border = BorderStroke(1.dp, Border),
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(journey.displayName, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text("${date(journey.startedAt)} · ${time(journey.startedAt)}", color = Muted)
                if (journey.notes.isNotBlank()) Text(journey.notes, fontSize = 14.sp)
                TextButton(onClick = { editing = true }) { Text("Edit name & notes") }
                RouteTrace(journey.points, Modifier.fillMaxWidth().height(210.dp))
                OutlinedButton(
                    onMap,
                    enabled = journey.points.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("View street map")
                }
                Row {
                    Stat(
                        "DISTANCE",
                        decimal(journey.distanceMeters / unit.distanceDivisor),
                        unit.distanceLabel,
                        Modifier.weight(1f),
                    )
                    Stat(
                        "RECORDED TIME",
                        duration(journey.durationMillis),
                        "h:mm:ss",
                        Modifier.weight(1f),
                    )
                }
                Row {
                    Stat(
                        "AVERAGE",
                        decimal(journey.averageSpeed * unit.multiplier, 0),
                        unit.label,
                        Modifier.weight(1f),
                    )
                    Stat(
                        "TOP SPEED",
                        decimal(journey.maxSpeed * unit.multiplier, 0),
                        unit.label,
                        Modifier.weight(1f),
                    )
                }
                Text(
                    "Average = distance ÷ recorded time. Manual pauses are excluded; stationary recording time counts.",
                    color = Muted,
                    fontSize = 12.sp,
                )
                SpeedGraph(journey, unit)
                if (journey.interrupted)
                    Text(
                        "Recovered after recording was interrupted. Includes the last saved checkpoint.",
                        fontSize = 12.sp,
                        color = Amber,
                    )
                Button(
                    onClick = onExport,
                    enabled = journey.points.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Export route as GPX")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { delete = true }) {
                        Text("Delete journey", color = Amber)
                    }
                    TextButton(onClick = onDismiss) { Text("Done") }
                }
            }
        }
    }
    if (editing)
        JourneyEditor(
            "Journey details",
            journey.name,
            journey.notes,
            "Save changes",
            { editing = false },
            { name, notes ->
                onEdit(name, notes)
                editing = false
            },
        )
    if (delete)
        AlertDialog(
            onDismissRequest = { delete = false },
            title = { Text("Delete this journey?") },
            text = {
                Text("Its route and statistics will be permanently removed from this device.")
            },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { delete = false }) { Text("Keep journey") } },
        )
}

@Composable
private fun RouteTrace(points: List<RoutePoint>, modifier: Modifier) {
    if (points.isEmpty())
        Box(
            modifier.background(VelocityColors.Navigation, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("Route appears after a GPS fix", color = Muted, fontSize = 12.sp)
            return
        }
    val positions =
        remember(points) {
            val origin = points.first()
            points.map { point ->
                val longitude = ((point.longitude - origin.longitude + 540) % 360) - 180
                Offset(
                    (longitude * cos(Math.toRadians(origin.latitude))).toFloat(),
                    (-point.latitude + origin.latitude).toFloat(),
                )
            }
        }
    Canvas(
        modifier.clip(RoundedCornerShape(12.dp)).background(VelocityColors.Navigation).semantics {
            contentDescription = "Recorded route trace, north up, ${points.size} GPS points"
        }
    ) {
        val inset = 22.dp.toPx()
        for (i in 1..5) {
            drawLine(
                Border.copy(alpha = 0.35f),
                Offset(size.width * i / 6, 0f),
                Offset(size.width * i / 6, size.height),
            )
            drawLine(
                Border.copy(alpha = 0.35f),
                Offset(0f, size.height * i / 6),
                Offset(size.width, size.height * i / 6),
            )
        }
        val minX = positions.minOf { it.x }
        val maxX = positions.maxOf { it.x }
        val minY = positions.minOf { it.y }
        val maxY = positions.maxOf { it.y }
        val scale =
            min(
                (size.width - inset * 2) / max(maxX - minX, 0.00005f),
                (size.height - inset * 2) / max(maxY - minY, 0.00005f),
            )
        fun project(p: Offset) =
            Offset(
                (p.x - (minX + maxX) / 2) * scale + size.width / 2,
                (p.y - (minY + maxY) / 2) * scale + size.height / 2,
            )
        val path = Path()
        positions.forEachIndexed { index, position ->
            val p = project(position)
            if (index == 0 || points[index].segment != points[index - 1].segment)
                path.moveTo(p.x, p.y)
            else path.lineTo(p.x, p.y)
        }
        drawPath(
            path,
            Pink,
            style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawCircle(Green, 5.dp.toPx(), project(positions.first()))
        drawCircle(Color.White, 4.dp.toPx(), project(positions.last()))
    }
}

@Composable
internal fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Panel,
        contentColor = VelocityColors.Text,
        border = BorderStroke(1.dp, Border),
    ) {
        Column(Modifier.padding(20.dp), content = content)
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit, enabled: Boolean) {
    Button(
        onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
internal fun Stat(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Eyebrow(label)
        Text(
            value,
            fontSize = 25.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(unit, color = Muted, fontSize = 11.sp)
    }
}

@Composable
internal fun Eyebrow(text: String) {
    Text(
        text,
        color = Muted,
        fontSize = 10.sp,
        letterSpacing = 1.5.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
internal fun Header(
    title: String,
    subtitle: String,
    state: DriveState? = null,
    onConnectivity: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("VELOCITY", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            state?.let { SignalPill(it, onConnectivity) }
        }
        Text(
            title,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        Text(subtitle, color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun SignalPill(state: DriveState, onClick: (() -> Unit)? = null) {
    val color =
        when (state.locationQuality) {
            LocationQuality.GOOD -> Green
            LocationQuality.WEAK -> Amber
            else -> Muted
        }
    Row(
        Modifier.background(color.copy(alpha = 0.08f), CircleShape)
            .then(
                if (onClick != null)
                    Modifier.clip(CircleShape).clickable(onClick = onClick).heightIn(min = 48.dp)
                else Modifier
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(5.dp).background(color, CircleShape))
        Text(
            "GPS · ${state.locationQuality.label.uppercase(Locale.getDefault())}",
            color = color,
            fontSize = 10.sp,
            letterSpacing = 0.7.sp,
        )
    }
}

@Composable
private fun NavGlyph(index: Int, selected: Boolean, modifier: Modifier = Modifier) {
    val color = if (selected) Pink else Muted
    Canvas(modifier.size(22.dp)) {
        val stroke = 1.7.dp.toPx()
        when (index) {
            0 -> {
                drawArc(
                    color,
                    140f,
                    260f,
                    false,
                    Offset(size.width * 0.1f, size.height * 0.14f),
                    Size(size.width * 0.8f, size.height * 0.8f),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                drawLine(
                    color,
                    center,
                    Offset(size.width * 0.72f, size.height * 0.3f),
                    stroke,
                    StrokeCap.Round,
                )
                drawCircle(color, stroke, center)
            }
            1 -> {
                val path =
                    Path().apply {
                        moveTo(size.width * .2f, size.height * .8f)
                        cubicTo(
                            size.width * -.1f,
                            size.height * .1f,
                            size.width * 1.1f,
                            size.height * .9f,
                            size.width * .8f,
                            size.height * .2f,
                        )
                    }
                drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round))
                drawCircle(color, stroke * 1.5f, Offset(size.width * .2f, size.height * .8f))
                drawCircle(color, stroke * 1.5f, Offset(size.width * .8f, size.height * .2f))
            }
            2 -> {
                val path =
                    Path().apply {
                        moveTo(size.width * .12f, size.height * .22f)
                        lineTo(size.width * .38f, size.height * .12f)
                        lineTo(size.width * .65f, size.height * .22f)
                        lineTo(size.width * .88f, size.height * .12f)
                        lineTo(size.width * .88f, size.height * .78f)
                        lineTo(size.width * .65f, size.height * .88f)
                        lineTo(size.width * .38f, size.height * .78f)
                        lineTo(size.width * .12f, size.height * .88f)
                        close()
                    }
                drawPath(path, color, style = Stroke(stroke))
                drawLine(
                    color,
                    Offset(size.width * .38f, size.height * .12f),
                    Offset(size.width * .38f, size.height * .78f),
                    stroke,
                )
                drawLine(
                    color,
                    Offset(size.width * .65f, size.height * .22f),
                    Offset(size.width * .65f, size.height * .88f),
                    stroke,
                )
            }
            3 -> {
                drawCircle(color, size.minDimension * .32f, center, style = Stroke(stroke))
                drawCircle(color, size.minDimension * .12f, center, style = Stroke(stroke))
                drawLine(color, Offset(center.x, 0f), Offset(center.x, size.height * .22f), stroke)
                drawLine(
                    color,
                    Offset(center.x, size.height * .78f),
                    Offset(center.x, size.height),
                    stroke,
                )
                drawLine(color, Offset(0f, center.y), Offset(size.width * .22f, center.y), stroke)
                drawLine(
                    color,
                    Offset(size.width * .78f, center.y),
                    Offset(size.width, center.y),
                    stroke,
                )
            }
            else -> {
                for (i in 0..2) {
                    val y = size.height * (0.25f + i * 0.25f)
                    drawLine(
                        color,
                        Offset(size.width * .15f, y),
                        Offset(size.width * .85f, y),
                        stroke,
                        StrokeCap.Round,
                    )
                    drawCircle(
                        Panel,
                        stroke * 2,
                        Offset(size.width * (if (i == 1) .65f else .35f), y),
                    )
                    drawCircle(
                        color,
                        stroke * 1.8f,
                        Offset(size.width * (if (i == 1) .65f else .35f), y),
                        style = Stroke(stroke),
                    )
                }
            }
        }
    }
}

private fun decimal(value: Double, digits: Int = 2) =
    String.format(Locale.getDefault(), "%.${digits}f", value)

private fun duration(millis: Long): String {
    val seconds = millis / 1000
    return String.format(
        Locale.getDefault(),
        "%d:%02d:%02d",
        seconds / 3600,
        seconds / 60 % 60,
        seconds % 60,
    )
}

private fun date(millis: Long) =
    SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date(millis))

private fun time(millis: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
