package com.example.myapplication.tracking

import java.util.Calendar
import java.util.TimeZone

data class JourneyTotals(
    val distance: Double,
    val recordedMillis: Long,
    val maxSpeed: Double,
    val recentDistance: Double,
) {
    val averageSpeed: Double
        get() = if (recordedMillis > 0) distance / (recordedMillis / 1000.0) else 0.0

    companion object {
        fun from(
            journeys: List<Journey>,
            now: Long,
            zone: TimeZone = TimeZone.getDefault(),
        ): JourneyTotals {
            val start =
                Calendar.getInstance(zone)
                    .apply {
                        timeInMillis = now
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                        add(Calendar.DAY_OF_YEAR, -6)
                    }
                    .timeInMillis
            return JourneyTotals(
                journeys.sumOf { it.distanceMeters },
                journeys.sumOf { it.durationMillis },
                journeys.maxOfOrNull { it.maxSpeed } ?: 0.0,
                journeys.filter { it.startedAt in start..now }.sumOf { it.distanceMeters },
            )
        }
    }
}
