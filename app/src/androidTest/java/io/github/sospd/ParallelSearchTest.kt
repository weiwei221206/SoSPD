package io.github.sospd

import android.Manifest
import android.app.ActivityManager
import android.os.Build
import android.os.SystemClock
import android.os.Process
import androidx.room.Room
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.sospd.data.AppDatabase
import io.github.sospd.data.TaskEntity
import io.github.sospd.domain.ConditionMatcher
import io.github.sospd.domain.Models.*
import io.github.sospd.domain.SearchPartitions
import io.github.sospd.domain.SearchPartitions.Checkpoint
import io.github.sospd.domain.SeedCodec
import io.github.sospd.work.DungeonTaskService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ParallelSearchTest {
    private val app get() = ApplicationProvider.getApplicationContext<SospdApplication>()
    private val container get() = app.container
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before fun setup() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        runBlocking { container.ready.await() }
        if (Build.VERSION.SDK_INT >= 33) InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "pm grant io.github.sospd ${Manifest.permission.POST_NOTIFICATIONS}").close()
    }
    @After fun cleanup() {
        if (container.work.value != null) {
            scenario.onActivity { DungeonTaskService.stop(it) }
            await { container.work.value == null }
        }
        scenario.close()
    }
    private fun await(timeout: Long = 25_000, condition: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + timeout
        while (!condition()) { if (SystemClock.elapsedRealtime() >= until) fail("等待搜索进度超时"); Thread.sleep(50) }
    }
    private fun row(id: String) = runBlocking(Dispatchers.IO) { container.tasks.find(id)!! }
    private fun checkpoint(row: TaskEntity) = container.gson.fromJson(row.checkpointJson, Checkpoint::class.java)
    private fun impossible(workers: Int) = SearchRequest(listOf(Condition("items.rings.RingOfWealth", Category.RING, 99)), true, 4, 0, false, workers)
    private fun start(request: SearchRequest): String {
        val task = TaskEntity().apply {
            id = UUID.randomUUID().toString(); kind = "SEARCH"; status = "INTERRUPTED"; label = "并行回归"
            requestJson = container.gson.toJson(request); startSeed = 666; nextSeed = 666
            startedAt = System.currentTimeMillis(); updatedAt = startedAt
        }
        runBlocking(Dispatchers.IO) { container.tasks.save(task) }
        scenario.onActivity { DungeonTaskService.resume(it, task.id) }
        return task.id
    }
    private fun stop(id: String): TaskEntity {
        scenario.onActivity { DungeonTaskService.stop(it) }
        await { container.work.value == null }
        return row(id)
    }

    @Test fun disjointProcessesStopAndResumeTheirOwnCursors() {
        val id = start(impossible(2))
        await { row(id).let { it.checkpointJson != null && checkpoint(it).lanes().all { lane -> lane.scanned() >= 3 } } }
        val running = row(id)
        assertEquals("RUNNING", running.status)
        assertEquals(SearchPartitions.create(666, 2).lanes().map { it.start() }, checkpoint(running).lanes().map { it.start() })
        val processes = app.getSystemService(ActivityManager::class.java).runningAppProcesses
            .filter { it.processName.startsWith("io.github.sospd:seed_search_") }
        assertTrue(processes.map { it.pid }.distinct().size >= 2)
        assertTrue(processes.none { it.pid == Process.myPid() })
        val stopped = stop(id)
        assertEquals("CANCELLED", stopped.status)
        assertEquals(stopped.searchedCount, checkpoint(stopped).scanned())
        assertTrue(stopped.searchedCount >= running.searchedCount)
        scenario.onActivity { DungeonTaskService.resume(it, id) }
        await { row(id).let { updated -> checkpoint(updated).lanes().indices.all { i ->
            checkpoint(updated).lanes()[i].scanned() > checkpoint(stopped).lanes()[i].scanned()
        } } }
        assertEquals("RUNNING", row(id).status)
    }

    @Test fun firstResultAndResumeNeverReturnTheSameCandidate() {
        val request = SearchRequest(listOf(Condition("items.potions.PotionOfStrength", Category.POTION, null)), true, 4, 0, true, 2)
        val id = start(request)
        await { container.work.value == null && row(id).status == "FOUND" }
        val first = row(id)
        val report = container.gson.fromJson(first.reportJson, DungeonReport::class.java)
        assertTrue(ConditionMatcher.matches(report, request))
        assertEquals(1L, checkpoint(first).scanned())
        assertEquals(1, checkpoint(first).lanes().count { it.scanned() == 0L })
        scenario.onActivity { DungeonTaskService.resume(it, id) }
        await { container.work.value == null && row(id).searchedCount > first.searchedCount }
        val next = row(id)
        assertEquals("FOUND", next.status)
        val nextReport = container.gson.fromJson(next.reportJson, DungeonReport::class.java)
        assertTrue(ConditionMatcher.matches(nextReport, request))
        assertNotEquals(report.seed(), nextReport.seed())
    }

    @Test fun interruptedWorkerLeavesAResumableCheckpoint() {
        val id = start(impossible(2))
        await { row(id).let { it.checkpointJson != null && checkpoint(it).lanes().all { lane -> lane.scanned() >= 3 } } }
        val before = row(id)
        val worker = app.getSystemService(ActivityManager::class.java).runningAppProcesses.single { it.processName == "io.github.sospd:seed_search_2" }
        assertNotEquals(Process.myPid(), worker.pid)
        Process.killProcess(worker.pid)
        await { container.work.value == null }
        val failed = row(id)
        assertEquals("FAILED", failed.status)
        assertTrue(failed.searchedCount >= before.searchedCount)
        scenario.onActivity { DungeonTaskService.resume(it, id) }
        await { row(id).searchedCount > failed.searchedCount }
        assertEquals("RUNNING", row(id).status)
    }

    @Test fun deepParallelResultMatchesAnIndependentRunWithAllChallenges() {
        val request = SearchRequest(listOf(Condition("items.potions.PotionOfStrength", Category.POTION, null)), true, 24, 511, false, 2)
        val id = start(request)
        await { container.work.value == null && row(id).status in setOf("FOUND", "FAILED") }
        val task = row(id)
        assertEquals("FOUND", task.status)
        val report = container.gson.fromJson(task.reportJson, DungeonReport::class.java)
        assertEquals(511, report.challenges()); assertEquals(24, report.floors().size)
        val expected = container.engine.scout(ScoutRequest(SeedCodec.toCode(report.seed()), null, 24, 511), { false }) { }
        report.floors().zip(expected.floors()).forEach { (actual, reference) ->
            assertEquals(reference.rooms().map { it.toString() }.sorted(), actual.rooms().map { it.toString() }.sorted())
            assertEquals(reference.items().filter { it.reliable() }.map { it.toString() }.sorted(),
                actual.items().filter { it.reliable() }.map { it.toString() }.sorted())
        }
    }

    @Test fun migrateExistingSequentialSearchWithoutDeletingHistory() {
        val name = "migration-${UUID.randomUUID()}.db"
        val file = app.getDatabasePath(name)
        val old = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null)
        old.execSQL("CREATE TABLE tasks (id TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL, status TEXT NOT NULL, requestJson TEXT NOT NULL, label TEXT NOT NULL, startSeed INTEGER NOT NULL, nextSeed INTEGER NOT NULL, searchedCount INTEGER NOT NULL, startedAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, elapsedMillis INTEGER NOT NULL, reportJson TEXT, error TEXT)")
        val legacy = """{"conditions":[{"id":"items.rings.RingOfWealth","category":"RING","minimumLevel":99}],"matchAll":true,"floors":4,"challenges":0}"""
        old.execSQL("INSERT INTO tasks VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>("legacy", "SEARCH", "CANCELLED", legacy, "旧搜索", 666, 700, 34, 1, 1, 100, null, null))
        old.version = 1; old.close()
        val migrated = Room.databaseBuilder(app, AppDatabase::class.java, name).addMigrations(AppDatabase.MIGRATION_1_2).build()
        try {
            val task = runBlocking(Dispatchers.IO) { migrated.tasks().find("legacy")!! }
            assertEquals(700L, task.nextSeed); assertEquals(34L, task.searchedCount); assertNull(task.checkpointJson)
            assertEquals(1, container.gson.fromJson(task.requestJson, SearchRequest::class.java).parallelism())
        } finally { migrated.close(); app.deleteDatabase(name) }
    }

    @Test fun measureSequentialAndParallelSearchThroughput() {
        for (workers in intArrayOf(1, 2, 4)) {
            scenario.moveToState(Lifecycle.State.RESUMED)
            val id = start(impossible(workers))
            await { (container.work.value?.count ?: 0) >= 10 }
            val before = container.work.value!!
            scenario.moveToState(Lifecycle.State.CREATED)
            val began = SystemClock.elapsedRealtime()
            while (SystemClock.elapsedRealtime() - began < 6_000) Thread.sleep(100)
            val stopped = stop(id)
            scenario.moveToState(Lifecycle.State.RESUMED)
            val rate = (stopped.searchedCount - before.count) * 1000.0 / (stopped.elapsedMillis - before.elapsedMillis)
            android.util.Log.i("SoSPDPerformance", "workers=$workers seedsPerSecond=$rate count=${stopped.searchedCount}")
            assertEquals("CANCELLED", stopped.status)
            assertTrue(stopped.searchedCount > before.count)
        }
    }
}
