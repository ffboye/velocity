package com.example.myapplication.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.example.myapplication.tracking.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

internal val Journey.displayName: String
    get() =
        name.ifBlank {
            SimpleDateFormat("EEE, d MMM · HH:mm", Locale.getDefault()).format(Date(startedAt))
        }

@Composable
internal fun SpeedGraph(journey: Journey, unit: SpeedUnit) {
    var selected by remember(journey.id) { mutableStateOf<SpeedSample?>(null) }
    Text("Speed over recorded time", style = MaterialTheme.typography.titleMedium)
    if (journey.samples.isEmpty()) {
        Text(
            "This journey was saved before speed graphs were added. New recordings include a graph.",
            color = Muted,
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }
    val maximum =
        max(
            10.0,
            ceil(journey.samples.maxOf { it.metersPerSecond } * unit.multiplier / 10.0) * 10.0,
        )
    val duration = max(1000L, journey.durationMillis)
    Text(
        selected?.let {
            "${(it.metersPerSecond * unit.multiplier).roundToInt()} ${unit.label} at ${graphTime(it.recordedMillis)}"
        } ?: "Tap the graph for a reading. Gaps in reception and manual pauses aren't connected.",
        color = Muted,
        style = MaterialTheme.typography.bodySmall,
    )
    Text(
        "${maximum.toInt()} ${unit.label}",
        color = Muted,
        style = MaterialTheme.typography.labelSmall,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { selected = journey.samples.first() }) { Text("First reading") }
        TextButton(onClick = { selected = journey.samples.maxBy { it.metersPerSecond } }) {
            Text("Peak reading")
        }
        TextButton(onClick = { selected = journey.samples.last() }) { Text("Last reading") }
    }
    Canvas(
        Modifier.fillMaxWidth()
            .height(160.dp)
            .semantics {
                contentDescription =
                    "Speed graph. ${journey.samples.size} readings over ${graphTime(journey.durationMillis)} recorded time. Maximum ${ (journey.maxSpeed * unit.multiplier).roundToInt()} ${unit.label}. Manual pauses are excluded."
            }
            .pointerInput(journey.id, journey.samples) {
                detectTapGestures { offset ->
                    val time = offset.x / size.width * duration
                    selected = journey.samples.minByOrNull { abs(it.recordedMillis - time) }
                }
            }
    ) {
        for (row in 0..3) drawLine(
            Border,
            Offset(0f, size.height * row / 3),
            Offset(size.width, size.height * row / 3),
            1.dp.toPx(),
        )
        val path = Path()
        var segment = -1
        journey.samples.forEach { sample ->
            val x = (sample.recordedMillis.toDouble() / duration * size.width).toFloat()
            val y =
                (size.height * (1.0 - sample.metersPerSecond * unit.multiplier / maximum)).toFloat()
            if (sample.segment != segment) path.moveTo(x, y) else path.lineTo(x, y)
            segment = sample.segment
        }
        drawPath(path, Pink, style = Stroke(2.dp.toPx()))
        selected?.let {
            drawCircle(
                Pink,
                4.dp.toPx(),
                Offset(
                    (it.recordedMillis.toDouble() / duration * size.width).toFloat(),
                    (size.height * (1.0 - it.metersPerSecond * unit.multiplier / maximum)).toFloat(),
                ),
            )
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("0:00", color = Muted, style = MaterialTheme.typography.labelSmall)
        Text(
            graphTime(journey.durationMillis),
            color = Muted,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private fun graphTime(millis: Long) =
    String.format(Locale.getDefault(), "%d:%02d", millis / 60000, millis / 1000 % 60)
