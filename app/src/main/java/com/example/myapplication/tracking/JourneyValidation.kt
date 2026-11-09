package com.example.myapplication.tracking

/** Shared by local storage and imported backups; old schema-1 optional fields stay optional. */
object JourneyValidation {
    private val identifier = Regex("[A-Za-z0-9-]{1,128}")

    fun validate(trip: Journey) {
        require(trip.id.matches(identifier)) { "Invalid journey ID" }
        require(trip.startedAt >= 0 && trip.durationMillis >= 0) { "Invalid journey time" }
        require(trip.distanceMeters.isFinite() && trip.distanceMeters >= 0) { "Invalid distance" }
        require(trip.maxSpeed.isFinite() && trip.maxSpeed in 0.0..100.0) { "Invalid maximum speed" }
        require(trip.points.size <= 6000) { "Too many route points" }
        require(trip.name.length <= 100 && trip.notes.length <= 2000) { "Journey text is too long" }
        require(trip.samples.size <= 12000) { "Too many speed samples" }
        var previousTime = -1L
        trip.samples.forEach { sample ->
            require(
                sample.recordedMillis in 0..trip.durationMillis &&
                    sample.recordedMillis >= previousTime
            ) {
                "Invalid sample time"
            }
            require(
                sample.metersPerSecond.isFinite() &&
                    sample.metersPerSecond in 0.0..100.0 &&
                    sample.segment >= 0
            ) {
                "Invalid speed sample"
            }
            previousTime = sample.recordedMillis
        }
        trip.points.forEach { point ->
            require(point.latitude.isFinite() && point.latitude in -90.0..90.0) {
                "Invalid latitude"
            }
            require(point.longitude.isFinite() && point.longitude in -180.0..180.0) {
                "Invalid longitude"
            }
            require(point.wallMillis >= 0 && point.segment >= 0) { "Invalid route point" }
        }
    }
}
