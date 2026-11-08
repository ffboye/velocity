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
import kotlinx.coroutines.*

class TrackingService : Service(), LocationListener {
    private val repository
        get() = (application as VelocityApplication).journeys

    private val locations by lazy { getSystemService(LocationManager::class.java) }
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
        }
        return START_NOT_STICKY
    }

    private fun hasPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @Suppress("MissingPermission")
    private fun connectGps() {
        val permitted = hasPermission()
        val enabled = locations.isProviderEnabled(LocationManager.GPS_PROVIDER)
        repository.permissions(permitted, enabled)
        if (!permitted || listening) return
        try {
            if (locations.allProviders.contains(LocationManager.GPS_PROVIDER)) {
                locations.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000,
                    0f,
                    this,
                    Looper.getMainLooper(),
                )
                listening = true
            } else repository.message("This device has no GPS provider.")
        } catch (_: SecurityException) {
            repository.permissions(false, enabled)
        }
    }

    private fun disconnectGps() {
        locations.removeUpdates(this)
        listening = false
        repository.permissions(
            hasPermission(),
            locations.isProviderEnabled(LocationManager.GPS_PROVIDER),
        )
    }

    override fun onLocationChanged(location: Location) {
        if (!location.hasAccuracy()) return
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
        repository.permissions(hasPermission(), false)
    }

    override fun onProviderEnabled(provider: String) {
        repository.permissions(hasPermission(), true)
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
        private const val CHANNEL = "velocity_recording"
        private const val NOTIFICATION = 21
    }
}
