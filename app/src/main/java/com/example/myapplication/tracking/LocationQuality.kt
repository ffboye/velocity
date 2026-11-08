package com.example.myapplication.tracking

enum class LocationQuality(val label: String) {
    GOOD("Good"),
    WEAK("Weak"),
    SEARCHING("Searching"),
    OFF("Off"),
}

val DriveState.locationQuality: LocationQuality
    get() =
        when {
            !trackingEnabled || !permission || !gpsEnabled -> LocationQuality.OFF
            hasSignal ->
                if (
                    (fix?.accuracy ?: Float.MAX_VALUE) <= 20f &&
                        (fixAgeMillis ?: Long.MAX_VALUE) <= 3000L
                )
                    LocationQuality.GOOD
                else LocationQuality.WEAK
            searchAccuracy != null -> LocationQuality.WEAK
            else -> LocationQuality.SEARCHING
        }
