package com.example.myapplication.tracking

object JourneyBackup {
    const val MAX_BYTES = 16 * 1024 * 1024

    fun read(bytes: ByteArray): List<Journey> {
        require(bytes.size <= MAX_BYTES) { "The backup is too large" }
        val saved = JourneyCodec.decodeAll(bytes.toString(Charsets.UTF_8))
        val journeys = saved.journeys + listOfNotNull(saved.active?.copy(interrupted = true))
        require(journeys.size <= 1000) { "The backup contains too many journeys" }
        require(journeys.map { it.id }.distinct().size == journeys.size) { "Duplicate journey IDs" }
        return journeys
    }

    fun merge(existing: List<Journey>, imported: List<Journey>) =
        (existing + imported).distinctBy { it.id }.sortedByDescending { it.startedAt }
}
