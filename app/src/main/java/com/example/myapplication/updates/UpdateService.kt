package com.example.myapplication.updates

import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

interface ReleaseTransport {
    suspend fun read(url: String, limit: Long, chunk: suspend (ByteArray, Int) -> Unit)
}

class HttpsReleaseTransport(
    private val connectionFactory: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    }
) : ReleaseTransport {
    override suspend fun read(url: String, limit: Long, chunk: suspend (ByteArray, Int) -> Unit) =
        withContext(Dispatchers.IO) {
            var destination = url
            for (redirect in 0..3) {
                UpdateSecurity.destination(destination, redirect > 0)
                val connection = connectionFactory(URL(destination))
                val watcher =
                    launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                        try {
                            awaitCancellation()
                        } finally {
                            connection.disconnect()
                        }
                    }
                try {
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = 15000
                    connection.readTimeout = 15000
                    connection.setRequestProperty("Accept-Encoding", "identity")
                    connection.setRequestProperty("User-Agent", "Velocity-Android")
                    if (destination.startsWith("https://api.github.com/")) {
                        connection.setRequestProperty("Accept", "application/vnd.github+json")
                        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                    }
                    val status = connection.responseCode
                    if (status in setOf(301, 302, 303, 307, 308)) {
                        if (url.startsWith("https://api.github.com/") || redirect == 3)
                            throw UpdateException("Unexpected release redirect")
                        destination =
                            connection.getHeaderField("Location")
                                ?: throw UpdateException("Missing redirect destination")
                        UpdateSecurity.destination(destination, true)
                        continue
                    }
                    if (
                        status == 404 &&
                            url.startsWith("https://api.github.com/") &&
                            url.endsWith("/releases/latest")
                    )
                        throw NoPublishedRelease()
                    if (status != 200) throw UpdateException("Release server returned HTTP $status")
                    if (connection.contentLengthLong > limit)
                        throw UpdateException("Release asset exceeds the size limit")
                    if (
                        connection.contentEncoding != null &&
                            connection.contentEncoding != "identity"
                    )
                        throw UpdateException("Unexpected content encoding")
                    var received = 0L
                    connection.inputStream.buffered().use { input ->
                        val buffer = ByteArray(65536)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            received += count
                            if (received > limit)
                                throw UpdateException("Download exceeds expected size")
                            chunk(buffer, count)
                        }
                    }
                    return@withContext
                } finally {
                    watcher.cancel()
                    connection.disconnect()
                }
            }
            throw UpdateException("Too many redirects")
        }
}

interface ApkInspector {
    fun verify(file: File, manifest: ReleaseManifest)
}

interface UpdatePreferences {
    var automatic: Boolean
    var lastCheck: Long
    var highestCode: Long
    var pendingCode: Long
}

class UpdateService(
    val config: ReleaseConfig,
    private val transport: ReleaseTransport,
    private val inspector: ApkInspector,
    private val preferences: UpdatePreferences,
    private val directory: File,
    private val installedCode: Long,
    private val sdk: Int,
    private val packageName: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val downloads = Mutex()

    private suspend fun bytes(url: String, limit: Long): ByteArray {
        val result = ByteArrayOutputStream()
        transport.read(url, limit) { data, count -> result.write(data, 0, count) }
        return result.toByteArray()
    }

    suspend fun check(): ReleaseManifest? =
        withContext(Dispatchers.IO) {
            val release = JSONObject(bytes(config.apiUrl, 2097152).toString(Charsets.UTF_8))
            if (release.optBoolean("draft", true) || release.optBoolean("prerelease", true))
                throw UpdateException("Only stable published releases are supported")
            val tag = release.getString("tag_name")
            val metadata = bytes(config.asset(tag, "update.json"), 131072)
            val signature = bytes(config.asset(tag, "update.json.sig"), 8192)
            UpdateSecurity.authenticate(metadata, signature, config.publicKey)
            val manifest = UpdateSecurity.parse(metadata, config, tag)
            if (manifest.packageName != packageName) throw UpdateException("Wrong application ID")
            if (manifest.versionCode < preferences.highestCode)
                throw UpdateException("Stale update metadata rejected")
            if (manifest.minimumAndroidSdk > sdk)
                throw UpdateException("This release requires a newer Android version")
            preferences.highestCode = manifest.versionCode
            preferences.lastCheck = clock()
            if (manifest.versionCode <= installedCode) null else manifest
        }

    suspend fun download(manifest: ReleaseManifest, progress: (Float) -> Unit): File =
        downloads.withLock {
            withContext(Dispatchers.IO) {
                UpdateSecurity.compatible(
                    manifest,
                    installedCode,
                    sdk,
                    packageName,
                    preferences.highestCode,
                )
                directory.mkdirs()
                val complete = File(directory, "${manifest.versionCode}-${manifest.sha256}.apk")
                if (complete.exists()) {
                    try {
                        verify(complete, manifest)
                        progress(1f)
                        return@withContext complete
                    } catch (_: Exception) {
                        complete.delete()
                    }
                }
                directory.listFiles()?.filter { it != complete }?.forEach { it.delete() }
                val temporary = File(directory, "download.part")
                try {
                    var received = 0L
                    temporary.outputStream().use { output ->
                        transport.read(manifest.apkUrl, manifest.sizeBytes) { data, count ->
                            currentCoroutineContext().ensureActive()
                            output.write(data, 0, count)
                            received += count
                            progress((received.toDouble() / manifest.sizeBytes).toFloat())
                        }
                        output.fd.sync()
                    }
                    verify(temporary, manifest)
                    currentCoroutineContext().ensureActive()
                    if (!temporary.renameTo(complete))
                        throw UpdateException("Cannot save the verified update")
                    complete
                } finally {
                    temporary.delete()
                }
            }
        }

    fun verify(file: File, manifest: ReleaseManifest) {
        UpdateSecurity.compatible(
            manifest,
            installedCode,
            sdk,
            packageName,
            preferences.highestCode,
        )
        UpdateSecurity.file(file, manifest)
        inspector.verify(file, manifest)
    }

    suspend fun clean() =
        downloads.withLock {
            withContext(Dispatchers.IO) {
                directory
                    .listFiles()
                    ?.filter {
                        it.name.endsWith(".part") ||
                            it.name.substringBefore('-').toLongOrNull()?.let { code ->
                                code <= installedCode
                            } == true
                    }
                    ?.forEach { it.delete() }
            }
        }
}
