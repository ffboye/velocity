package com.example.myapplication

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.tracking.JourneyRepository
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JourneySaveIntegrationTest {
    @Test
    fun failedSaveBlocksInstallationUntilRetrySucceeds() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val directory = File(context.cacheDir, "save-check-${UUID.randomUUID()}")
        assertTrue(directory.mkdir())
        val isolatedContext =
            object : ContextWrapper(context) {
                override fun getFilesDir() = directory
            }
        lateinit var repository: JourneyRepository
        fun waitFor(condition: () -> Boolean) {
            val deadline = android.os.SystemClock.elapsedRealtime() + 10000
            while (!condition()) {
                check(android.os.SystemClock.elapsedRealtime() < deadline) {
                    "Storage condition timed out"
                }
                Thread.sleep(20)
            }
        }
        try {
            instrumentation.runOnMainSync { repository = JourneyRepository(isolatedContext) }
            waitFor { !repository.state.value.loading }
            assertTrue(repository.state.value.storageReady)
            // Fail storage after a successful read, rather than disguising a read failure.
            assertTrue(directory.delete())
            directory.writeText("temporary storage obstacle")
            instrumentation.runOnMainSync { repository.persist() }
            waitFor { repository.state.value.saveFailed }
            instrumentation.runOnMainSync { assertFalse(repository.tryBeginInstallation()) }
            assertTrue(directory.delete())
            assertTrue(directory.mkdir())
            instrumentation.runOnMainSync { repository.persist() }
            waitFor { !repository.state.value.saveFailed && !repository.state.value.saving }
            instrumentation.runOnMainSync {
                assertTrue(repository.tryBeginInstallation())
                repository.endInstallation()
            }
            assertTrue(File(directory, "journeys.json").exists())
        } finally {
            File(directory, "journeys.json").delete()
            File(directory, "journeys.json.bak").delete()
            File(directory, "journeys.json.new").delete()
            directory.delete()
        }
    }
}
