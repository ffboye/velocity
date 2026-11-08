package com.example.myapplication.tracking

enum class LocationSource(val label: String) {
    GPS("GPS receiver"),
    FUSED("Fused location"),
}

enum class ReceiverStatus {
    IDLE,
    LISTENING,
    UNAVAILABLE,
}

data class Satellite(
    val constellation: Int,
    val id: Int,
    val cn0: Float,
    val elevation: Float,
    val azimuth: Float,
    val usedInFix: Boolean,
) {
    val system: String
        get() =
            when (constellation) {
                1 -> "GPS"
                2 -> "SBAS"
                3 -> "GLONASS"
                4 -> "QZSS"
                5 -> "BeiDou"
                6 -> "Galileo"
                7 -> "NavIC"
                else -> "GNSS"
            }

    val key: String
        get() = "$constellation:$id"

    val label: String
        get() = "$system $id"
}

data class GnssState(
    val status: ReceiverStatus = ReceiverStatus.IDLE,
    val satellites: List<Satellite> = emptyList(),
    val updatedAt: Long? = null,
    val firstFixMillis: Int? = null,
) {
    fun isFresh(now: Long): Boolean =
        status == ReceiverStatus.LISTENING && updatedAt?.let { now - it in 0..10000 } == true

    val usedCount: Int
        get() = satellites.count { it.usedInFix }

    val strongest: Float?
        get() = satellites.maxOfOrNull { it.cn0 }

    companion object {
        // Multi-band receivers may report the same satellite more than once. Count it once,
        // retain its strongest reading, and preserve use in a fix on either frequency.
        fun normalize(readings: List<Satellite>): List<Satellite> =
            readings
                .filter {
                    it.id > 0 &&
                        it.cn0.isFinite() &&
                        it.cn0 in 0f..63f &&
                        it.elevation.isFinite() &&
                        it.elevation in -90f..90f &&
                        it.azimuth.isFinite() &&
                        it.azimuth in 0f..360f
                }
                .groupBy { it.key }
                .values
                .map { group ->
                    group.maxBy { it.cn0 }.copy(usedInFix = group.any { it.usedInFix })
                }
                .sortedWith(
                    compareByDescending<Satellite> { it.usedInFix }
                        .thenByDescending { it.cn0 }
                        .thenBy { it.key }
                )
    }
}
