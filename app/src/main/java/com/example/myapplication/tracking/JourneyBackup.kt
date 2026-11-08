package com.example.myapplication.tracking

object JourneyBackup {
    const val MAX_BYTES = 16 * 1024 * 1024

    fun read(bytes: ByteArray): List<Journey> {
        require(bytes.size <= MAX_BYTES) { "The backup is too large" }
        val saved = JourneyCodec.decodeAll(bytes.toString(Charsets.UTF_8))
        val journeys = saved.journeys + listOfNotNull(saved.active?.copy(interrupted = true))
        require(journeys.size <= 1000) { "The backup contains too many journeys" }
        require(journeys.map { it.id }.distinct().size == journeys.size) { "Duplicate journey IDs" }
        journeys.forEach { trip ->
            require(trip.id.matches(Regex("[A-Za-z0-9-]{1,128}")))
            require(trip.startedAt >= 0 && trip.durationMillis >= 0)
            require(trip.distanceMeters.isFinite() && trip.distanceMeters >= 0)
            require(trip.maxSpeed.isFinite() && trip.maxSpeed in 0.0..100.0)
            require(trip.points.size <= 6000)
            trip.points.forEach { point ->
                require(point.latitude.isFinite() && point.latitude in -90.0..90.0)
                require(point.longitude.isFinite() && point.longitude in -180.0..180.0)
                require(point.wallMillis >= 0 && point.segment >= 0)
            }
        }
        return journeys
    }

    fun merge(existing: List<Journey>, imported: List<Journey>) =
        (existing + imported).distinctBy { it.id }.sortedByDescending { it.startedAt }
}
