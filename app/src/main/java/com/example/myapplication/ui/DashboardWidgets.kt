package com.example.myapplication.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.example.myapplication.tracking.*
import com.example.myapplication.ui.theme.LocalDaylight
import java.util.Locale
import kotlin.math.*

@Composable
internal fun SpeedGauge(state: DriveState) {
    val speed = if (state.hasSignal) state.speed * state.unit.multiplier else 0.0
    val limit = state.speedLimit.takeIf { it.isFinite() && it > 0 }
    val over = limit != null && state.hasSignal && state.speed > limit
    val severe = over && state.speed > limit!! * 1.1
    val normal = Pink
    val amber = Amber
    val red = MaterialTheme.colorScheme.error
    val color by
        animateColorAsState(
            if (severe) red else if (over) amber else normal,
            tween(180),
            label = "Limit warning",
        )
    val end = if (LocalDaylight.current) Color(0xFF00757B) else Color(0xFF75E3DA)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val tick = Border
    val fraction =
        animateFloatAsState(
            (speed / if (state.unit == SpeedUnit.KMH) 200.0 else 125.0)
                .coerceIn(0.0, 1.0)
                .toFloat(),
            tween(300),
            label = "Speed arc",
        )
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val diameter = minOf(maxWidth, 300.dp)
            val numberSize =
                with(LocalDensity.current) { minOf(108.sp.toDp(), diameter / 2.6f).toSp() }
            Box(
                Modifier.size(diameter).testTag("speed-dial").semantics(mergeDescendants = true) {
                    contentDescription = "Current speed ${speed.roundToInt()} ${state.unit.label}"
                },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(
                        Brush.radialGradient(listOf(normal.copy(alpha = 0.12f), Color.Transparent)),
                        radius = size.minDimension * 0.43f,
                    )
                    val inset = 26.dp.toPx()
                    val origin = Offset(inset, inset)
                    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                    drawArc(
                        track,
                        135f,
                        270f,
                        false,
                        origin,
                        arcSize,
                        style = Stroke(12.dp.toPx(), cap = StrokeCap.Round),
                    )
                    if (fraction.value > 0)
                        drawArc(
                            Brush.sweepGradient(listOf(normal, if (over) color else end, normal)),
                            135f,
                            270f * fraction.value,
                            false,
                            origin,
                            arcSize,
                            style = Stroke(12.dp.toPx(), cap = StrokeCap.Round),
                        )
                    val radius = size.minDimension / 2 - 10.dp.toPx()
                    for (i in 0..20) {
                        val angle = Math.toRadians(135.0 + 13.5 * i)
                        val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                        drawLine(
                            tick,
                            center + direction * radius,
                            center + direction * (radius - (if (i % 5 == 0) 7.dp else 3.dp).toPx()),
                            1.5.dp.toPx(),
                        )
                    }
                }
                if (limit != null)
                    Canvas(Modifier.fillMaxSize().testTag("speed-limit-ring")) {
                        val inset = 4.dp.toPx()
                        drawArc(
                            color.copy(alpha = if (over) 0.9f else 0.3f),
                            135f,
                            270f,
                            false,
                            Offset(inset, inset),
                            Size(size.width - inset * 2, size.height - inset * 2),
                            style =
                                Stroke(
                                    if (over) 3.dp.toPx() else 1.5.dp.toPx(),
                                    cap = StrokeCap.Round,
                                ),
                        )
                    }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        speed.roundToInt().toString(),
                        fontSize = numberSize,
                        lineHeight = numberSize * 1.05f,
                        letterSpacing = (-4).sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        state.unit.label.uppercase(Locale.getDefault()),
                        color = normal,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        if (limit != null)
            Surface(
                modifier = Modifier.testTag("speed-limit-badge"),
                shape = RoundedCornerShape(16.dp),
                color = color.copy(alpha = 0.10f),
                contentColor = color,
                border = BorderStroke(1.5.dp, color.copy(alpha = 0.65f)),
            ) {
                FlowRow(
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Your limit", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "${(limit * state.unit.multiplier).roundToInt()} ${state.unit.label}",
                        fontWeight = FontWeight.Bold,
                    )
                    if (over) Text("Over limit", fontWeight = FontWeight.Bold)
                }
            }
    }
}

@Composable
internal fun QuickStats(state: DriveState) {
    val altitude = state.fix?.altitude?.takeIf { state.hasSignal && it.isFinite() }
    val imperial = state.unit == SpeedUnit.MPH
    FlowRow(
        Modifier.fillMaxWidth(),
        maxItemsInEachRow = if (LocalDensity.current.fontScale > 1.3f) 1 else 2,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlancePill(
            "Altitude",
            altitude?.let {
                "${(it * if (imperial) 3.28084 else 1.0).roundToInt()} ${if (imperial) "ft" else "m"}"
            } ?: "—",
            Modifier.weight(1f),
        )
        GlancePill(
            "Session peak",
            "${(state.maxSpeed * state.unit.multiplier).roundToInt()} ${state.unit.label}",
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun GlancePill(label: String, value: String, modifier: Modifier) {
    Surface(
        modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(label, color = Muted, style = MaterialTheme.typography.labelSmall)
            Text(
                value,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
internal fun JourneySummary(state: DriveState, summary: JourneyTotals) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatsRow {
            SummaryCell("JOURNEYS", state.journeys.size.toString(), "", Modifier.weight(1f))
            SummaryCell(
                "TOTAL DISTANCE",
                String.format(
                    Locale.getDefault(),
                    "%.2f",
                    state.journeys.sumOf { it.distanceMeters } / state.unit.distanceDivisor,
                ),
                state.unit.distanceLabel,
                Modifier.weight(1f),
            )
        }
        StatsRow {
            SummaryCell(
                "AVERAGE SPEED",
                (summary.averageSpeed * state.unit.multiplier).roundToInt().toString(),
                state.unit.label,
                Modifier.weight(1f),
            )
            SummaryCell(
                "TOP SPEED",
                (summary.maxSpeed * state.unit.multiplier).roundToInt().toString(),
                state.unit.label,
                Modifier.weight(1f),
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Last 7 days · ${String.format(Locale.getDefault(), "%.2f", summary.recentDistance / state.unit.distanceDivisor)} ${state.unit.distanceLabel}",
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
            )
            val seconds = summary.recordedMillis / 1000
            Text(
                "Recorded time · ${String.format(Locale.getDefault(), "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)}",
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SummaryCell(label: String, value: String, unit: String, modifier: Modifier) {
    GlassCard(modifier) { Stat(label, value, unit, reserveUnitSpace = true) }
}

@Composable
internal fun JourneyTagPills(tags: List<String>) {
    if (tags.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tags.forEach { tag ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    tag,
                    Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
internal fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    GlassCard {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = Pink,
            modifier = Modifier.semantics { heading() },
        )
        Column(content = content)
    }
}
