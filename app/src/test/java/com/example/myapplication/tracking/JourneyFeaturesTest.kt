package com.example.myapplication.tracking

import com.example.myapplication.maps.MapArea
import com.example.myapplication.maps.MapState
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class JourneyFeaturesTest {
    @Test
    fun mapNeverJoinsNonAdjacentSectionsWithReusedSegmentIds() {
        val points =
            listOf(
                RoutePoint(0.0, 0.0, 0, 0),
                RoutePoint(0.0, 0.1, 1, 1),
                RoutePoint(0.0, 0.2, 2, 0),
            )
        assertEquals(3, routeSections(points).size)
    }

    private fun fix(t: Long, lon: Double = 0.0, speed: Double = 10.0) =
        GpsFix(0.0, lon, 3f, t, 1700000000000 + t, speed)

    private val trip =
        Journey(
            "id",
            1700000000000,
            3000,
            20.0,
            10.0,
            listOf(RoutePoint(52.0, 4.0, 1700000000000, 0)),
        )

    @Test
    fun originalJourneyFileLoadsWithoutInventingGraphOrMetadata() {
        val json =
            """{"schema":1,"journeys":[{"id":"old","startedAt":1000,"durationMillis":1000,"distanceMeters":10,"maxSpeed":10,"points":[[52,4,1000,0]]}]}"""
        val old = JourneyCodec.decodeAll(json).journeys.single()
        assertEquals("", old.name)
        assertEquals("", old.notes)
        assertTrue(old.samples.isEmpty())
        assertEquals(1, old.points.size)
    }

    @Test
    fun namesNotesGraphsAndBackupRoundTrip() {
        val saved =
            trip.copy(
                name = "A < B & C",
                notes = "Café\nLong route",
                samples = listOf(SpeedSample(0, 0.0, 0), SpeedSample(2000, 10.0, 1)),
            )
        assertEquals(
            saved,
            JourneyBackup.read(JourneyCodec.encodeAll(listOf(saved), null).toByteArray()).single(),
        )
        val doc =
            DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(JourneyCodec.gpx(saved).byteInputStream())
        assertEquals(saved.name, doc.getElementsByTagName("name").item(0).textContent)
        assertEquals(saved.notes, doc.getElementsByTagName("desc").item(0).textContent)
    }

    @Test
    fun graphTimeExcludesManualPauseAndKeepsGap() {
        val engine = JourneyEngine()
        engine.start("trip", 0, 1000)
        engine.accept(fix(1000), 1000)
        engine.accept(fix(2000, 0.0001), 2000)
        engine.togglePause(2000)
        engine.accept(fix(9000, 0.001), 9000)
        engine.togglePause(12000)
        engine.accept(fix(13000, 0.0011), 13000)
        val samples = engine.snapshot()!!.samples
        assertEquals(3, samples.size)
        assertEquals(2000L, samples.last().recordedMillis)
        assertNotEquals(samples.first().segment, samples.last().segment)
    }

    @Test
    fun signalLossCreatesGraphGap() {
        val engine = JourneyEngine()
        engine.start("trip", 0, 1000)
        engine.accept(fix(1000), 1000)
        engine.accept(fix(30000, 1.0), 30000)
        val samples = engine.snapshot()!!.samples
        assertNotEquals(samples.first().segment, samples.last().segment)
        assertEquals(0.0, engine.distanceMeters, 0.0)
    }

    @Test
    fun stationaryTimeCountsWithoutAutomaticPauses() {
        val engine = JourneyEngine()
        engine.start("trip", 0, 1000)
        engine.accept(fix(1000, speed = 0.0), 1000)
        engine.tick(11000)
        assertEquals(Recording.RECORDING, engine.recording)
        assertEquals(10000L, engine.snapshot()!!.durationMillis)
    }

    @Test
    fun summaryAverageIsWeightedByRecordedTime() {
        val values =
            listOf(
                trip.copy(distanceMeters = 10.0, durationMillis = 10000),
                trip.copy(id = "b", distanceMeters = 90.0, durationMillis = 30000),
            )
        val total = JourneyTotals.from(values, 1700000000000, TimeZone.getTimeZone("UTC"))
        assertEquals(2.5, total.averageSpeed, 0.0)
        assertEquals(100.0, total.recentDistance, 0.0)
    }

    @Test
    fun qualityUsesAccuracyFreshnessAndTrackingChoice() {
        val good =
            DriveState(
                permission = true,
                gpsEnabled = true,
                hasSignal = true,
                fix = fix(1000),
                fixAgeMillis = 1000,
            )
        assertEquals(LocationQuality.GOOD, good.locationQuality)
        assertEquals(LocationQuality.WEAK, good.copy(fixAgeMillis = 4000).locationQuality)
        assertEquals(
            LocationQuality.WEAK,
            good.copy(fix = fix(1000).copy(accuracy = 30f)).locationQuality,
        )
        assertEquals(LocationQuality.SEARCHING, good.copy(hasSignal = false).locationQuality)
        assertEquals(LocationQuality.OFF, good.copy(trackingEnabled = false).locationQuality)
        assertEquals(
            LocationQuality.WEAK,
            good.copy(hasSignal = false, searchAccuracy = 150f).locationQuality,
        )
    }

    @Test
    fun offlineAreaSizeAndNetworkPolicyAreBounded() {
        val small = MapArea(52.371, 4.909, 52.366, 4.900)
        assertTrue(small.downloadable)
        assertTrue(small.tiles in 1..900)
        assertFalse(MapArea(80.0, 170.0, -80.0, -170.0).downloadable)
        assertFalse(small.copy(north = Double.NaN).downloadable)
        assertFalse(small.copy(west = 179.0, east = -179.0).downloadable)
        assertFalse(MapState(network = true, wifi = true).mayDownload(true))
        val enabled = MapState(enabled = true, network = true, wifi = false)
        assertFalse(enabled.mayDownload(true))
        assertTrue(enabled.copy(wifiOnly = false).mayDownload(true))
        assertFalse(enabled.copy(wifi = true).mayDownload(false))
    }

    @Test(expected = IllegalArgumentException::class)
    fun backupRejectsImpossibleGraphTime() {
        JourneyBackup.read(
            JourneyCodec.encodeAll(
                    listOf(trip.copy(samples = listOf(SpeedSample(4000, 10.0, 0)))),
                    null,
                )
                .toByteArray()
        )
    }
}
