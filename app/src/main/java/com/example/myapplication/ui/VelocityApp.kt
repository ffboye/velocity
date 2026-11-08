package com.example.myapplication.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    onGpsTabVisibility: (Boolean) -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var finishDialog by rememberSaveable { mutableStateOf(false) }
    var mapJourney by rememberSaveable { mutableStateOf<String?>(null) }
    val pageState = rememberSaveableStateHolder()
    // Keep full tab names readable within their fixed-width touch targets at large font sizes.
    val navigationLabelSize = (12f / (LocalDensity.current.fontScale / 1.3f).coerceAtLeast(1f)).sp
    val pageOpacity = remember { Animatable(1f) }
    LaunchedEffect(page) {
        pageOpacity.snapTo(0.65f)
        pageOpacity.animateTo(1f, tween(180))
    }
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
                HorizontalDivider(color = Border.copy(alpha = 0.55f))
                if (state.recording != Recording.IDLE && page != 0)
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
                                    shape = RoundedCornerShape(14.dp),
                                ) {
                                    Text(
                                        if (state.recording == Recording.PAUSED) "Resume"
                                        else "Pause"
                                    )
                                }
                                Button(
                                    onClick = { finishDialog = true },
                                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                                    shape = RoundedCornerShape(14.dp),
                                ) {
                                    Text("Finish & save")
                                }
                            }
                    }
                NavigationBar(containerColor = VelocityColors.Navigation, tonalElevation = 0.dp) {
                    listOf("Speed", "Journeys", "Map", "GPS", "Settings")
                        .withIndex()
                        .filter { it.index != 3 || state.showGpsTab }
                        .forEach { (index, label) ->
                            NavigationBarItem(
                                selected = page == index,
                                onClick = {
                                    selected = null
                                    onPage(index)
                                },
                                icon = { NavGlyph(index, page == index) },
                                label = {
                                    Text(
                                        label,
                                        fontSize = navigationLabelSize,
                                        lineHeight = navigationLabelSize * 1.5f,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                colors =
                                    NavigationBarItemDefaults.colors(
                                        selectedIconColor = Pink,
                                        selectedTextColor = Pink,
                                        indicatorColor = VelocityColors.Raised,
                                        unselectedIconColor = Muted,
                                        unselectedTextColor = Muted,
                                    ),
                            )
                        }
                }
            }
        },
    ) { padding ->
        val content =
            Modifier.fillMaxSize().padding(padding).graphicsLayer { alpha = pageOpacity.value }
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
                        onGpsTabVisibility,
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
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Header("Speedometer", state, onConnectivity)
        if (!state.trackingEnabled)
            GlassCard {
                Text("Tracking is off", style = MaterialTheme.typography.titleMedium)
                Text("Resume tracking in GPS controls.", color = Muted)
                TextButton(onConnectivity) { Text("Open GPS controls") }
            }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Spacer(Modifier.height(6.dp))
            if (state.recording != Recording.IDLE)
                Eyebrow(
                    if (state.recording == Recording.PAUSED) "JOURNEY PAUSED"
                    else "JOURNEY IN PROGRESS"
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
                lineHeight = 18.sp,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
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
                    "h:mm:ss",
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
        if (state.trackingEnabled && state.permission && state.gpsEnabled && !state.hasSignal) {
            state.locationProblem?.let {
                Text(it, color = Amber, style = MaterialTheme.typography.bodySmall)
            }
            FlowRow {
                TextButton(onClick = onRetryGps) { Text("Retry GPS") }
                TextButton(onClick = onConnectivity) { Text("View GPS status") }
            }
        }
        if (!state.permission)
            GlassCard {
                Text(
                    "Location permission needed",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                )
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onLocation) { Text("Enable GPS access") }
                    TextButton(onClick = onAppSettings) { Text("Permissions") }
                }
            }
        else if (!state.gpsEnabled)
            GlassCard {
                Text("Switch on GPS", fontWeight = FontWeight.SemiBold)
                Text("Device location is off.", color = Muted, fontSize = 13.sp)
                TextButton(onClick = onGps) { Text("Open location settings") }
            }
        if (state.points.isNotEmpty())
            GlassCard {
                Eyebrow("YOUR ROUTE")
                RouteTrace(
                    state.points,
                    Modifier.fillMaxWidth().height(145.dp).padding(top = 12.dp),
                )
            }
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
        val diameter = minOf(maxWidth, 272.dp)
        val numberSize = with(LocalDensity.current) { minOf(96.sp.toDp(), diameter / 2.8f).toSp() }
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
                    VelocityColors.Raised,
                    135f,
                    270f,
                    false,
                    origin,
                    arcSize,
                    style = Stroke(4.dp.toPx(), cap = StrokeCap.Round),
                )
                if (fraction > 0)
                    drawArc(
                        accent,
                        135f,
                        270f * fraction,
                        false,
                        origin,
                        arcSize,
                        style = Stroke(4.dp.toPx(), cap = StrokeCap.Round),
                    )
                for (index in 0..20) {
                    val angle = Math.toRadians(135.0 + index * 13.5)
                    val major = index % 5 == 0
                    val outer = radius + 12.dp.toPx()
                    val inner = outer - (if (major) 8.dp.toPx() else 4.dp.toPx())
                    drawLine(
                        if (index / 20f <= fraction && fraction > 0) accent else Border,
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
                    fontSize = numberSize,
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.Default,
                    letterSpacing = (-3).sp,
                    lineHeight = numberSize * 1.1f,
                )
                Text(
                    state.unit.label.uppercase(Locale.getDefault()),
                    color = Pink,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(12.dp))
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
        modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 20.dp),
    ) {
        item { Header("Your journeys") }
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
                }
            }
        if (state.journeys.isNotEmpty())
            item {
                GlassCard {
                    Row {
                        Stat("JOURNEYS", state.journeys.size.toString(), "", Modifier.weight(1f))
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
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
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
    onGpsTabVisibility: (Boolean) -> Unit,
) {
    var privacyDetails by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Header("Settings")
        GlassCard {
            Text("Measurement units", style = MaterialTheme.typography.titleMedium)
            FlowRow(
                Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SpeedUnit.entries.forEach { unit ->
                    FilterChip(
                        selected = state.unit == unit,
                        onClick = { onUnit(unit) },
                        label = { Text(if (unit == SpeedUnit.KMH) "km/h · km" else "mph · mi") },
                    )
                }
            }
        }
        GlassCard {
            Eyebrow("DISPLAY")
            PreferenceRow(
                "Keep screen awake",
                "While the speedometer is open",
                state.keepAwake,
                onAwake,
            )
            HorizontalDivider(color = Border.copy(alpha = 0.55f))
            PreferenceRow("Show GPS tab", "", state.showGpsTab, onGpsTabVisibility)
        }
        GlassCard {
            PreferenceRow(
                "Speed reminder",
                "",
                state.speedLimit > 0,
                { onLimit(if (it) 50.0 / 3.6 else 0.0) },
            )
            if (state.speedLimit > 0) {
                Text(
                    "${decimal(state.speedLimit * state.unit.multiplier, 0)} ${state.unit.label}",
                    color = Pink,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(top = 12.dp),
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
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                "Your journeys stay on this device.",
                color = Muted,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            TextButton(onClick = { privacyDetails = !privacyDetails }) {
                Text(if (privacyDetails) "Hide privacy details" else "Privacy details")
            }
            if (privacyDetails)
                Text(
                    "OpenFreeMap supplies the map area you view; your route is drawn locally. Android may use Google Location Accuracy. GitHub is used for updates. Velocity never uploads your journeys or satellite readings.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            TextButton(onClick = onAppSettings) { Text("Manage Android permissions") }
        }
        GlassCard {
            Text("Journey backup", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onBackup,
                    enabled = !state.loading && state.recording == Recording.IDLE,
                ) {
                    Text("Back up journeys")
                }
                TextButton(
                    onRestore,
                    enabled = !state.loading && state.recording == Recording.IDLE,
                ) {
                    Text("Restore journeys")
                }
            }
            if (state.recording != Recording.IDLE)
                Text(
                    "Finish your journey before backing up or restoring.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
        }
        updates()
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
    var averageDetails by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().systemBarsPadding(),
            shape = RoundedCornerShape(0.dp),
            color = VelocityColors.Background,
            contentColor = VelocityColors.Text,
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        journey.displayName,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    TextButton(onDismiss) { Text("Done") }
                }
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
                TextButton(onClick = { averageDetails = true }) { Text("Average calculation") }
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
                TextButton(onClick = { delete = true }) { Text("Delete journey", color = Amber) }
            }
        }
    }
    if (averageDetails)
        AlertDialog(
            onDismissRequest = { averageDetails = false },
            title = { Text("Average speed") },
            text = {
                Text(
                    "Distance ÷ recorded time. Manual pauses are excluded; stationary recording time counts."
                )
            },
            confirmButton = { TextButton(onClick = { averageDetails = false }) { Text("Done") } },
        )
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
        shape = RoundedCornerShape(18.dp),
        color = Panel,
        contentColor = VelocityColors.Text,
        border = BorderStroke(1.dp, Border.copy(alpha = 0.55f)),
    ) {
        Column(Modifier.padding(20.dp), content = content)
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit, enabled: Boolean) {
    Button(
        onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(label, fontWeight = FontWeight.Medium, fontSize = 16.sp)
    }
}

@Composable
internal fun Stat(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Eyebrow(label)
        Text(
            value,
            fontSize = if (value.length > 7) 21.sp else 26.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (unit.isNotBlank())
            Text(unit, color = Muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun Eyebrow(text: String) {
    Text(
        text,
        color = Muted,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
internal fun Header(
    title: String,
    state: DriveState? = null,
    onConnectivity: (() -> Unit)? = null,
) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title,
            fontSize = 28.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        if (state != null && onConnectivity != null) SignalPill(state, onConnectivity)
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
            lineHeight = 14.sp,
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
