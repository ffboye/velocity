package com.example.myapplication.tracking

import android.location.LocationManager
import androidx.core.location.LocationManagerCompat

fun LocationManager.locationEnabled(): Boolean = LocationManagerCompat.isLocationEnabled(this)

// Coarse startup locations inform the UI, but never enter speed/distance calculations.
fun GpsFix.isFreshLocation(now: Long): Boolean =
    latitude.isFinite() &&
        longitude.isFinite() &&
        latitude in -90.0..90.0 &&
        longitude in -180.0..180.0 &&
        accuracy.isFinite() &&
        accuracy >= 0f &&
        now - elapsedMillis in 0..5000
