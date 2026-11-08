package com.example.myapplication

import android.app.Application
import com.example.myapplication.maps.OfflineMaps
import com.example.myapplication.tracking.JourneyRepository
import com.example.myapplication.updates.UpdateController

class VelocityApplication : Application() {
    val journeys by lazy { JourneyRepository(this) }
    val updates by lazy { UpdateController(this, journeys) }
    val maps by lazy { OfflineMaps(this) }
}
