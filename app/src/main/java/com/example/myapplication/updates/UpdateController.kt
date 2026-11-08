package com.example.myapplication.updates

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Base64
import com.example.myapplication.BuildConfig
import com.example.myapplication.tracking.JourneyRepository
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class UpdatePhase {
    IDLE,
    CHECKING,
    AVAILABLE,
    DOWNLOADING,
    READY,
    VERIFYING,
    INSTALLING,
    ERROR,
}

data class UpdateState(
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val manifest: ReleaseManifest? = null,
    val automatic: Boolean = true,
    val lastCheck: Long = 0,
    val progress: Float = 0f,
    val dialog: Boolean = false,
    val message: String? = null,
)

/** Process-scoped state: rotating or backgrounding the Activity does not cancel downloads. */
class UpdateController(context: Context, journeys: JourneyRepository) {
    private val prefs = AndroidUpdatePreferences(context)
    private val config =
        ReleaseConfig(
            BuildConfig.UPDATE_OWNER,
            BuildConfig.UPDATE_REPOSITORY,
            Base64.decode(BuildConfig.UPDATE_PUBLIC_KEY, Base64.DEFAULT),
        )
    private val service =
        UpdateService(
            config,
            HttpsReleaseTransport(),
            AndroidApkInspector(context),
            prefs,
            File(context.filesDir, "updates"),
            BuildConfig.VERSION_CODE.toLong(),
            Build.VERSION.SDK_INT,
            context.packageName,
        )
    private val installer =
        UpdateInstaller(service, prefs, journeys::tryBeginInstallation, journeys::endInstallation)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val session = UpdateSession()
    private val mutable =
        MutableStateFlow(UpdateState(automatic = prefs.automatic, lastCheck = prefs.lastCheck))
    val state = mutable.asStateFlow()
    private var operation: Job? = null
    private var verified: File? = null

    init {
        if (updateCompleted(BuildConfig.VERSION_CODE.toLong(), prefs.pendingCode)) {
            mutable.update {
                it.copy(message = "Velocity updated successfully to ${BuildConfig.VERSION_NAME}.")
            }
            prefs.pendingCode = 0
        }
        scope.launch { service.clean() }
    }

    fun startup() {
        if (
            prefs.automatic &&
                mutable.value.phase !in
                    setOf(UpdatePhase.DOWNLOADING, UpdatePhase.VERIFYING, UpdatePhase.INSTALLING) &&
                session.startup(SystemClock.elapsedRealtime())
        )
            check(manual = false)
    }

    fun automatic(value: Boolean) {
        prefs.automatic = value
        mutable.update { it.copy(automatic = value) }
    }

    fun check(manual: Boolean = true) {
        if (operation?.isActive == true) return
        val previous = mutable.value.manifest
        operation =
            scope.launch {
                mutable.update { it.copy(phase = UpdatePhase.CHECKING, message = null) }
                try {
                    val manifest = service.check()
                    val cached =
                        manifest != null &&
                            previous?.sha256 == manifest.sha256 &&
                            verified?.isFile == true
                    if (!cached) verified = null
                    mutable.update {
                        it.copy(
                            phase =
                                if (manifest == null) UpdatePhase.IDLE
                                else if (cached) UpdatePhase.READY else UpdatePhase.AVAILABLE,
                            manifest = manifest,
                            lastCheck = prefs.lastCheck,
                            dialog =
                                manifest != null && session.prompt(manifest.versionCode, manual),
                            message =
                                if (manifest == null && manual)
                                    "You're running the latest published version."
                                else null,
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: NoPublishedRelease) {
                    verified = null
                    mutable.update {
                        it.copy(
                            phase = UpdatePhase.IDLE,
                            manifest = null,
                            dialog = false,
                            message =
                                if (manual) "No update has been published to ffboye/velocity yet."
                                else null,
                        )
                    }
                } catch (e: Exception) {
                    failure(e, manual)
                }
            }
    }

    fun later() {
        mutable.value.manifest?.let { session.dismiss(it.versionCode) }
        mutable.update { it.copy(dialog = false) }
    }

    fun show() {
        mutable.update { it.copy(dialog = true) }
    }

    fun download() {
        if (operation?.isActive == true) return
        val manifest = mutable.value.manifest ?: return
        operation =
            scope.launch {
                mutable.update {
                    it.copy(phase = UpdatePhase.DOWNLOADING, progress = 0f, message = null)
                }
                try {
                    verified =
                        service.download(manifest) { progress ->
                            mutable.update { it.copy(progress = progress) }
                        }
                    mutable.update {
                        it.copy(
                            phase = UpdatePhase.READY,
                            message = "Download complete. The update has been verified.",
                        )
                    }
                } catch (e: CancellationException) {
                    mutable.update {
                        it.copy(phase = UpdatePhase.AVAILABLE, message = "Download cancelled.")
                    }
                    throw e
                } catch (e: Exception) {
                    failure(e)
                }
            }
    }

    fun cancel() {
        operation?.cancel()
    }

    fun install(launch: (File) -> Unit) {
        if (operation?.isActive == true) return
        val manifest = mutable.value.manifest ?: return
        val file = verified ?: return
        operation =
            scope.launch {
                mutable.update { it.copy(phase = UpdatePhase.VERIFYING, message = null) }
                try {
                    val opened =
                        installer.handoff(file, manifest) { apk ->
                            mutable.update { it.copy(phase = UpdatePhase.INSTALLING) }
                            launch(apk)
                        }
                    if (!opened)
                        mutable.update {
                            it.copy(
                                phase = UpdatePhase.READY,
                                message =
                                    "Finish your journey before installing. The verified update will wait here.",
                            )
                        }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failure(e)
                }
            }
    }

    fun permissionResult(granted: Boolean) {
        installer.returned(false)
        mutable.update {
            it.copy(
                phase = UpdatePhase.READY,
                message =
                    if (granted) "Permission granted. Tap Install update to continue."
                    else "Installation permission was not granted. You can try again later.",
            )
        }
    }

    fun installResult(success: Boolean, cancelled: Boolean) {
        installer.returned(success)
        mutable.update {
            it.copy(
                phase = UpdatePhase.READY,
                message =
                    when {
                        success -> "Android approved the update."
                        cancelled ->
                            "Installation cancelled. The verified download is ready to retry."
                        else -> "Android couldn't install this update. You can retry."
                    },
            )
        }
    }

    private fun failure(error: Exception, showDialog: Boolean = mutable.value.dialog) {
        val message =
            when (error) {
                is java.io.IOException -> "Couldn't reach GitHub. Check your connection and retry."
                is SecurityException ->
                    "Android blocked installation. Review installation permission."
                is android.content.ActivityNotFoundException ->
                    "Android's package installer is unavailable."
                is UpdateException -> error.message
                else -> "Update failed. Please try again."
            }
        mutable.update {
            it.copy(phase = UpdatePhase.ERROR, message = message, dialog = showDialog)
        }
    }
}
