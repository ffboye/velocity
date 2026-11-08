package com.example.myapplication.tracking

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.myapplication.MainActivity
import com.example.myapplication.R
import com.example.myapplication.VelocityApplication
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.*

class TrackingService : Service(), LocationListener {
    private val repository
        get() = (application as VelocityApplication).journeys

    private val locations by lazy { getSystemService(LocationManager::class.java) }
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var fusedCallback: LocationCallback? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listening = false
    private var bound = false
    private var foreground = false
    private var seconds = 0

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(
                    NotificationChannel(
                            CHANNEL,
                            "Journey recording",
                            NotificationManager.IMPORTANCE_LOW,
                        )
                        .apply { description = "Shows when Velocity is recording your journey" }
                )
        }
        scope.launch {
            while (isActive) {
                delay(1000)
                repository.tick()
                if (repository.state.value.recording != Recording.IDLE) {
                    if (++seconds % 10 == 0) repository.persist()
                    if (seconds % 5 == 0)
                        getSystemService(NotificationManager::class.java)
                            .notify(NOTIFICATION, notification())
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        bound = true
        connectGps()
        return Binder()
    }

    override fun onRebind(intent: Intent?) {
        bound = true
        connectGps()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        bound = false
        if (repository.state.value.recording == Recording.IDLE) disconnectGps()
        return true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            START -> {
                try {
                    // User must grant precise location while the Activity is visible before this
                    // call.
                    if (!hasPermission()) throw SecurityException()
                    if (Build.VERSION.SDK_INT >= 29)
                        startForeground(
                            NOTIFICATION,
                            notification(),
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
                        )
                    else startForeground(NOTIFICATION, notification())
                    foreground = true
                    connectGps()
                    if (!repository.start()) {
                        repository.message(
                            "Enable precise location and GPS before starting a journey."
                        )
                        endForeground()
                    }
                } catch (_: SecurityException) {
                    repository.message(
                        "Location access is unavailable. Open Settings and allow precise location."
                    )
                    endForeground()
                } catch (_: RuntimeException) {
                    repository.message(
                        "Android couldn't start recording. Keep Velocity open and try again."
                    )
                    endForeground()
                }
            }
            PAUSE -> {
                repository.pauseResume()
                refreshNotification()
            }
            FINISH -> {
                repository.finish()
                endForeground()
            }
            RETRY -> {
                disconnectGps()
                repository.resetSignal()
                connectGps()
                // A bound-only speedometer must not become a permanently started service.
                if (!foreground) stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun hasPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @Suppress("MissingPermission")
    private fun connectGps() {
        val permitted = hasPermission()
        val enabled = locations.locationEnabled()
        repository.permissions(permitted, enabled)
        if (!permitted || !enabled || listening) return
        listening = true
        repository.locationProblem(null)
        var rawGps = false
        try {
            if (locations.allProviders.contains(LocationManager.GPS_PROVIDER)) {
                locations.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000,
                    0f,
                    this,
                    Looper.getMainLooper(),
                )
                rawGps = true
            }
        } catch (_: SecurityException) {
            repository.permissions(false, enabled)
        } catch (_: IllegalArgumentException) {
            // Continue with fused location when a vendor GPS provider is unavailable.
        }
        if (
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this) ==
                ConnectionResult.SUCCESS
        ) {
            val callback =
                object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        if (fusedCallback === this) result.locations.forEach(::onLocationChanged)
                    }
                }
            fusedCallback = callback
            try {
                val request =
                    LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                        .setMinUpdateIntervalMillis(1000L)
                        .setMaxUpdateDelayMillis(1000L)
                        .setMaxUpdateAgeMillis(0L)
                        .setWaitForAccurateLocation(false)
                        .build()
                fused
                    .requestLocationUpdates(request, callback, Looper.getMainLooper())
                    .addOnFailureListener {
                        if (fusedCallback === callback) {
                            fused.removeLocationUpdates(callback)
                            fusedCallback = null
                            if (!rawGps) listening = false
                            repository.locationProblem(
                                if (rawGps)
                                    "Using GPS fallback. Try outdoors, then tap Retry GPS if the signal does not return."
                                else
                                    "Android couldn't start location updates. Check precise location permission, then tap Retry GPS."
                            )
                        }
                    }
            } catch (_: SecurityException) {
                fusedCallback = null
                repository.permissions(false, enabled)
            }
        } else if (!rawGps) {
            listening = false
            repository.locationProblem(
                "No location provider is available. Check device location settings, then tap Retry GPS."
            )
        }
    }

    private fun disconnectGps() {
        locations.removeUpdates(this)
        fusedCallback?.let { fused.removeLocationUpdates(it) }
        fusedCallback = null
        listening = false
        repository.permissions(hasPermission(), locations.locationEnabled())
    }

    override fun onLocationChanged(location: Location) {
        if (
            !listening ||
                !hasPermission() ||
                !locations.locationEnabled() ||
                !location.hasAccuracy()
        )
            return
        val speed =
            if (
                location.hasSpeed() &&
                    (Build.VERSION.SDK_INT < 26 ||
                        !location.hasSpeedAccuracy() ||
                        location.speedAccuracyMetersPerSecond <= 3f)
            )
                location.speed.toDouble()
            else null
        repository.accept(
            GpsFix(
                location.latitude,
                location.longitude,
                location.accuracy,
                location.elapsedRealtimeNanos / 1000000,
                location.time,
                speed,
                if (location.hasAltitude()) location.altitude else null,
                if (location.hasBearing()) location.bearing else null,
            )
        )
    }

    override fun onProviderDisabled(provider: String) {
        repository.permissions(hasPermission(), locations.locationEnabled())
    }

    override fun onProviderEnabled(provider: String) {
        repository.permissions(hasPermission(), locations.locationEnabled())
        connectGps()
    }

    @Deprecated("Required on older Android versions")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    private fun notification(): Notification {
        val state = repository.state.value
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val stop =
            PendingIntent.getService(
                this,
                1,
                Intent(this, TrackingService::class.java).setAction(FINISH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val distance =
            String.format(
                java.util.Locale.getDefault(),
                "%.2f %s",
                state.distanceMeters / state.unit.distanceDivisor,
                state.unit.distanceLabel,
            )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_velocity)
            .setContentTitle(
                if (state.recording == Recording.PAUSED) "Journey paused"
                else "Velocity is recording"
            )
            .setContentText("$distance · ${state.durationMillis / 60000} min")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "Finish & save", stop)
            .build()
    }

    private fun refreshNotification() {
        if (foreground)
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification())
    }

    private fun endForeground() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        foreground = false
        if (!bound) disconnectGps()
        stopSelf()
    }

    override fun onDestroy() {
        if (repository.state.value.recording != Recording.IDLE)
            repository.finish(interrupted = true)
        disconnectGps()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val START = "velocity.START"
        const val PAUSE = "velocity.PAUSE"
        const val FINISH = "velocity.FINISH"
        const val RETRY = "velocity.RETRY"
        private const val CHANNEL = "velocity_recording"
        private const val NOTIFICATION = 21
    }
}
