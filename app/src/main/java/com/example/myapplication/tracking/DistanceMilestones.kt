package com.example.myapplication.tracking

import kotlin.math.floor

/**
 * One quiet cue per forward crossing; never replay old milestones on enable, resume or unit change.
 */
class DistanceMilestones {
    private var lastDistance = 0.0
    private var lastBucket = 0L
    private var lastUnit: SpeedUnit? = null
    private var wasEnabled = false

    fun observe(
        distance: Double,
        unit: SpeedUnit,
        recording: Recording,
        enabled: Boolean,
    ): Boolean {
        if (!distance.isFinite() || distance < 0) return false
        val bucket = floor(distance / unit.distanceDivisor).toLong()
        val crossed =
            enabled &&
                wasEnabled &&
                recording == Recording.RECORDING &&
                unit == lastUnit &&
                distance >= lastDistance &&
                bucket > lastBucket
        lastDistance = distance
        lastBucket = bucket
        lastUnit = unit
        wasEnabled = enabled && recording == Recording.RECORDING
        return crossed
    }
}
