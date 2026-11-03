package io.github.sospd.work

import android.content.Context
import android.os.SystemClock
import com.google.gson.Gson
import io.github.sospd.data.TaskDao
import io.github.sospd.data.TaskEntity
import io.github.sospd.domain.ConditionMatcher
import io.github.sospd.domain.Models.*
import io.github.sospd.domain.SearchPartitions
import io.github.sospd.domain.SearchPartitions.*
import io.github.sospd.domain.SeedCodec
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Foreground controller owns all durable progress; workers own isolated upstream global state. */
internal class ParallelSearchRunner(private val context: Context, private val gson: Gson, private val tasks: TaskDao,
    private val cancelled: AtomicBoolean, private val publish: (Long, Long, Int) -> Unit) {
    suspend fun run(row: TaskEntity, request: SearchRequest) {
        val checkpoint = row.checkpointJson?.let {
            val raw = gson.fromJson(it, Checkpoint::class.java)
            Checkpoint(raw.lanes().map { lane -> Lane(lane.start(), lane.length(), lane.scanned()) })
        }
            ?: SearchPartitions.create(row.startSeed, request.parallelism()).also {
                check(row.searchedCount == 0L) { "缺少并行搜索进度" }
            }
        check(checkpoint.lanes().size == request.parallelism()) { "搜索分段与原任务不一致" }
        val lanes = checkpoint.lanes().toMutableList()
        val session = UUID.randomUUID().toString()
        val events = Channel<SearchEvent>(Channel.UNLIMITED)
        val clients = mutableListOf<SearchWorkerClient>()
        val done = lanes.indices.filter { lanes[it].exhausted() }.toMutableSet()
        val started = SystemClock.elapsedRealtime()
        val priorElapsed = row.elapsedMillis
        var winner: Int? = null

        fun apply(event: SearchEvent, deferFound: Boolean) {
            val incoming = event.lane ?: return
            check(event.slot in lanes.indices) { "未知的搜索分段" }
            val previous = lanes[event.slot]
            check(incoming.start() == previous.start() && incoming.length() == previous.length()) { "搜索分段被改变" }
            val accepted = if (event.kind == SearchProtocol.FOUND && deferFound) incoming.retryLast() else incoming
            if (accepted.scanned() >= previous.scanned()) lanes[event.slot] = accepted
        }
        fun checkpoint() {
            val saved = Checkpoint(lanes)
            row.checkpointJson = gson.toJson(saved)
            row.searchedCount = saved.scanned()
            row.nextSeed = lanes[0].nextSeed()
            row.elapsedMillis = priorElapsed + SystemClock.elapsedRealtime() - started
            row.updatedAt = System.currentTimeMillis()
        }

        try {
            for (slot in lanes.indices) {
                if (cancelled.get()) throw CancellationException()
                if (slot !in done) clients += withContext(Dispatchers.Main.immediate) {
                    withTimeout(8_000) { SearchWorkerClient.connect(context, slot, session, events) }
                }
            }
            for (client in clients) client.start(request, lanes[client.slot])
            var lastSaved = 0L
            while (done.size < lanes.size) {
                currentCoroutineContext().ensureActive()
                if (cancelled.get()) throw CancellationException()
                val event = withTimeoutOrNull(150) { events.receive() }
                if (event != null) {
                    apply(event, deferFound = true)
                    when (event.kind) {
                        SearchProtocol.FOUND -> {
                            done += event.slot
                            val lane = event.lane ?: error("缺少结果的分段进度")
                            check(lane.scanned() > 0)
                            val json = SearchProtocol.resultFile(context, session, event.slot).readText()
                            val report = gson.fromJson(json, DungeonReport::class.java)
                            check(report.seed() == (lane.nextSeed() + SeedCodec.TOTAL_SEEDS - 1) % SeedCodec.TOTAL_SEEDS
                                && report.challenges() == request.challenges() && ConditionMatcher.matches(report, request)) {
                                "计算结果与搜索条件不一致"
                            }
                            apply(event, deferFound = false)
                            winner = event.slot
                            row.reportJson = json; row.status = "FOUND"
                            break
                        }
                        SearchProtocol.EXHAUSTED -> done += event.slot
                        SearchProtocol.ERROR, SearchProtocol.STOPPED -> {
                            done += event.slot
                            error(event.reason ?: "一路搜索中断，进度已保存，可重新继续")
                        }
                    }
                }
                if (SystemClock.elapsedRealtime() - lastSaved >= 1000) {
                    checkpoint(); tasks.save(row)
                    publish(row.searchedCount, row.elapsedMillis, lanes.size)
                    lastSaved = SystemClock.elapsedRealtime()
                }
            }
            if (winner == null) row.status = "EXHAUSTED"
        } finally {
            withContext(NonCancellable) {
                clients.forEach { it.cancel() }
                try {
                    val waiting = clients.map { it.slot }.filter { it !in done }.toMutableSet()
                    val deadline = SystemClock.elapsedRealtime() + 3_000
                    while (waiting.isNotEmpty() && SystemClock.elapsedRealtime() < deadline) {
                        val event = withTimeoutOrNull(100) { events.receive() } ?: continue
                        // Other matching candidates are retried on resume, so simultaneous results are never lost.
                        apply(event, deferFound = event.slot != winner)
                        if (event.kind != SearchProtocol.PROGRESS) waiting -= event.slot
                    }
                    checkpoint()
                } finally {
                    withContext(Dispatchers.Main.immediate) { clients.forEach { it.close() } }
                    events.close()
                    for (slot in lanes.indices) SearchProtocol.resultFile(context, session, slot).delete()
                }
            }
        }
    }
}
