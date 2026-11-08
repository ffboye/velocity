package com.example.myapplication.tracking

import android.content.Context
import android.os.SystemClock
import androidx.core.content.edit
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class JourneyRepository(context: Context) {
    private val settings =
        context.getSharedPreferences("velocity_preferences", Context.MODE_PRIVATE)
    private val storage = JourneyStorage(context.filesDir)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private data class SaveRequest(val revision: Long, val snapshot: StoredJourneys)

    private val saves = Channel<SaveRequest>(Channel.CONFLATED)
    private var saveRevision = 0L
    private val engine = JourneyEngine()
    private val mutable =
        MutableStateFlow(
            DriveState(
                unit =
                    SpeedUnit.entries.firstOrNull { it.name == settings.getString("unit", "KMH") }
                        ?: SpeedUnit.KMH,
                keepAwake = settings.getBoolean("keep_awake", true),
                speedLimit = settings.getFloat("speed_limit", 0f).toDouble(),
            )
        )
    val state = mutable.asStateFlow()
    private var storageHealthy = true
    private var installationInProgress = false
    private var observedAt = 0L

    // All recorder commands and installer handoffs run on the main thread.
    fun tryBeginInstallation(): Boolean {
        if (
            mutable.value.loading ||
                mutable.value.saving ||
                mutable.value.saveFailed ||
                engine.recording != Recording.IDLE ||
                installationInProgress
        )
            return false
        installationInProgress = true
        return true
    }

    fun endInstallation() {
        installationInProgress = false
    }

    init {
        scope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { storage.load() }
                val recovered = saved.active?.copy(interrupted = true)
                mutable.update {
                    it.copy(
                        loading = false,
                        journeys =
                            (listOfNotNull(recovered) + saved.journeys).distinctBy { journey ->
                                journey.id
                            },
                        message =
                            if (recovered != null) "An interrupted journey was recovered and saved."
                            else null,
                    )
                }
                if (recovered != null) persist()
            } catch (_: Exception) {
                storageHealthy = false
                mutable.update {
                    it.copy(
                        loading = false,
                        storageReady = false,
                        message =
                            "Saved journeys could not be read. Existing data has been kept. Recording is disabled until this is resolved.",
                    )
                }
            }
        }
        scope.launch {
            for (request in saves) {
                try {
                    withContext(Dispatchers.IO) {
                        storage.save(request.snapshot.journeys, request.snapshot.active)
                    }
                    mutable.update {
                        it.copy(saveFailed = false, saving = request.revision != saveRevision)
                    }
                } catch (_: Exception) {
                    mutable.update {
                        it.copy(saveFailed = true, saving = request.revision != saveRevision)
                    }
                    message("Couldn't save journey data. Free some storage, then tap Retry save.")
                }
            }
        }
    }

    fun permissions(granted: Boolean, enabled: Boolean) {
        if (!granted || !enabled) {
            resetSignal()
            receiver(ReceiverStatus.IDLE)
        }
        mutable.update {
            it.copy(
                permission = granted,
                gpsEnabled = enabled,
                hasSignal = if (granted && enabled) it.hasSignal else false,
                speed = if (granted && enabled) it.speed else 0.0,
            )
        }
    }

    fun accept(fix: GpsFix) {
        val now = SystemClock.elapsedRealtime()
        if (fix.isFreshLocation(now)) {
            observedAt = now
            mutable.update { it.copy(searchAccuracy = fix.accuracy, locationProblem = null) }
        }
        if (engine.accept(fix, now)) publish()
    }

    fun locationProblem(problem: String?) {
        mutable.update { it.copy(locationProblem = problem) }
    }

    fun receiver(status: ReceiverStatus) {
        mutable.update { it.copy(gnss = GnssState(status), gnssFresh = false) }
    }

    fun satellites(readings: List<Satellite>) {
        if (!mutable.value.permission || !mutable.value.gpsEnabled) return
        mutable.update {
            it.copy(
                gnss =
                    it.gnss.copy(
                        status = ReceiverStatus.LISTENING,
                        satellites = GnssState.normalize(readings),
                        updatedAt = SystemClock.elapsedRealtime(),
                    ),
                gnssFresh = true,
            )
        }
    }

    fun firstSatelliteFix(millis: Int) {
        if (millis >= 0) mutable.update { it.copy(gnss = it.gnss.copy(firstFixMillis = millis)) }
    }

    fun resetSignal() {
        engine.resetSignal()
        observedAt = 0L
        mutable.update { it.copy(searchAccuracy = null, locationProblem = null) }
        publish()
    }

    fun tick() {
        engine.tick(SystemClock.elapsedRealtime())
        publish()
    }

    private fun publish() {
        val now = SystemClock.elapsedRealtime()
        mutable.update {
            it.copy(
                fix = engine.fix,
                fixAgeMillis = engine.fix?.let { p -> (now - p.elapsedMillis).coerceAtLeast(0) },
                gnssFresh = it.permission && it.gpsEnabled && it.gnss.isFresh(now),
                searchAccuracy = if (now - observedAt <= 5000) it.searchAccuracy else null,
                hasSignal =
                    it.permission &&
                        it.gpsEnabled &&
                        engine.fix?.let { p -> now - p.elapsedMillis <= 5000 } == true,
                speed = if (it.permission && it.gpsEnabled) engine.speed else 0.0,
                recording = engine.recording,
                durationMillis = engine.durationMillis,
                distanceMeters = engine.distanceMeters,
                maxSpeed = engine.maxSpeed,
                points = engine.points,
            )
        }
    }

    fun start(): Boolean {
        if (
            mutable.value.loading ||
                installationInProgress ||
                !storageHealthy ||
                !mutable.value.permission ||
                !mutable.value.gpsEnabled
        )
            return false
        engine.start(
            UUID.randomUUID().toString(),
            System.currentTimeMillis(),
            SystemClock.elapsedRealtime(),
        )
        publish()
        persist()
        return true
    }

    fun pauseResume() {
        engine.togglePause(SystemClock.elapsedRealtime())
        publish()
        persist()
    }

    fun finish(interrupted: Boolean = false) {
        val journey =
            engine.finish(SystemClock.elapsedRealtime())?.copy(interrupted = interrupted) ?: return
        mutable.update {
            it.copy(
                journeys = listOf(journey) + it.journeys,
                message =
                    if (interrupted) "Recording stopped. Your interrupted journey was saved."
                    else "Journey saved. Find it in Journeys.",
            )
        }
        publish()
        persist()
    }

    fun persist() {
        if (storageHealthy && !mutable.value.loading) {
            mutable.update { it.copy(saving = true) }
            saves.trySend(
                SaveRequest(
                    ++saveRevision,
                    StoredJourneys(mutable.value.journeys, engine.snapshot()),
                )
            )
        }
    }

    fun delete(id: String) {
        if (!storageHealthy) return
        mutable.update { it.copy(journeys = it.journeys.filterNot { trip -> trip.id == id }) }
        persist()
    }

    fun restore(journeys: List<Journey>): Boolean {
        if (
            !storageHealthy ||
                mutable.value.loading ||
                engine.recording != Recording.IDLE ||
                installationInProgress
        )
            return false
        mutable.update {
            it.copy(
                journeys = JourneyBackup.merge(it.journeys, journeys),
                message = "Journey backup restored. Existing journeys were kept.",
            )
        }
        persist()
        return true
    }

    fun unit(unit: SpeedUnit) {
        settings.edit { putString("unit", unit.name) }
        mutable.update { it.copy(unit = unit) }
    }

    fun keepAwake(enabled: Boolean) {
        settings.edit { putBoolean("keep_awake", enabled) }
        mutable.update { it.copy(keepAwake = enabled) }
    }

    fun limit(speed: Double) {
        settings.edit { putFloat("speed_limit", speed.toFloat()) }
        mutable.update { it.copy(speedLimit = speed) }
    }

    fun message(text: String?) {
        mutable.update { it.copy(message = text) }
    }
}
