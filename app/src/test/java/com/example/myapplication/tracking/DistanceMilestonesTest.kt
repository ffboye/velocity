package com.example.myapplication.tracking

import org.junit.Assert.*
import org.junit.Test

class DistanceMilestonesTest {
    @Test
    fun oneCuePerKilometreWithoutRepeatsOrBursts() {
        val counter = DistanceMilestones()
        assertFalse(counter.observe(0.0, SpeedUnit.KMH, Recording.RECORDING, true))
        assertFalse(counter.observe(999.0, SpeedUnit.KMH, Recording.RECORDING, true))
        assertTrue(counter.observe(1000.0, SpeedUnit.KMH, Recording.RECORDING, true))
        assertFalse(counter.observe(1000.0, SpeedUnit.KMH, Recording.RECORDING, true))
        assertTrue(counter.observe(4100.0, SpeedUnit.KMH, Recording.RECORDING, true))
        assertFalse(counter.observe(4101.0, SpeedUnit.KMH, Recording.RECORDING, true))
    }

    @Test
    fun optInAndUnitChangesNeverReplayPastMilestones() {
        val counter = DistanceMilestones()
        assertFalse(counter.observe(3000.0, SpeedUnit.KMH, Recording.RECORDING, false))
        assertFalse(counter.observe(3100.0, SpeedUnit.KMH, Recording.RECORDING, true))
        assertFalse(counter.observe(3200.0, SpeedUnit.MPH, Recording.RECORDING, true))
        assertTrue(counter.observe(3218.688, SpeedUnit.MPH, Recording.RECORDING, true))
        assertFalse(counter.observe(3300.0, SpeedUnit.MPH, Recording.RECORDING, false))
    }

    @Test
    fun pausesAndNewJourneysResetWithoutChiming() {
        val counter = DistanceMilestones()
        counter.observe(900.0, SpeedUnit.KMH, Recording.RECORDING, true)
        assertFalse(counter.observe(1100.0, SpeedUnit.KMH, Recording.PAUSED, true))
        assertFalse(counter.observe(1100.0, SpeedUnit.KMH, Recording.RECORDING, true))
        assertTrue(counter.observe(2000.0, SpeedUnit.KMH, Recording.RECORDING, true))
        assertFalse(counter.observe(0.0, SpeedUnit.KMH, Recording.RECORDING, true))
        assertTrue(counter.observe(1000.0, SpeedUnit.KMH, Recording.RECORDING, true))
        assertFalse(counter.observe(2000.0, SpeedUnit.KMH, Recording.IDLE, true))
        assertFalse(counter.observe(Double.NaN, SpeedUnit.KMH, Recording.RECORDING, true))
    }
}
