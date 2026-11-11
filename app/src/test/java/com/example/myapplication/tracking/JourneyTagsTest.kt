package com.example.myapplication.tracking

import org.junit.Assert.*
import org.junit.Test

class JourneyTagsTest {
    private val journey = Journey("tagged", 1000, 1000, 10.0, 10.0, emptyList())

    @Test
    fun tagsSurviveDiskAndBackupRoundTrips() {
        val tagged = journey.copy(tags = listOf("Commute", "Cycling"))
        val encoded = JourneyCodec.encodeAll(listOf(tagged), tagged)
        assertEquals(tagged, JourneyCodec.decodeAll(encoded).active)
        assertEquals(
            tagged,
            JourneyBackup.read(JourneyCodec.encodeAll(listOf(tagged), null).toByteArray()).first(),
        )
        assertTrue(JourneyCodec.gpx(tagged).contains("<type>Commute, Cycling</type>"))
    }

    @Test
    fun olderFilesHaveNoInventedTags() {
        val encoded =
            JourneyCodec.encodeAll(listOf(journey), null)
                .replace("\"tags\":[],", "")
                .replace(",\"tags\":[]", "")
        assertTrue(JourneyCodec.decodeAll(encoded).journeys.single().tags.isEmpty())
    }

    @Test
    fun normalizationBoundsAndDeduplicatesUserTags() {
        assertEquals(
            listOf("Commute", "Drive"),
            JourneyTags.clean(listOf(" Commute ", "", "Commute", "Drive")),
        )
        assertEquals(24, JourneyTags.clean(listOf("a".repeat(100))).single().length)
        assertEquals(5, JourneyTags.clean((0..10).map { "$it" }).size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun malformedBackupsCannotCarryUnboundedTags() {
        JourneyValidation.validate(journey.copy(tags = listOf("a".repeat(25))))
    }
}
