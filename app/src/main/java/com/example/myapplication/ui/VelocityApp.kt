package com.example.myapplication.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.DialogProperties
import com.example.myapplication.maps.*
import com.example.myapplication.tracking.*
import com.example.myapplication.ui.theme.VelocityColors
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

internal val Pink: Color
    @Composable get() = MaterialTheme.colorScheme.primary
internal val Muted: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
internal val Green: Color
    @Composable
    get() =
        if (com.example.myapplication.ui.theme.LocalDaylight.current) Color(0xFF006539)
        else VelocityColors.Positive
internal val Amber: Color
    @Composable
    get() =
        if (com.example.myapplication.ui.theme.LocalDaylight.current) Color(0xFF805300)
        else VelocityColors.Warning
internal val Panel: Color
    @Composable get() = MaterialTheme.colorScheme.surface
internal val Border: Color
    @Composable get() = MaterialTheme.colorScheme.outlineVariant

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
    onEasyReading: (Boolean) -> Unit = {},
    onDaylight: (Boolean) -> Unit = {},
    onTapHaptics: (Boolean) -> Unit = {},
    onTripHaptics: (Boolean) -> Unit = {},
    onDistanceChimes: (Boolean) -> Unit = {},
    onEditJourneyTags: (String, String, String, List<String>) -> Unit = { id, name, notes, _ ->
        onEditJourney(id, name, notes)
    },
    onFinishTagged: (String, String, List<String>) -> Unit = { name, notes, _ ->
        onFinish(name, notes)
    },
) {
    val snackbar = remember { SnackbarHostState() }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var finishDialog by rememberSaveable { mutableStateOf(false) }
    val pageState = rememberSaveableStateHolder()
    // Keep full tab names readable within their fixed-width touch targets at large font sizes.
    val navigationLabelSize = (14f / (LocalDensity.current.fontScale / 1.3f).coerceAtLeast(1f)).sp
    LaunchedEffect(page) {
        // Restore old saved navigation state into Journeys after removing the Map tab.
        if (page == 2) onPage(1)
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
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (state.saveFailed || !state.storageReady)
                Surface(color = Panel, contentColor = Amber) {
                    FlowRow(
                        Modifier.statusBarsPadding()
                            .fillMaxWidth()
                            .padding(horizontal = AppSpace.Page, vertical = AppSpace.Related),
                        maxItemsInEachRow = if (LocalDensity.current.fontScale > 1.3f) 1 else 2,
                        horizontalArrangement = Arrangement.spacedBy(AppSpace.Group, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            if (state.saveFailed)
                                "Journey changes haven't been saved. Free some space and retry."
                            else "Journey storage couldn't be read. Recording is unavailable.",
                            Modifier.weight(1f),
                            fontSize = 14.sp,
                        )
                        if (state.saveFailed) AppTextButton(onRetrySave) { Text("Retry save") }
                    }
                }
        },
        bottomBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
                HorizontalDivider(color = Border.copy(alpha = 0.55f))
                if (state.recording != Recording.IDLE && page != 0)
                    FlowRow(
                        Modifier.fillMaxWidth()
                            .clickable { onPage(0) }
                            .heightIn(min = 48.dp)
                            .padding(horizontal = AppSpace.Page, vertical = AppSpace.Related),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            if (state.recording == Recording.PAUSED) "Journey paused"
                            else "Recording journey",
                            color = if (state.recording == Recording.PAUSED) Amber else Green,
                            fontSize = 14.sp,
                        )
                        Text(
                            "${decimal(state.distanceMeters / state.unit.distanceDivisor)} ${state.unit.distanceLabel} · ${duration(state.durationMillis)}",
                            color = Muted,
                            fontSize = 14.sp,
                        )
                    }
                if (page == 0 || state.recording != Recording.IDLE)
                    Box(Modifier.padding(horizontal = AppSpace.Page, vertical = AppSpace.Group)) {
                        if (state.recording == Recording.IDLE) {
                            PrimaryButton(
                                if (state.loading) "Loading journeys…" else "Start journey",
                                onStart,
                                enabled =
                                    !state.loading && state.storageReady && state.trackingEnabled,
                            )
                        } else
                            Row(
                                Modifier.height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                AppOutlinedButton(
                                    onClick = onPause,
                                    enabled =
                                        state.recording != Recording.PAUSED ||
                                            state.trackingEnabled,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    shape = RoundedCornerShape(14.dp),
                                ) {
                                    Text(
                                        if (state.recording == Recording.PAUSED) "Resume"
                                        else "Pause"
                                    )
                                }
                                AppButton(
                                    onClick = { finishDialog = true },
                                    modifier =
                                        Modifier.weight(1f)
                                            .fillMaxHeight()
                                            .recordingHighlight(
                                                state.recording == Recording.RECORDING,
                                                MaterialTheme.colorScheme.onPrimary,
                                            ),
                                    shape = RoundedCornerShape(14.dp),
                                ) {
                                    Text("Finish & save")
                                }
                            }
                    }
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp,
                ) {
                    listOf(0 to "Speed", 1 to "Journeys", 3 to "GPS", 4 to "Settings")
                        .filter { it.first != 3 || state.showGpsTab }
                        .forEach { (index, label) ->
                            NavigationBarItem(
                                selected = page == index,
                                onClick =
                                    clickWithFeedback {
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
                                        indicatorColor =
                                            MaterialTheme.colorScheme.surfaceContainerHighest,
                                        unselectedIconColor = Muted,
                                        unselectedTextColor = Muted,
                                    ),
                            )
                        }
                }
            }
        },
    ) { padding ->
        val content = Modifier.fillMaxSize()
        PageArrival(page, Modifier.fillMaxSize().padding(padding)) {
            pageState.SaveableStateProvider(page) {
                when (page) {
                    1 -> JourneysScreen(state, content) { selected = it.id }
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
                            mapState,
                            mapController,
                            onEasyReading,
                            onDaylight,
                            onTapHaptics,
                            onTripHaptics,
                            onDistanceChimes,
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
                onEdit = { name, notes, tags -> onEditJourneyTags(journey.id, name, notes, tags) },
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
            onSaveWithTags = { name, notes, tags ->
                finishDialog = false
                onFinishTagged(name, notes, tags)
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
        modifier.verticalScroll(rememberScrollState()).padding(AppSpace.Page),
        verticalArrangement = Arrangement.spacedBy(AppSpace.Section),
    ) {
        Header("Speedometer", state, onConnectivity)
        if (!state.trackingEnabled)
            GlassCard {
                Text("Tracking is off", style = MaterialTheme.typography.titleMedium)
                Text("Resume tracking in GPS controls.", color = Muted)
                AppTextButton(onConnectivity) { Text("Open GPS controls") }
            }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpace.Related),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.recording != Recording.IDLE)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpace.Related),
                ) {
                    LiveRecordingDot(state.recording == Recording.RECORDING)
                    Eyebrow(
                        if (state.recording == Recording.PAUSED) "JOURNEY PAUSED"
                        else "JOURNEY IN PROGRESS"
                    )
                }
            SpeedGauge(state)
            QuickStats(state)
        }
        GlassCard {
            StatsRow {
                Stat(
                    "DISTANCE",
                    decimal(state.distanceMeters / state.unit.distanceDivisor),
                    state.unit.distanceLabel,
                    Modifier.weight(1f),
                )
                Stat(
                    "RECORDED TIME",
                    duration(state.durationMillis),
                    "h:mm:ss",
                    Modifier.weight(1f),
                )
            }
            HorizontalDivider(color = Border)
            Text(
                "Average ${decimal(state.averageSpeed * state.unit.multiplier, 0)} ${state.unit.label}",
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (state.trackingEnabled && state.permission && state.gpsEnabled && !state.hasSignal) {
            state.locationProblem?.let {
                Text(it, color = Amber, style = MaterialTheme.typography.bodySmall)
            }
            ActionRow {
                AppTextButton(onClick = onRetryGps) { Text("Retry GPS") }
                AppTextButton(onClick = onConnectivity) { Text("View GPS status") }
            }
        }
        if (!state.permission)
            GlassCard {
                Text(
                    "Location permission needed",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                )
                ActionRow {
                    AppTextButton(onClick = onLocation) { Text("Enable GPS access") }
                    AppTextButton(onClick = onAppSettings) { Text("Permissions") }
                }
            }
        else if (!state.gpsEnabled)
            GlassCard {
                Text("Switch on GPS", fontWeight = FontWeight.SemiBold)
                Text("Device location is off.", color = Muted, fontSize = 14.sp)
                AppTextButton(onClick = onGps) { Text("Open location settings") }
            }
        if (state.points.isNotEmpty())
            GlassCard {
                Eyebrow("YOUR ROUTE")
                RouteTrace(state.points, Modifier.fillMaxWidth().height(145.dp))
            }
    }
}

@Composable
private fun JourneysScreen(state: DriveState, modifier: Modifier, select: (Journey) -> Unit) {
    val summary = JourneyTotals.from(state.journeys, System.currentTimeMillis())
    LazyColumn(
        modifier.padding(horizontal = 20.dp).testTag("journeys-content"),
        verticalArrangement = Arrangement.spacedBy(AppSpace.Section),
        contentPadding = PaddingValues(vertical = 20.dp),
    ) {
        item { Header("Your journeys") }
        if (state.journeys.isEmpty())
            item {
                GlassCard {
                    NavGlyph(1, true, Modifier.size(38.dp).align(Alignment.CenterHorizontally))
                    Text(
                        if (state.loading) "Loading journeys…" else "No journeys yet",
                        fontSize = 21.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }
        if (state.journeys.isNotEmpty()) item { JourneySummary(state, summary) }
        items(state.journeys, key = { it.id }) { journey ->
            val interactions = remember { MutableInteractionSource() }
            GlassCard(
                Modifier.pressMotion(interactions, pressedScale = 0.992f)
                    .clickable(
                        interactionSource = interactions,
                        indication = LocalIndication.current,
                    ) {
                        select(journey)
                    }
                    .semantics {
                        contentDescription =
                            "${journey.displayName}, ${decimal(journey.distanceMeters / state.unit.distanceDivisor)} ${state.unit.distanceLabel}"
                    }
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpace.Group),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            journey.displayName,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (journey.interrupted) "Recovered recording"
                            else "${date(journey.startedAt)} · ${time(journey.startedAt)}",
                            color = Muted,
                            fontSize = 14.sp,
                        )
                    }
                    Text("View →", color = Pink, fontSize = 14.sp, maxLines = 1, softWrap = false)
                }
                JourneyTagPills(journey.tags)
                RouteTrace(journey.points, Modifier.fillMaxWidth().height(116.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(AppSpace.Related),
                ) {
                    Text(
                        "${decimal(journey.distanceMeters / state.unit.distanceDivisor)} ${state.unit.distanceLabel}",
                        color = Pink,
                    )
                    Text(duration(journey.durationMillis), color = Muted)
                    Text(
                        "${decimal(journey.averageSpeed * state.unit.multiplier, 0)} ${state.unit.label} avg",
                        color = Muted,
                        fontSize = 14.sp,
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
    maps: MapState,
    mapController: OfflineMaps?,
    onEasyReading: (Boolean) -> Unit,
    onDaylight: (Boolean) -> Unit,
    onTapHaptics: (Boolean) -> Unit,
    onTripHaptics: (Boolean) -> Unit,
    onDistanceChimes: (Boolean) -> Unit,
) {
    var privacyDetails by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(AppSpace.Section),
    ) {
        Header("Settings")
        GlassCard {
            Text("Measurement units", style = MaterialTheme.typography.titleMedium)
            FlowRow(
                Modifier,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(AppSpace.Related),
            ) {
                SpeedUnit.entries.forEach { unit ->
                    FilterChip(
                        modifier = Modifier.heightIn(min = 56.dp),
                        selected = state.unit == unit,
                        onClick = { onUnit(unit) },
                        label = { Text(if (unit == SpeedUnit.KMH) "km/h · km" else "mph · mi") },
                    )
                }
            }
        }
        SettingsSection("Behavior") {
            PreferenceRow(
                "Keep screen awake",
                "While the speedometer is open",
                state.keepAwake,
                onAwake,
            )
            HorizontalDivider(color = Border)
            PreferenceRow(
                "Speed reminder",
                "Show your chosen limit on the dial",
                state.speedLimit > 0,
                {
                    onLimit(
                        if (it)
                            (if (state.unit == SpeedUnit.KMH) 50.0 else 30.0) /
                                state.unit.multiplier
                        else 0.0
                    )
                },
            )
            if (state.speedLimit > 0) {
                Text(
                    "Your limit � ${decimal(state.speedLimit * state.unit.multiplier, 0)} ${state.unit.label}",
                    color = Pink,
                    style = MaterialTheme.typography.titleMedium,
                )
                Slider(
                    value =
                        (state.speedLimit * state.unit.multiplier)
                            .toFloat()
                            .coerceIn(
                                if (state.unit == SpeedUnit.KMH) 10f else 5f,
                                if (state.unit == SpeedUnit.KMH) 200f else 125f,
                            ),
                    onValueChange = {
                        onLimit((it / 5f).roundToInt() * 5.0 / state.unit.multiplier)
                    },
                    valueRange = if (state.unit == SpeedUnit.KMH) 10f..200f else 5f..125f,
                    steps = if (state.unit == SpeedUnit.KMH) 37 else 23,
                    modifier = Modifier.semantics { contentDescription = "Speed reminder limit" },
                )
            }
            HorizontalDivider(color = Border)
            PreferenceRow(
                "Haptic feedback on tap",
                "Buttons and navigation",
                state.tapHaptics,
                onTapHaptics,
            )
            HorizontalDivider(color = Border)
            PreferenceRow(
                "Trip haptics",
                "Start, pause and finish",
                state.tripHaptics,
                onTripHaptics,
            )
            HorizontalDivider(color = Border)
            PreferenceRow(
                "Distance chimes",
                "Every 1 ${state.unit.distanceLabel} � notification volume",
                state.distanceChimes,
                onDistanceChimes,
            )
        }
        SettingsSection("Appearance") {
            PreferenceRow(
                "Large text mode",
                "Larger text and roomier controls",
                state.easyReading,
                onEasyReading,
            )
            HorizontalDivider(color = Border)
            PreferenceRow(
                "High-contrast daylight mode",
                "Bright surfaces and stronger contrast",
                state.daylight,
                onDaylight,
            )
        }
        SettingsSection("Diagnostics") {
            PreferenceRow(
                "Show GPS tab",
                "Signal and satellite details",
                state.showGpsTab,
                onGpsTabVisibility,
            )
        }
        if (mapController != null) OfflineMapSettings(state, maps, mapController)
        GlassCard {
            Eyebrow("LOCATION & PRIVACY")
            Text(
                if (state.permission) "Precise location enabled"
                else "Precise location not enabled",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier,
            )
            Text(
                "Your journeys stay on this device.",
                color = Muted,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier,
            )
            AppTextButton(onClick = { privacyDetails = !privacyDetails }) {
                Text(if (privacyDetails) "Hide privacy details" else "Privacy details")
            }
            if (privacyDetails)
                Text(
                    "OpenFreeMap supplies the map area you view; your route is drawn locally. Android may use Google Location Accuracy. GitHub is used for updates. Velocity never uploads your journeys or satellite readings.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            AppTextButton(onClick = onAppSettings) { Text("Manage Android permissions") }
        }
        GlassCard {
            Text("Journey backup", style = MaterialTheme.typography.titleMedium)
            ActionRow {
                AppTextButton(
                    onBackup,
                    enabled = !state.loading && state.recording == Recording.IDLE,
                ) {
                    Text("Back up journeys")
                }
                AppTextButton(
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
    onEdit: (String, String, List<String>) -> Unit,
) {
    var delete by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var averageDetails by remember { mutableStateOf(false) }
    AppDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().systemBarsPadding(),
            shape = RoundedCornerShape(0.dp),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth()
                        .padding(horizontal = AppSpace.Page, vertical = AppSpace.Group),
                    horizontalArrangement = Arrangement.spacedBy(AppSpace.Group),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        journey.displayName,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    AppTextButton(onDismiss) { Text("Done") }
                }
                HorizontalDivider(color = Border.copy(alpha = 0.55f))
                Column(
                    Modifier.weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(AppSpace.Section),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpace.Related)) {
                        Text(
                            "${date(journey.startedAt)} · ${time(journey.startedAt)}",
                            color = Muted,
                        )
                        JourneyTagPills(journey.tags)
                        if (journey.notes.isNotBlank()) Text(journey.notes, fontSize = 14.sp)
                        AppTextButton(onClick = { editing = true }) { Text("Edit name & notes") }
                    }
                    SpeedGraph(journey, unit)
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpace.Group)) {
                        Text(
                            "Route",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics { heading() },
                        )
                        if (journey.points.isNotEmpty()) {
                            RouteMap(
                                journey.points,
                                Modifier.fillMaxWidth(),
                                cameraKey = journey.id,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RouteMarker("Start", Green)
                                RouteMarker("Finish", MaterialTheme.colorScheme.onSurface)
                            }
                        } else {
                            Text(
                                "No GPS route was recorded for this journey.",
                                color = Muted,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    GlassCard {
                        StatsRow {
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
                        StatsRow {
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
                        AppTextButton(onClick = { averageDetails = true }) {
                            Text("Average calculation")
                        }
                    }
                    if (journey.interrupted)
                        Text(
                            "Recovered after recording was interrupted. Includes the last saved checkpoint.",
                            fontSize = 14.sp,
                            color = Amber,
                        )
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpace.Related)) {
                        AppButton(
                            onClick = onExport,
                            enabled = journey.points.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Export route as GPX")
                        }
                        AppTextButton(onClick = { delete = true }) {
                            Text("Delete journey", color = Amber)
                        }
                    }
                }
            }
        }
    }
    if (averageDetails)
        AppAlertDialog(
            onDismissRequest = { averageDetails = false },
            title = { Text("Average speed") },
            text = {
                Text(
                    "Distance ÷ recorded time. Manual pauses are excluded; stationary recording time counts."
                )
            },
            confirmButton = { AppTextButton(onClick = { averageDetails = false }) { Text("Done") } },
        )
    if (editing)
        JourneyEditor(
            "Journey details",
            journey.name,
            journey.notes,
            "Save changes",
            { editing = false },
            { name, notes ->
                onEdit(name, notes, journey.tags)
                editing = false
            },
            initialTags = journey.tags,
            onSaveWithTags = { name, notes, tags ->
                onEdit(name, notes, tags)
                editing = false
            },
        )
    if (delete)
        AppAlertDialog(
            onDismissRequest = { delete = false },
            title = { Text("Delete this journey?") },
            text = {
                Text("Its route and statistics will be permanently removed from this device.")
            },
            confirmButton = { AppTextButton(onClick = onDelete) { Text("Delete") } },
            dismissButton = { AppTextButton(onClick = { delete = false }) { Text("Keep journey") } },
        )
}

@Composable
private fun RouteMarker(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Text(label, color = Muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun RouteTrace(points: List<RoutePoint>, modifier: Modifier) {
    val routeColor = Pink
    val startColor = Green
    val finishColor = MaterialTheme.colorScheme.onSurface
    if (points.isEmpty())
        Box(
            modifier.background(
                MaterialTheme.colorScheme.surfaceContainerLow,
                RoundedCornerShape(12.dp),
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text("Route appears after a GPS fix", color = Muted, fontSize = 14.sp)
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
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .semantics {
                contentDescription = "Recorded route trace, north up, ${points.size} GPS points"
            }
    ) {
        val inset = 22.dp.toPx()
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
            routeColor,
            style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawCircle(startColor, 5.dp.toPx(), project(positions.first()))
        drawCircle(finishColor, 4.dp.toPx(), project(positions.last()))
    }
}

@Composable
internal fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Panel,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, Border.copy(alpha = 0.55f)),
    ) {
        Column(
            Modifier.padding(AppSpace.Page),
            verticalArrangement = Arrangement.spacedBy(AppSpace.Group),
            content = content,
        )
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit, enabled: Boolean) {
    AppButton(
        onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
        haptic = !com.example.myapplication.ui.theme.LocalTripHaptics.current,
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

@Composable
internal fun Stat(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    reserveUnitSpace: Boolean = false,
) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Eyebrow(label, minLines = LocalStatLabelLines.current)
        Text(
            value,
            fontSize = if (value.length > 7) 23.sp else 28.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Medium,
        )
        if (unit.isNotBlank() || reserveUnitSpace)
            Text(unit.ifBlank { " " }, color = Muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun Eyebrow(text: String, minLines: Int = 1) {
    Text(
        text,
        color = Muted,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 1.sp,
        fontWeight = FontWeight.Medium,
        minLines = minLines,
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
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(AppSpace.Related),
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
    val quality = state.locationQuality
    val targetColor =
        when (quality) {
            LocationQuality.GOOD -> Green
            LocationQuality.WEAK -> Amber
            LocationQuality.SEARCHING -> MaterialTheme.colorScheme.error
            LocationQuality.OFF -> Muted
        }
    val color by animateColorAsState(targetColor, tween(220), label = "GPS quality colour")
    val label =
        when {
            !state.trackingEnabled -> "Tracking off"
            !state.permission -> "Location access needed"
            !state.gpsEnabled -> "Location off"
            quality == LocationQuality.SEARCHING -> "GPS � Lost"
            else ->
                "GPS � ${quality.label}" +
                    (if (state.hasSignal) state.fix?.accuracy else state.searchAccuracy)
                        ?.let { " � �${it.roundToInt()} m" }
                        .orEmpty()
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
        StatusDot(color, state.locationQuality)
        Text(
            "GPS · ${state.locationQuality.label.uppercase(Locale.getDefault())}",
            color = color,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.7.sp,
        )
    }
}

@Composable
private fun NavGlyph(index: Int, selected: Boolean, modifier: Modifier = Modifier) {
    val panelColor = Panel
    val color = animateColorAsState(if (selected) Pink else Muted, tween(180), label = "Tab colour")
    val scale = animateFloatAsState(if (selected) 1.08f else 1f, tween(180), label = "Tab emphasis")
    Canvas(
        modifier.size(26.dp).graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
    ) {
        val color = color.value
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
                        panelColor,
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
