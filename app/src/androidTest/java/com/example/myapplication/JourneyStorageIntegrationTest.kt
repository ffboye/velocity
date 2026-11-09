package com.example.myapplication

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.tracking.*
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JourneyStorageIntegrationTest {
    private val trip =
        Journey("storage-test", 1000, 5000, 20.0, 10.0, listOf(RoutePoint(52.0, 4.0, 1000, 0)))

    private fun isolated(block: (File, JourneyStorage) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "storage-check-${UUID.randomUUID()}")
        check(directory.mkdir())
        try {
            block(directory, JourneyStorage(directory))
        } finally {
            File(directory, "journeys.json").delete()
            File(directory, "journeys.json.bak").delete()
            File(directory, "journeys.json.new").delete()
            directory.delete()
        }
    }

    private fun rejects(block: () -> Unit) {
        try {
            block()
        } catch (_: Exception) {
            return
        }
        fail("Invalid storage must not be accepted")
    }

    @Test
    fun invalidLocalDataIsRejectedAndPreserved() = isolated { directory, storage ->
        val file = File(directory, "journeys.json")
        val invalid =
            listOf(
                "{broken json",
                "{\"schema\":1,\"journeys\":[],\"active\":\"unreadable checkpoint\"}",
                JourneyCodec.encodeAll(listOf(trip), null)
                    .replace("\"samples\":[]", "\"samples\":\"unreadable graph\""),
                JourneyCodec.encodeAll(
                    listOf(trip.copy(points = listOf(RoutePoint(200.0, 4.0, 1000, 0)))),
                    null,
                ),
                JourneyCodec.encodeAll(
                    listOf(trip.copy(samples = listOf(SpeedSample(6000, 10.0, 0)))),
                    null,
                ),
            )
        invalid.forEach { text ->
            file.writeText(text)
            rejects { storage.load() }
            assertEquals(text, file.readText())
        }
    }

    @Test
    fun rejectedSaveLeavesThePreviousJourneyFileIntact() = isolated { directory, storage ->
        storage.save(listOf(trip), null)
        val before = File(directory, "journeys.json").readText()
        rejects { storage.save(listOf(trip.copy(distanceMeters = -1.0)), null) }
        assertEquals(before, File(directory, "journeys.json").readText())
        assertEquals(listOf(trip), storage.load().journeys)
    }

    @Test
    fun missingFileIsEmptyButUnreadableExistingPathIsAnError() = isolated { directory, storage ->
        assertTrue(storage.load().journeys.isEmpty())
        val path = File(directory, "journeys.json")
        assertTrue(path.mkdir())
        rejects { storage.load() }
        assertTrue(path.isDirectory)
        assertTrue(path.delete())
        assertTrue(directory.delete())
        directory.writeText("invalid parent")
        rejects { storage.load() }
        assertEquals("invalid parent", directory.readText())
    }

    @Test
    fun atomicFileRecoversPreviousBackupAndIgnoresUnfinishedWrite() =
        isolated { directory, storage ->
            val previous = JourneyCodec.encodeAll(listOf(trip), null)
            File(directory, "journeys.json.bak").writeText(previous)
            File(directory, "journeys.json").writeText("interrupted write")
            assertEquals(listOf(trip), storage.load().journeys)
            File(directory, "journeys.json.new").writeText("incomplete replacement")
            assertEquals(listOf(trip), storage.load().journeys)
        }
}
