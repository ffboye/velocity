package com.example.myapplication.tracking

import org.junit.Assert.*
import org.junit.Test

class JourneyBackupTest {
    private val trip =
        Journey("trip-one", 1000, 5000, 200.0, 10.0, listOf(RoutePoint(52.0, 4.0, 1000, 0)))

    @Test
    fun backupPreservesStatsAndRoutes() {
        assertEquals(
            listOf(trip),
            JourneyBackup.read(JourneyCodec.encodeAll(listOf(trip), null).toByteArray()),
        )
    }

    @Test
    fun restoringKeepsExistingJourneysAndAvoidsDuplicates() {
        val second = trip.copy(id = "trip-two", startedAt = 2000)
        assertEquals(
            listOf(second, trip),
            JourneyBackup.merge(listOf(trip), listOf(trip.copy(distanceMeters = 1.0), second)),
        )
    }

    @Test
    fun invalidCoordinatesAreRejected() {
        val invalid = trip.copy(points = listOf(RoutePoint(200.0, 4.0, 1000, 0)))
        try {
            JourneyBackup.read(JourneyCodec.encodeAll(listOf(invalid), null).toByteArray())
            fail()
        } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun duplicateJourneyIdsAreRejected() {
        try {
            JourneyBackup.read(JourneyCodec.encodeAll(listOf(trip, trip), null).toByteArray())
            fail()
        } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun activeCheckpointIsRestoredAsInterruptedHistory() {
        assertTrue(
            JourneyBackup.read(JourneyCodec.encodeAll(emptyList(), trip).toByteArray())
                .single()
                .interrupted
        )
    }
}
