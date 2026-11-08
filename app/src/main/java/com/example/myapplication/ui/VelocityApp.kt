package com.example.myapplication.ui

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
import com.example.myapplication.tracking.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

private val Pink = Color(0xFFFF65B0)
private val Muted = Color(0xFF998E9F)
private val Green = Color(0xFF75E6B1)
private val Amber = Color(0xFFFFBF78)
private val Panel = Color(0xFF1B1722)
private val Border = Color(0xFF342938)

@Composable
fun VelocityApp(
    state: DriveState,
    page: Int,
    onPage: (Int) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onFinish: () -> Unit,
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
) {
    val snackbar = remember { SnackbarHostState() }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var finishDialog by rememberSaveable { mutableStateOf(false) }
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
        containerColor = Color(0xFF100D14),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column(Modifier.background(Color(0xFF17131C))) {
                if (page == 0)
                    Box(Modifier.padding(horizontal = 22.dp, vertical = 12.dp)) {
                        if (state.recording == Recording.IDLE) {
                            GradientButton(
                                if (state.loading) "Loading journeys…" else "Start journey",
                                onStart,
                                enabled = !state.loading && state.storageReady,
                            )
                        } else
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(
                                    onClick = onPause,
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
                NavigationBar(containerColor = Color(0xFF17131C), tonalElevation = 0.dp) {
                    listOf("Speedometer", "Journeys", "Settings").forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = page == index,
                            onClick = { onPage(index) },
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
        when (page) {
            1 -> JourneysScreen(state, content) { selected = it.id }
            2 ->
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
                )
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
            )
        }
    if (finishDialog)
        AlertDialog(
            onDismissRequest = { finishDialog = false },
            title = { Text("Finish this journey?") },
            text = {
                Text("Your distance, time, speed statistics and route will be saved to Journeys.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        finishDialog = false
                        onFinish()
                    }
                ) {
                    Text("Save journey")
                }
            },
            dismissButton = {
                TextButton(onClick = { finishDialog = false }) { Text("Keep recording") }
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
) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "V E L O C I T Y",
                    color = Pink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Make every journey count.",
                    fontSize = 12.sp,
                    color = Muted,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
            SignalPill(state)
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
                    else -> "YOUR PACE. YOUR JOURNEY."
                }
            )
            SpeedDial(state)
            val detail =
                when {
                    !state.permission -> "Ready when you are"
                    !state.gpsEnabled -> "GPS is turned off"
                    !state.hasSignal ->
                        if (state.searchAccuracy != null) "Improving location accuracy…"
                        else "Finding your GPS signal…"
                    state.speedLimit > 0 && state.speed > state.speedLimit ->
                        "Above your speed reminder"
                    else -> "Live GPS speed · ±${state.fix?.accuracy?.roundToInt() ?: 0} m"
                }
            Text(
                detail,
                color =
                    if (state.speedLimit > 0 && state.speed > state.speedLimit) Amber else Muted,
                fontSize = 12.sp,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (state.permission && state.gpsEnabled && !state.hasSignal)
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
                Row {
                    TextButton(onClick = onRetryGps) { Text("Retry GPS") }
                    TextButton(onClick = onGps) { Text("Location settings") }
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
                    "TRIP TIME",
                    duration(state.durationMillis),
                    "active time",
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
            if (state.recording == Recording.IDLE) "No accounts. No cloud. Just the road ahead."
            else "Recording continues with your screen off. Pausing excludes time and distance.",
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
                drawCircle(
                    Brush.radialGradient(listOf(accent.copy(alpha = 0.07f), Color.Transparent)),
                    radius * 1.2f,
                    center,
                )
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
                drawCircle(accent.copy(alpha = 0.16f), 12.dp.toPx(), dot)
                drawCircle(accent, 4.dp.toPx(), dot)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (state.hasSignal) speed.roundToInt().toString() else "0",
                    fontSize = 88.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.Monospace,
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
                    fontSize = 9.sp,
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
    LazyColumn(
        modifier.padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 20.dp),
    ) {
        item { Header("Your journeys", "A little history of everywhere you've been.") }
        if (state.journeys.isEmpty())
            item {
                GlassCard {
                    Spacer(Modifier.height(22.dp))
                    NavGlyph(1, true, Modifier.size(38.dp).align(Alignment.CenterHorizontally))
                    Text(
                        if (state.loading) "Loading journeys…" else "The road is waiting.",
                        fontSize = 21.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 20.dp),
                    )
                    Text(
                        "Start a journey from the speedometer. Finish it to save your stats and route here.",
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
                        Text(date(journey.startedAt), fontWeight = FontWeight.SemiBold)
                        Text(
                            if (journey.interrupted) "Recovered recording"
                            else time(journey.startedAt),
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
        Header("Make it yours", "The essentials, exactly how you like them.")
        GlassCard {
            Eyebrow("SPEED & DISTANCE")
            Text("Measurement units", modifier = Modifier.padding(top = 14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                "Location powers your live speedometer. Your journeys stay on this device unless you export a route or create a backup. GitHub is contacted only for app updates.",
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
) {
    var delete by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Panel)
                .border(1.dp, Border, RoundedCornerShape(28.dp))
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(date(journey.startedAt), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Text(time(journey.startedAt), color = Muted)
            RouteTrace(journey.points, Modifier.fillMaxWidth().height(210.dp))
            Row {
                Stat(
                    "DISTANCE",
                    decimal(journey.distanceMeters / unit.distanceDivisor),
                    unit.distanceLabel,
                    Modifier.weight(1f),
                )
                Stat(
                    "ACTIVE TIME",
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
                TextButton(onClick = { delete = true }) { Text("Delete journey", color = Amber) }
                TextButton(onClick = onDismiss) { Text("Done") }
            }
        }
    }
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
            modifier.background(Color(0xFF15121B), RoundedCornerShape(16.dp)),
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
        modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF15121B)).semantics {
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
            Pink.copy(alpha = 0.12f),
            style = Stroke(12.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
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
private fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF241B2B), Panel, Color(0xFF19151F))))
            .border(1.dp, Border, RoundedCornerShape(24.dp))
            .padding(20.dp),
        content = content,
    )
}

@Composable
private fun GradientButton(label: String, onClick: () -> Unit, enabled: Boolean) {
    Button(
        onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
    ) {
        Box(
            Modifier.fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        if (enabled) listOf(Color(0xFFFF6DB5), Color(0xFFC86AEF))
                        else listOf(Border, Border)
                    )
                )
                .padding(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = if (enabled) Color(0xFF240D24) else Muted,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
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
private fun Eyebrow(text: String) {
    Text(
        text,
        color = Muted,
        fontSize = 9.sp,
        letterSpacing = 1.5.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun Header(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("V E L O C I T Y", color = Pink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(
            title,
            fontSize = 29.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        Text(subtitle, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun SignalPill(state: DriveState) {
    val live = state.hasSignal
    Row(
        Modifier.background((if (live) Green else Muted).copy(alpha = 0.08f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(5.dp).background(if (live) Green else Muted, CircleShape))
        Text(
            if (live) "GPS LIVE" else "GPS",
            color = if (live) Green else Muted,
            fontSize = 9.sp,
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
