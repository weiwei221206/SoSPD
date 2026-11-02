package io.github.sospd

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.sospd.domain.Models.ScoutRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EngineStressProbeTest {
    @Test fun consecutiveDeepRunsAcrossSeedsAndChallenges() = runBlocking(Dispatchers.Default) {
        val container = ApplicationProvider.getApplicationContext<SospdApplication>().container
        container.ready.await()
        container.engine.catalog()
        for (depth in intArrayOf(4, 5, 6, 24)) {
            assertEquals(depth, container.engine.scout(ScoutRequest("666", null, depth, 0), { false }) { }.floors().size)
        }
        val failures = mutableListOf<String>()
        repeat(128) { seed ->
            val challenges = intArrayOf(0, 511, 64, 1)[seed % 4]
            try {
                val report = container.engine.scout(ScoutRequest(seed.toString(), null, 24, challenges), { false }) { }
                assertEquals(24, report.floors().size)
            } catch (e: Exception) {
                android.util.Log.e("SoSPD", "Deep regression: seed=$seed challenges=$challenges", e)
                failures += "seed=$seed challenges=$challenges: ${e.javaClass.simpleName}: ${e.message}"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
