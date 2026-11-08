package com.example.myapplication.updates

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpdateInstaller(
    private val service: UpdateService,
    private val prefs: UpdatePreferences,
    private val acquire: () -> Boolean,
    private val release: () -> Unit,
) {
    suspend fun handoff(file: File, manifest: ReleaseManifest, launch: (File) -> Unit): Boolean {
        withContext(Dispatchers.IO) { service.verify(file, manifest) }
        if (!acquire()) return false
        try {
            withContext(Dispatchers.IO) { prefs.pendingCode = manifest.versionCode }
            launch(file)
            return true
        } catch (e: Exception) {
            release()
            prefs.pendingCode = 0
            throw e
        }
    }

    fun returned(success: Boolean) {
        release()
        if (!success) prefs.pendingCode = 0
    }
}

class UpdateSession {
    private var started = false
    private val dismissed = mutableSetOf<Long>()

    fun startup(): Boolean {
        if (started) return false
        started = true
        return true
    }

    fun dismiss(code: Long) {
        dismissed.add(code)
    }

    fun prompt(code: Long, manual: Boolean) = manual || code !in dismissed
}

fun updateCompleted(installed: Long, pending: Long) = pending > 0 && installed >= pending
