package com.example.myapplication.tracking

object JourneyTags {
    val suggestions = listOf("Commute", "Cycling", "Drive", "Walk")

    fun clean(tags: List<String>) =
        tags.map { it.trim().take(24) }.filter { it.isNotEmpty() }.distinct().take(5)
}
