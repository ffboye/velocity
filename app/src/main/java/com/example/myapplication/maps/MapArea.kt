package com.example.myapplication.maps

import kotlin.math.*

data class MapArea(val north: Double, val east: Double, val south: Double, val west: Double) {
    val valid: Boolean
        get() =
            listOf(north, east, south, west).all { it.isFinite() } &&
                south < north &&
                west < east &&
                south >= -85.0 &&
                north <= 85.0 &&
                west >= -180.0 &&
                east <= 180.0

    val tiles: Long
        get() {
            if (!valid) return Long.MAX_VALUE
            var count = 0L
            for (zoom in MIN_ZOOM..MAX_ZOOM) {
                val n = (1 shl zoom).toDouble()
                fun x(lon: Double) =
                    floor((lon + 180.0) / 360.0 * n).toLong().coerceIn(0L, n.toLong() - 1)
                fun y(lat: Double): Long {
                    val r = Math.toRadians(lat)
                    return floor((1.0 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * n)
                        .toLong()
                        .coerceIn(0L, n.toLong() - 1)
                }
                count += (x(east) - x(west) + 1) * (y(south) - y(north) + 1)
            }
            return count
        }

    val estimatedBytes: Long
        get() = if (tiles == Long.MAX_VALUE) Long.MAX_VALUE else 8_000_000L + tiles * 30_000L

    val downloadable: Boolean
        get() = valid && tiles <= 900L && estimatedBytes <= MAX_BYTES

    companion object {
        const val MIN_ZOOM = 6
        const val MAX_ZOOM = 14
        const val MAX_BYTES = 40_000_000L
        const val STYLE = "https://tiles.openfreemap.org/styles/positron"
    }
}

data class OfflineArea(
    val id: Long,
    val name: String,
    val area: MapArea,
    val bytes: Long = 0,
    val progress: Float = 0f,
    val complete: Boolean = false,
    val downloading: Boolean = false,
    val message: String? = null,
)

data class MapState(
    val enabled: Boolean = false,
    val wifiOnly: Boolean = true,
    val wifi: Boolean = false,
    val network: Boolean = false,
    val loading: Boolean = true,
    val areas: List<OfflineArea> = emptyList(),
    val message: String? = null,
) {
    fun mayDownload(foreground: Boolean): Boolean =
        enabled && foreground && network && (!wifiOnly || wifi)
}
