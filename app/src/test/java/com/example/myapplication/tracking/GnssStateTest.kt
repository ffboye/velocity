package com.example.myapplication.tracking

import org.junit.Assert.*
import org.junit.Test

class GnssStateTest {
    private val satellite = Satellite(1, 7, 32f, 45f, 120f, false)

    @Test
    fun multiBandSatelliteCountsOnceAndPreservesUseInFix() {
        val readings =
            GnssState.normalize(
                listOf(satellite.copy(cn0 = 45f), satellite.copy(cn0 = 25f, usedInFix = true))
            )
        assertEquals(1, readings.size)
        assertEquals(45f, readings.single().cn0, 0f)
        assertTrue(readings.single().usedInFix)
        assertEquals(1, GnssState(satellites = readings).usedCount)
    }

    @Test
    fun equalIdsInDifferentConstellationsRemainSeparate() {
        val readings = GnssState.normalize(listOf(satellite, satellite.copy(constellation = 6)))
        assertEquals(2, readings.size)
        assertEquals(setOf("GPS 7", "Galileo 7"), readings.map { it.label }.toSet())
    }

    @Test
    fun invalidReadingsNeverReachChartsOrCounts() {
        val readings =
            GnssState.normalize(
                listOf(
                    satellite,
                    satellite.copy(cn0 = Float.NaN),
                    satellite.copy(cn0 = -1f),
                    satellite.copy(cn0 = 64f),
                    satellite.copy(azimuth = 361f),
                    satellite.copy(elevation = 91f),
                    satellite.copy(id = 0),
                )
            )
        assertEquals(listOf(satellite), readings)
    }

    @Test
    fun noReportAndStaleReportAreDifferentFromZeroSatellites() {
        assertFalse(GnssState(status = ReceiverStatus.LISTENING).isFresh(1000))
        val emptyReport = GnssState(ReceiverStatus.LISTENING, emptyList(), 1000)
        assertTrue(emptyReport.isFresh(1000))
        assertTrue(emptyReport.isFresh(11000))
        assertFalse(emptyReport.isFresh(11001))
        assertFalse(emptyReport.isFresh(999))
        assertFalse(emptyReport.copy(status = ReceiverStatus.IDLE).isFresh(1000))
    }

    @Test
    fun usedSatellitesSortFirstThenByStrength() {
        val readings =
            GnssState.normalize(
                listOf(
                    satellite.copy(id = 1, cn0 = 50f),
                    satellite.copy(id = 2, cn0 = 20f, usedInFix = true),
                    satellite.copy(id = 3, cn0 = 30f, usedInFix = true),
                )
            )
        assertEquals(listOf(3, 2, 1), readings.map { it.id })
        assertEquals(50f, GnssState(satellites = readings).strongest!!, 0f)
    }
}
