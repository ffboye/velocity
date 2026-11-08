package com.example.myapplication.updates

import java.io.File
import java.io.IOException
import java.security.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateServiceTest {
    @get:Rule val folder = TemporaryFolder()
    private val config = ReleaseConfig("ffboye", "velocity", keys.public.encoded)
    private val apk = "APK bytes fixture; Android archive inspection is injected".toByteArray()
    private val hash =
        MessageDigest.getInstance("SHA-256").digest(apk).joinToString("") { "%02x".format(it) }

    private fun metadata(code: Any = 4) =
        JSONObject()
            .apply {
                put("schemaVersion", 1)
                put("version", "1.1.2")
                put("versionCode", code)
                put("minimumAndroidSdk", 24)
                put("apkUrl", config.asset("v1.1.2", "velocity.apk"))
                put("sha256", hash)
                put("sizeBytes", apk.size)
                put("releaseNotes", listOf("Improved journeys"))
                put("packageName", "com.example.myapplication")
            }
            .toString()
            .toByteArray()

    private fun sign(bytes: ByteArray) =
        Signature.getInstance("SHA256withRSA").run {
            initSign(keys.private)
            update(bytes)
            sign()
        }

    private class Preferences : UpdatePreferences {
        override var automatic = true
        override var lastCheck = 0L
        override var highestCode = 0L
        override var pendingCode = 0L
    }

    private inner class Transport(var metadata: ByteArray = metadata()) : ReleaseTransport {
        var signature = sign(metadata)
        var offline = false
        var interrupted = false
        var cancelled = false
        var corrupt = false
        var apkRequests = 0

        override suspend fun read(
            url: String,
            limit: Long,
            chunk: suspend (ByteArray, Int) -> Unit,
        ) {
            if (offline) throw IOException("offline")
            val body =
                when {
                    url == config.apiUrl ->
                        """{"draft":false,"prerelease":false,"tag_name":"v1.1.2"}""".toByteArray()
                    url.endsWith("update.json.sig") -> signature
                    url.endsWith("update.json") -> metadata
                    else -> {
                        apkRequests++
                        if (interrupted || cancelled) {
                            chunk(apk, 4)
                            if (cancelled) throw CancellationException("cancelled")
                            throw IOException("connection interrupted")
                        }
                        if (corrupt) apk.copyOf().apply { this[0] = 0 } else apk
                    }
                }
            check(body.size <= limit)
            chunk(body, body.size)
        }
    }

    private fun service(
        transport: Transport,
        installed: Long = 3,
        prefs: Preferences = Preferences(),
        inspector: ApkInspector =
            object : ApkInspector {
                override fun verify(file: File, manifest: ReleaseManifest) {}
            },
    ) =
        UpdateService(
            config,
            transport,
            inspector,
            prefs,
            folder.root,
            installed,
            37,
            "com.example.myapplication",
            clock = { 1234 },
        )

    private fun rejects(block: () -> Unit) {
        try {
            block()
            fail("Expected security rejection")
        } catch (_: UpdateException) {}
    }

    @Test
    fun newVersionAvailable() = runBlocking {
        assertEquals(4L, service(Transport()).check()!!.versionCode)
    }

    @Test
    fun noNewVersionUpdatesLastSuccessfulCheck() = runBlocking {
        val prefs = Preferences()
        assertNull(service(Transport(), installed = 4, prefs = prefs).check())
        assertEquals(1234L, prefs.lastCheck)
    }

    @Test
    fun invalidVersionCodesRejected() {
        listOf(-1, 0, 1.5, "4", 2100000001L).forEach {
            rejects { UpdateSecurity.parse(metadata(it), config, "v1.1.2") }
        }
    }

    @Test
    fun malformedMetadataRejected() {
        rejects { UpdateSecurity.parse("broken".toByteArray(), config, "v1.1.2") }
        rejects { UpdateSecurity.parse("{}".toByteArray(), config, "v1.1.2") }
    }

    @Test
    fun untrustedSignatureDoesNotAdvanceLastCheck() = runBlocking {
        val prefs = Preferences()
        val transport = Transport().apply { signature[0] = (signature[0].toInt() xor 1).toByte() }
        rejects { runBlocking { service(transport, prefs = prefs).check() } }
        assertEquals(0L, prefs.lastCheck)
    }

    @Test
    fun changedManifestDoesNotAuthenticate() {
        val bytes = metadata()
        val signed = sign(bytes)
        bytes[5] = (bytes[5].toInt() xor 1).toByte()
        rejects { UpdateSecurity.authenticate(bytes, signed, config.publicKey) }
    }

    @Test
    fun checksumMismatchCleansPartialFile() = runBlocking {
        val service = service(Transport().apply { corrupt = true })
        rejects { runBlocking { service.download(service.check()!!) {} } }
        assertTrue(folder.root.listFiles()!!.isEmpty())
    }

    @Test
    fun interruptedDownloadCanRetry() = runBlocking {
        val transport = Transport().apply { interrupted = true }
        val service = service(transport)
        val manifest = service.check()!!
        try {
            service.download(manifest) {}
            fail()
        } catch (_: IOException) {}
        assertTrue(folder.root.listFiles()!!.isEmpty())
        transport.interrupted = false
        assertTrue(service.download(manifest) {}.isFile)
    }

    @Test
    fun cancelledDownloadCleansPartialFile() = runBlocking {
        val service = service(Transport().apply { cancelled = true })
        val manifest = service.check()!!
        try {
            service.download(manifest) {}
            fail()
        } catch (_: CancellationException) {}
        assertTrue(folder.root.listFiles()!!.isEmpty())
    }

    @Test
    fun offlineCheckDoesNotAdvanceTimestamp() = runBlocking {
        val prefs = Preferences()
        try {
            service(Transport().apply { offline = true }, prefs = prefs).check()
            fail()
        } catch (_: IOException) {}
        assertEquals(0L, prefs.lastCheck)
    }

    @Test
    fun verifiedCacheIsReusedAndTamperedCacheIsReplaced() = runBlocking {
        val transport = Transport()
        val service = service(transport)
        val manifest = service.check()!!
        val cache = service.download(manifest) {}
        service.download(manifest) {}
        assertEquals(1, transport.apkRequests)
        cache.writeText("tampered")
        service.download(manifest) {}
        assertEquals(2, transport.apkRequests)
    }

    private val installed =
        ApkIdentity("com.example.myapplication", "1.1.1", 3, 24, setOf("trusted"))
    private val candidate
        get() = installed.copy(version = "1.1.2", code = 4)

    private fun archive(value: ApkIdentity) =
        UpdateSecurity.archive(
            value,
            installed,
            UpdateSecurity.parse(metadata(), config, "v1.1.2"),
            37,
            setOf("arm64-v8a"),
        )

    @Test
    fun wrongPackageRejected() {
        rejects { archive(candidate.copy(packageName = "other.app")) }
    }

    @Test
    fun wrongCertificateRejected() {
        rejects { archive(candidate.copy(signers = setOf("untrusted"))) }
    }

    @Test
    fun unsignedArchiveRejected() {
        rejects { archive(candidate.copy(signers = emptySet())) }
    }

    @Test
    fun downgradeRejected() {
        rejects { archive(candidate.copy(code = 3)) }
    }

    @Test
    fun incompatibleSdkRejected() {
        rejects { archive(candidate.copy(minSdk = 38)) }
    }

    @Test
    fun unsupportedAbiRejected() {
        rejects { archive(candidate.copy(abis = setOf("x86"))) }
    }

    @Test
    fun compatibleArchiveAccepted() {
        archive(candidate)
    }

    @Test
    fun staleSignedMetadataRejected() = runBlocking {
        rejects {
            runBlocking {
                service(Transport(), prefs = Preferences().apply { highestCode = 5 }).check()
            }
        }
    }

    @Test
    fun wrongSignedUrlRejected() {
        val json =
            JSONObject(metadata().toString(Charsets.UTF_8))
                .put("apkUrl", "https://github.com/attacker/evil.apk")
        rejects { UpdateSecurity.parse(json.toString().toByteArray(), config, "v1.1.2") }
    }

    @Test
    fun unsafeHostsRejected() {
        listOf(
                "http://github.com/file",
                "https://github.com.evil.test/file",
                "https://user@github.com/file",
                "https://github.com:443/file",
                "https://github.com/file#fragment",
            )
            .forEach { rejects { UpdateSecurity.destination(it, false) } }
        rejects { UpdateSecurity.destination("https://evil.test/apk", true) }
        UpdateSecurity.destination(
            "https://release-assets.githubusercontent.com/asset?token=test",
            true,
        )
    }

    @Test
    fun activeJourneyDefersInstallationAndCancellationSupportsRetry() = runBlocking {
        val prefs = Preferences()
        val service = service(Transport(), prefs = prefs)
        val manifest = service.check()!!
        val file = service.download(manifest) {}
        var active = true
        var held = false
        var launches = 0
        val installer =
            UpdateInstaller(
                service,
                prefs,
                {
                    if (active || held) false
                    else {
                        held = true
                        true
                    }
                },
                { held = false },
            )
        assertFalse(installer.handoff(file, manifest) { launches++ })
        assertEquals(0, launches)
        active = false
        assertTrue(installer.handoff(file, manifest) { launches++ })
        assertEquals(4L, prefs.pendingCode)
        installer.returned(false)
        assertFalse(held)
        assertEquals(0L, prefs.pendingCode)
        assertTrue(file.isFile)
        assertTrue(installer.handoff(file, manifest) { launches++ })
        assertEquals(2, launches)
    }

    @Test
    fun installerRevalidatesBytesBeforeHandoff() = runBlocking {
        val prefs = Preferences()
        val service = service(Transport(), prefs = prefs)
        val manifest = service.check()!!
        val file = service.download(manifest) {}
        file.writeText("tampered")
        val installer =
            UpdateInstaller(
                service,
                prefs,
                {
                    fail("Cannot acquire before validation")
                    false
                },
                {},
            )
        rejects { runBlocking { installer.handoff(file, manifest) { fail("Cannot launch") } } }
    }

    @Test
    fun launchErrorReleasesInstallerGate() = runBlocking {
        val prefs = Preferences()
        val service = service(Transport(), prefs = prefs)
        val manifest = service.check()!!
        val file = service.download(manifest) {}
        var held = false
        val installer =
            UpdateInstaller(
                service,
                prefs,
                {
                    held = true
                    true
                },
                { held = false },
            )
        try {
            installer.handoff(file, manifest) { throw IllegalStateException("no installer") }
            fail()
        } catch (_: IllegalStateException) {}
        assertFalse(held)
        assertEquals(0L, prefs.pendingCode)
    }

    @Test
    fun dismissedVersionIsNotRepeatedWithinSession() {
        val session = UpdateSession()
        assertTrue(session.startup())
        assertFalse(session.startup())
        session.dismiss(4)
        assertFalse(session.prompt(4, false))
        assertTrue(session.prompt(4, true))
        assertTrue(session.prompt(5, false))
    }

    @Test
    fun actualInstalledVersionDeterminesSuccess() {
        assertFalse(updateCompleted(3, 4))
        assertTrue(updateCompleted(4, 4))
        assertFalse(updateCompleted(4, 0))
    }

    @Test
    fun reopeningCanCheckAgainWithoutRepeatingDismissedVersion() {
        val session = UpdateSession()
        assertTrue(session.startup(1000))
        session.dismiss(4)
        assertFalse(session.startup(3000))
        assertTrue(session.startup(61000))
        assertFalse(session.prompt(4, false))
        assertTrue(session.prompt(5, false))
    }

    companion object {
        private val keys =
            KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair()
    }
}
