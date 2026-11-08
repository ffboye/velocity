package com.example.myapplication.updates

import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HttpsReleaseTransportTest {
    private class Connection(
        url: URL,
        private val code: Int = 200,
        private val location: String? = null,
    ) : HttpURLConnection(url) {
        var closed = false

        override fun disconnect() {
            closed = true
        }

        override fun usingProxy() = false

        override fun connect() {}

        override fun getResponseCode() = code

        override fun getHeaderField(name: String): String? =
            if (name == "Location") location else null

        override fun getContentLengthLong() = 2L

        override fun getInputStream() = ByteArrayInputStream("{}".toByteArray())
    }

    @Test
    fun validGithubAssetRedirectIsFollowedWithoutAutomaticRedirects() = runBlocking {
        val connections = mutableListOf<Connection>()
        val transport = HttpsReleaseTransport { url ->
            Connection(
                    url,
                    if (connections.isEmpty()) 302 else 200,
                    "https://release-assets.githubusercontent.com/asset",
                )
                .also { connections.add(it) }
        }
        var count = 0
        transport.read(
            "https://github.com/ffboye/velocity/releases/download/v1.1.1/velocity.apk",
            100,
        ) { _, n ->
            count += n
        }
        assertEquals(2, count)
        assertEquals(2, connections.size)
        assertTrue(connections.all { it.closed && !it.instanceFollowRedirects })
    }

    @Test
    fun apiRedirectIsNotFollowed() = runBlocking {
        var requests = 0
        val transport = HttpsReleaseTransport { url ->
            requests++
            Connection(url, 302, "https://release-assets.githubusercontent.com/asset")
        }
        try {
            transport.read("https://api.github.com/repos/ffboye/velocity/releases/latest", 100) {
                _,
                _ ->
            }
            fail()
        } catch (_: UpdateException) {}
        assertEquals(1, requests)
    }

    @Test
    fun unsafeRedirectIsRejectedBeforeContactingHost() = runBlocking {
        var requests = 0
        val transport = HttpsReleaseTransport { url ->
            requests++
            Connection(url, 302, "https://attacker.test/file")
        }
        try {
            transport.read(
                "https://github.com/ffboye/velocity/releases/download/v1.1.1/velocity.apk",
                100,
            ) { _, _ ->
            }
            fail()
        } catch (_: UpdateException) {}
        assertEquals(1, requests)
    }

    @Test
    fun emptyRepositoryHasDistinctNoReleaseResult() = runBlocking {
        val transport = HttpsReleaseTransport { url -> Connection(url, 404) }
        try {
            transport.read("https://api.github.com/repos/ffboye/velocity/releases/latest", 100) {
                _,
                _ ->
            }
            fail()
        } catch (_: NoPublishedRelease) {}
    }

    @Test
    fun oversizedPayloadClosesConnection() = runBlocking {
        lateinit var connection: Connection
        val transport = HttpsReleaseTransport { url -> Connection(url).also { connection = it } }
        try {
            transport.read("https://api.github.com/repos/ffboye/velocity/releases/latest", 1) { _, _
                ->
                fail()
            }
            fail()
        } catch (_: UpdateException) {}
        assertTrue(connection.closed)
    }
}
