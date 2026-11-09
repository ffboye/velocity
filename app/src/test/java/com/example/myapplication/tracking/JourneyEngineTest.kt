package com.example.myapplication.tracking

import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class JourneyEngineTest {
    @Test
    fun savedSnapshotsStayUnchangedDuringRecordingAndNextJourney() {
        val engine = JourneyEngine()
        engine.start("first", 0, 1000)
        engine.accept(fix(1000), 1000)
        val checkpoint = engine.snapshot()!!
        engine.tick(1500)
        engine.accept(fix(2000, 0.0001), 2000)
        assertEquals(1, checkpoint.points.size)
        assertEquals(2, engine.points.size)
        val finished = engine.finish(3000)!!
        engine.start("second", 4000, 4000)
        assertTrue(engine.points.isEmpty())
        engine.accept(fix(4000, 0.0002), 4000)
        assertEquals(1, checkpoint.points.size)
        assertEquals(2, finished.points.size)
        assertNotEquals(finished.points.first(), engine.points.first())
    }

    private fun fix(
        time: Long,
        longitude: Double = 0.0,
        accuracy: Float = 3f,
        speed: Double? = 10.0,
    ) = GpsFix(0.0, longitude, accuracy, time, 1700000000000 + time, speed)

    @Test
    fun coarseStartupLocationIsVisibleButCannotRecordSpeedOrDistance() {
        val engine = JourneyEngine()
        engine.start("trip", 0, 1000)
        val coarse = fix(1000, accuracy = 250f)
        assertTrue(coarse.isFreshLocation(1000))
        assertFalse(engine.accept(coarse, 1000))
        assertEquals(0.0, engine.speed, 0.0)
        assertTrue(engine.points.isEmpty())
        assertFalse(coarse.isFreshLocation(6001))
        assertFalse(coarse.copy(latitude = Double.NaN).isFreshLocation(1000))
        assertFalse(coarse.copy(elapsedMillis = 1001).isFreshLocation(1000))
        assertTrue(engine.accept(fix(2000), 2000))
        assertEquals(10.0, engine.speed, 0.0)
    }

    @Test
    fun retryPreservesJourneyAndDoesNotBridgeMissingLocation() {
        val engine = JourneyEngine()
        engine.start("trip", 0, 1000)
        engine.accept(fix(1000), 1000)
        engine.accept(fix(2000, 0.0001), 2000)
        engine.tick(2000)
        val before = engine.snapshot()!!
        engine.resetSignal()
        assertNull(engine.fix)
        assertEquals(0.0, engine.speed, 0.0)
        assertEquals(before, engine.snapshot())
        engine.accept(fix(3000, 1.0), 3000)
        assertEquals(before.distanceMeters, engine.distanceMeters, 0.0)
        assertNotEquals(engine.points.first().segment, engine.points.last().segment)
        assertEquals("trip", engine.finish(4000)!!.id)
    }

    @Test
    fun speedComesFromGpsAndIsConvertedCorrectly() {
        val engine = JourneyEngine()
        assertTrue(engine.accept(fix(1000), 1000))
        assertEquals(36.0, engine.speed * SpeedUnit.KMH.multiplier, 0.01)
        assertEquals(22.369, engine.speed * SpeedUnit.MPH.multiplier, 0.01)
        assertEquals(0.0, engine.distanceMeters, 0.0)
    }

    @Test
    fun recordingAccumulatesRealDistanceTimeAndMaximumSpeed() {
        val engine = JourneyEngine()
        engine.start("trip", 1234, 1000)
        engine.accept(fix(1000), 1000)
        engine.accept(fix(2000, longitude = 0.0001), 2000)
        engine.tick(3000)
        val trip = engine.finish(4000)!!
        assertEquals(3000L, trip.durationMillis)
        assertEquals(11.119, trip.distanceMeters, 0.1)
        assertEquals(10.0, trip.maxSpeed, 0.01)
        assertEquals(2, trip.points.size)
        assertEquals(Recording.IDLE, engine.recording)
    }

    @Test
    fun pausedTimeAndPausedMovementAreExcluded() {
        val engine = JourneyEngine()
        engine.start("trip", 0, 1000)
        engine.accept(fix(1000), 1000)
        engine.accept(fix(2000, 0.0001), 2000)
        engine.togglePause(2000)
        engine.accept(fix(3000, 0.0002), 3000)
        engine.togglePause(12000)
        engine.accept(fix(13000, 0.0003), 13000)
        engine.accept(fix(14000, 0.0004), 14000)
        val trip = engine.finish(15000)!!
        assertEquals(4000L, trip.durationMillis)
        assertEquals(22.239, trip.distanceMeters, 0.1)
        assertNotEquals(trip.points.first().segment, trip.points.last().segment)
    }

    @Test
    fun staleFixesAndBadAccuracyAreRejected() {
        val engine = JourneyEngine()
        assertFalse(engine.accept(fix(1000), 10000))
        assertFalse(engine.accept(fix(1000, accuracy = 100f), 1000))
        assertFalse(engine.accept(fix(1000).copy(latitude = Double.NaN), 1000))
        assertFalse(engine.accept(fix(1000).copy(accuracy = Float.NaN), 1000))
        assertNull(engine.fix)
    }

    @Test
    fun duplicateAndOutOfOrderFixesDoNotAddDistance() {
        val engine = JourneyEngine()
        engine.start("trip", 0, 1000)
        engine.accept(fix(2000), 2000)
        assertFalse(engine.accept(fix(1000, 0.0001), 2000))
        assertFalse(engine.accept(fix(2000, 0.0001), 2000))
        assertEquals(0.0, engine.distanceMeters, 0.0)
    }

    @Test
    fun gpsTeleportIsRejected() {
        val engine = JourneyEngine()
        engine.accept(fix(1000), 1000)
        assertFalse(engine.accept(fix(2000, 1.0), 2000))
        assertEquals(0.0, engine.fix!!.longitude, 0.0)
    }

    @Test
    fun stationaryGpsJitterDoesNotAddDistance() {
        val engine = JourneyEngine()
        engine.start("trip", 0, 1000)
        engine.accept(fix(1000, speed = 0.0), 1000)
        engine.accept(fix(2000, 0.00001, speed = 0.0), 2000)
        engine.accept(fix(3000, -0.00001, speed = 0.0), 3000)
        assertEquals(0.0, engine.distanceMeters, 0.0)
    }

    @Test
    fun speedIsZeroAfterSignalLoss() {
        val engine = JourneyEngine()
        engine.accept(fix(1000), 1000)
        engine.tick(7000)
        assertEquals(0.0, engine.speed, 0.0)
    }

    @Test
    fun longSignalGapStartsNewSegmentWithoutBridgingMissingRoute() {
        val engine = JourneyEngine()
        engine.start("trip", 0, 1000)
        engine.accept(fix(1000), 1000)
        engine.accept(fix(30000, 1.0), 30000)
        assertEquals(0.0, engine.distanceMeters, 0.0)
        assertEquals(2, engine.points.size)
        assertNotEquals(engine.points.first().segment, engine.points.last().segment)
    }

    @Test
    fun fallbackSpeedIsCalculatedWhenReceiverHasNoSpeed() {
        val engine = JourneyEngine()
        engine.accept(fix(1000, speed = null), 1000)
        engine.accept(fix(2000, 0.0001, speed = null), 2000)
        assertTrue(engine.speed > 7.0)
    }

    @Test
    fun startingNextTripClearsPreviousStats() {
        val engine = JourneyEngine()
        engine.start("one", 0, 1000)
        engine.accept(fix(1000), 1000)
        engine.finish(2000)
        engine.start("two", 2000, 3000)
        assertEquals(0L, engine.durationMillis)
        assertEquals(0.0, engine.maxSpeed, 0.0)
        assertTrue(engine.points.isEmpty())
        assertEquals("two", engine.snapshot()!!.id)
    }

    @Test
    fun noRecordingProducesNoJourney() {
        assertNull(JourneyEngine().finish(1000))
    }

    @Test
    fun datelineDistanceUsesShortPath() {
        assertEquals(222.39, JourneyEngine.distance(0.0, 179.999, 0.0, -179.999), 1.0)
    }

    @Test
    fun storageRoundTripsHistoryAndInterruptedDraft() {
        val trip =
            Journey(
                "id",
                100,
                10000,
                123.4,
                12.0,
                listOf(RoutePoint(52.0, 4.0, 1000, 0), RoutePoint(52.1, 4.1, 2000, 1)),
            )
        val loaded =
            JourneyCodec.decodeAll(JourneyCodec.encodeAll(listOf(trip), trip.copy(id = "draft")))
        assertEquals(listOf(trip), loaded.journeys)
        assertEquals("draft", loaded.active!!.id)
    }

    @Test
    fun gpxPreservesCoordinatesTimestampsAndPauseSegments() {
        val trip =
            Journey(
                "id",
                100,
                10000,
                123.4,
                12.0,
                listOf(RoutePoint(52.0, 4.0, 1000, 0), RoutePoint(52.1, 4.1, 2000, 1)),
            )
        val xml = JourneyCodec.gpx(trip)
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val doc = factory.newDocumentBuilder().parse(xml.byteInputStream())
        assertEquals(2, doc.getElementsByTagName("trkseg").length)
        assertEquals(2, doc.getElementsByTagName("trkpt").length)
        assertTrue(xml.contains("1970-01-01T00:00:01.000Z"))
        assertTrue(xml.contains("lat=\"52.0\" lon=\"4.0\""))
    }
}
