package com.example.myapplication.tracking

import kotlin.math.*

enum class Recording {
    IDLE,
    RECORDING,
    PAUSED,
}

enum class SpeedUnit(
    val label: String,
    val multiplier: Double,
    val distanceDivisor: Double,
    val distanceLabel: String,
) {
    KMH("km/h", 3.6, 1000.0, "km"),
    MPH("mph", 2.2369362921, 1609.344, "mi"),
}

data class GpsFix(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val elapsedMillis: Long,
    val wallMillis: Long,
    val speed: Double? = null,
    val altitude: Double? = null,
    val bearing: Float? = null,
    val source: LocationSource = LocationSource.GPS,
)

data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val wallMillis: Long,
    val segment: Int,
)

data class SpeedSample(val recordedMillis: Long, val metersPerSecond: Double, val segment: Int)

data class Journey(
    val id: String,
    val startedAt: Long,
    val durationMillis: Long,
    val distanceMeters: Double,
    val maxSpeed: Double,
    val points: List<RoutePoint>,
    val interrupted: Boolean = false,
    val name: String = "",
    val notes: String = "",
    val samples: List<SpeedSample> = emptyList(),
) {
    val averageSpeed: Double
        get() = if (durationMillis > 0) distanceMeters / (durationMillis / 1000.0) else 0.0
}

data class DriveState(
    val loading: Boolean = true,
    val storageReady: Boolean = true,
    val permission: Boolean = false,
    val gpsEnabled: Boolean = false,
    val fix: GpsFix? = null,
    val hasSignal: Boolean = false,
    val searchAccuracy: Float? = null,
    val locationProblem: String? = null,
    val fixAgeMillis: Long? = null,
    val gnss: GnssState = GnssState(),
    val gnssFresh: Boolean = false,
    val saveFailed: Boolean = false,
    val saving: Boolean = false,
    val trackingEnabled: Boolean = true,
    val showGpsTab: Boolean = true,
    val speed: Double = 0.0,
    val recording: Recording = Recording.IDLE,
    val durationMillis: Long = 0,
    val distanceMeters: Double = 0.0,
    val maxSpeed: Double = 0.0,
    val points: List<RoutePoint> = emptyList(),
    val journeys: List<Journey> = emptyList(),
    val unit: SpeedUnit = SpeedUnit.KMH,
    val keepAwake: Boolean = true,
    val speedLimit: Double = 0.0,
    val message: String? = null,
) {
    val averageSpeed: Double
        get() = if (durationMillis > 0) distanceMeters / (durationMillis / 1000.0) else 0.0
}

/**
 * Pure GPS and recording calculations. All input times are monotonic except exported timestamps.
 */
class JourneyEngine {
    var recording = Recording.IDLE
        private set

    var speed = 0.0
        private set

    var durationMillis = 0L
        private set

    var distanceMeters = 0.0
        private set

    var maxSpeed = 0.0
        private set

    var fix: GpsFix? = null
        private set

    private var id = ""
    private var startedAt = 0L
    private var lastTick = 0L
    private var anchor: GpsFix? = null
    private var segment = 0
    private val route = mutableListOf<RoutePoint>()
    private val speedSamples = mutableListOf<SpeedSample>()
    private var sampleInterval = 1000L
    val points: List<RoutePoint>
        get() = route.toList()

    fun start(id: String, wallMillis: Long, elapsedMillis: Long) {
        if (recording != Recording.IDLE) return
        this.id = id
        startedAt = wallMillis
        durationMillis = 0
        distanceMeters = 0.0
        maxSpeed = 0.0
        route.clear()
        speedSamples.clear()
        sampleInterval = 1000L
        anchor = null
        segment = 0
        lastTick = elapsedMillis
        recording = Recording.RECORDING
    }

    fun tick(now: Long) {
        if (recording == Recording.RECORDING) durationMillis += (now - lastTick).coerceAtLeast(0)
        lastTick = now
        if (fix?.let { now - it.elapsedMillis > 5000 } != false) speed = 0.0
    }

    fun togglePause(now: Long) {
        if (recording == Recording.IDLE) return
        tick(now)
        recording = if (recording == Recording.RECORDING) Recording.PAUSED else Recording.RECORDING
        anchor = null
        segment++
    }

    fun resetSignal() {
        fix = null
        speed = 0.0
        anchor = null
        segment++
    }

    fun snapshot(interrupted: Boolean = false): Journey? =
        if (recording == Recording.IDLE) null
        else
            Journey(
                id,
                startedAt,
                durationMillis,
                distanceMeters,
                maxSpeed,
                points,
                interrupted,
                samples = speedSamples.toList(),
            )

    fun finish(now: Long): Journey? {
        tick(now)
        val result = snapshot()
        recording = Recording.IDLE
        anchor = null
        return result
    }

    fun accept(value: GpsFix, now: Long): Boolean {
        if (
            !value.latitude.isFinite() ||
                !value.longitude.isFinite() ||
                value.latitude !in -90.0..90.0 ||
                value.longitude !in -180.0..180.0 ||
                !value.accuracy.isFinite() ||
                value.accuracy !in 0f..35f ||
                now - value.elapsedMillis !in 0..5000
        )
            return false
        val previous = fix
        if (previous != null && value.elapsedMillis <= previous.elapsedMillis) return false
        val gap = previous?.let { (value.elapsedMillis - it.elapsedMillis) / 1000.0 }
        val movement =
            previous?.let { distance(it.latitude, it.longitude, value.latitude, value.longitude) }
        if (gap != null && gap <= 15 && movement!! / gap > 100.0) return false
        val measured =
            value.speed?.takeIf { it.isFinite() && it in 0.0..100.0 }
                ?: if (
                    gap != null &&
                        gap in 0.5..15.0 &&
                        movement!! > max(3.0, value.accuracy.toDouble())
                )
                    movement / gap
                else 0.0
        speed =
            if (measured < 0.7) 0.0
            else if (previous == null || gap!! > 5) measured else speed * 0.3 + measured * 0.7
        fix = value
        if (recording != Recording.RECORDING) return true
        tick(now)
        maxSpeed = max(maxSpeed, measured)
        if (anchor != null && gap != null && gap > 15) {
            anchor = null
            segment++
        }
        if (
            speedSamples.lastOrNull()?.let {
                durationMillis - it.recordedMillis >= sampleInterval || it.segment != segment
            } != false
        ) {
            speedSamples.add(SpeedSample(durationMillis, speed, segment))
            if (speedSamples.size > 6000) {
                // Segment IDs retain graph gaps while bounding memory on long recordings.
                val reduced =
                    speedSamples.filterIndexed { index, sample ->
                        index == 0 || index == speedSamples.lastIndex || index % 2 == 0
                    }
                speedSamples.clear()
                speedSamples.addAll(reduced)
                sampleInterval = (sampleInterval * 2).coerceAtMost(60000L)
            }
        }
        if (anchor == null || gap == null || gap > 15) {
            if (anchor != null) segment++
            anchor = value
            route.add(RoutePoint(value.latitude, value.longitude, value.wallMillis, segment))
        } else {
            val old = anchor!!
            val step = distance(old.latitude, old.longitude, value.latitude, value.longitude)
            val threshold = max(3.0, (old.accuracy + value.accuracy) * 0.5)
            if (step >= threshold && measured >= 0.7) {
                distanceMeters += step
                anchor = value
                route.add(RoutePoint(value.latitude, value.longitude, value.wallMillis, segment))
            }
        }
        if (route.size > 6000) {
            val reduced =
                route.filterIndexed { index, _ ->
                    index == 0 || index == route.lastIndex || index % 2 == 0
                }
            route.clear()
            route.addAll(reduced)
        }
        return true
    }

    companion object {
        fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a =
                sin(dLat / 2).pow(2) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
            return 6371000 * 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
        }
    }
}
