package com.example.myapplication.updates

import java.io.File
import java.net.URI
import java.security.*
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import org.json.JSONObject

class UpdateException(message: String) : Exception(message)

class NoPublishedRelease : Exception()

data class ReleaseConfig(val owner: String, val repository: String, val publicKey: ByteArray) {
    val apiUrl
        get() = "https://api.github.com/repos/$owner/$repository/releases/latest"

    fun asset(tag: String, name: String): String {
        if (
            !owner.matches(Regex("[A-Za-z0-9-]{1,39}")) ||
                !repository.matches(Regex("[A-Za-z0-9_.-]{1,100}")) ||
                !tag.matches(Regex("v[0-9]+\\.[0-9]+\\.[0-9]+")) ||
                name !in setOf("velocity.apk", "update.json", "update.json.sig")
        ) {
            throw UpdateException("Unsupported release location")
        }
        return "https://github.com/$owner/$repository/releases/download/$tag/$name"
    }
}

data class ReleaseManifest(
    val version: String,
    val versionCode: Long,
    val minimumAndroidSdk: Int,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val releaseNotes: List<String>,
    val packageName: String,
)

data class ApkIdentity(
    val packageName: String,
    val version: String?,
    val code: Long,
    val minSdk: Int,
    val signers: Set<String>,
    val abis: Set<String> = emptySet(),
)

object UpdateSecurity {
    const val MAX_APK_SIZE = 1024L * 1024 * 1024

    fun authenticate(bytes: ByteArray, signature: ByteArray, publicKey: ByteArray) {
        try {
            val key =
                KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(publicKey))
                    as RSAPublicKey
            if (key.modulus.bitLength() < 3072)
                throw UpdateException("The manifest key is too weak")
            val verifier = Signature.getInstance("SHA256withRSA")
            verifier.initVerify(key)
            verifier.update(bytes)
            if (!verifier.verify(signature))
                throw UpdateException("The update manifest signature is untrusted")
        } catch (e: UpdateException) {
            throw e
        } catch (_: Exception) {
            throw UpdateException("The manifest signature or public key is invalid")
        }
    }

    fun parse(bytes: ByteArray, config: ReleaseConfig, tag: String): ReleaseManifest {
        try {
            if (bytes.size > 131072) throw UpdateException("The manifest is too large")
            val json = JSONObject(bytes.toString(Charsets.UTF_8))
            fun integer(name: String): Long {
                val value = json.get(name)
                if (value !is Int && value !is Long) throw UpdateException("Invalid $name")
                return (value as Number).toLong()
            }
            fun string(name: String) =
                json.get(name) as? String ?: throw UpdateException("Invalid $name")
            if (integer("schemaVersion") != 1L) throw UpdateException("Unsupported manifest schema")
            val version = string("version")
            if (tag != "v$version") throw UpdateException("The release version and tag differ")
            val code = integer("versionCode")
            if (code !in 1..2100000000L) throw UpdateException("Invalid versionCode")
            val sdk = integer("minimumAndroidSdk")
            if (sdk !in 24..1000) throw UpdateException("Invalid minimumAndroidSdk")
            val size = integer("sizeBytes")
            if (size !in 1..MAX_APK_SIZE) throw UpdateException("Invalid APK size")
            val hash = string("sha256")
            if (!hash.matches(Regex("[a-f0-9]{64}"))) throw UpdateException("Invalid APK checksum")
            val url = string("apkUrl")
            if (url != config.asset(tag, "velocity.apk"))
                throw UpdateException("Unexpected APK destination")
            val notes = json.getJSONArray("releaseNotes")
            if (notes.length() > 100) throw UpdateException("Too many release notes")
            val lines =
                (0 until notes.length()).map {
                    val line =
                        notes.get(it) as? String ?: throw UpdateException("Invalid release notes")
                    if (line.length > 2000) throw UpdateException("A release note is too long")
                    line
                }
            return ReleaseManifest(
                version,
                code,
                sdk.toInt(),
                url,
                hash,
                size,
                lines,
                string("packageName"),
            )
        } catch (e: UpdateException) {
            throw e
        } catch (_: Exception) {
            throw UpdateException("Malformed release metadata")
        }
    }

    fun destination(url: String, redirect: Boolean) {
        val uri =
            try {
                URI(url)
            } catch (_: Exception) {
                throw UpdateException("Invalid release URL")
            }
        val hosts =
            if (redirect) setOf("release-assets.githubusercontent.com")
            else setOf("api.github.com", "github.com")
        if (
            uri.scheme != "https" ||
                uri.host !in hosts ||
                uri.port != -1 ||
                uri.userInfo != null ||
                uri.fragment != null ||
                uri.path.isNullOrEmpty()
        ) {
            throw UpdateException("Untrusted download destination")
        }
    }

    fun compatible(
        manifest: ReleaseManifest,
        installedCode: Long,
        sdk: Int,
        packageName: String,
        highestCode: Long,
    ) {
        if (manifest.packageName != packageName) throw UpdateException("Wrong application ID")
        if (manifest.minimumAndroidSdk > sdk)
            throw UpdateException("This update requires a newer Android version")
        if (manifest.versionCode < highestCode)
            throw UpdateException("Stale update metadata rejected")
        if (manifest.versionCode <= installedCode)
            throw UpdateException("The APK is not newer than the installed app")
    }

    fun file(file: File, manifest: ReleaseManifest) {
        if (file.length() != manifest.sizeBytes) throw UpdateException("Download size mismatch")
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        if (digest.digest().joinToString("") { "%02x".format(it) } != manifest.sha256)
            throw UpdateException("APK checksum mismatch")
    }

    fun archive(
        candidate: ApkIdentity,
        installed: ApkIdentity,
        manifest: ReleaseManifest,
        sdk: Int,
        abis: Set<String>,
    ) {
        if (
            candidate.packageName != installed.packageName ||
                candidate.packageName != manifest.packageName
        )
            throw UpdateException("Wrong application ID")
        if (
            candidate.code != manifest.versionCode ||
                candidate.version != manifest.version ||
                candidate.code <= installed.code
        )
            throw UpdateException("Incompatible APK version or downgrade")
        if (candidate.minSdk != manifest.minimumAndroidSdk || candidate.minSdk > sdk)
            throw UpdateException("Incompatible Android SDK")
        if (installed.signers.isEmpty() || installed.signers != candidate.signers)
            throw UpdateException("Incompatible APK signing certificate")
        if (candidate.abis.isNotEmpty() && candidate.abis.intersect(abis).isEmpty())
            throw UpdateException("Unsupported device CPU")
    }
}
