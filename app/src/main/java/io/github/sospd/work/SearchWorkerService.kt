package io.github.sospd.work

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import com.google.gson.Gson
import io.github.sospd.domain.ConditionMatcher
import io.github.sospd.domain.Models.*
import io.github.sospd.domain.SearchPartitions.Lane
import io.github.sospd.domain.SeedCodec
import io.github.sospd.engine.DungeonEngine
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

internal object SearchProtocol {
    const val RUN = 1
    const val CANCEL = 2
    const val PROGRESS = 10
    const val FOUND = 11
    const val STOPPED = 12
    const val EXHAUSTED = 13
    const val ERROR = 14
    const val SESSION = "session"
    const val REQUEST = "request"
    const val LANE = "lane"
    const val REASON = "reason"
    fun resultFile(context: Context, session: String, slot: Int) = File(context.cacheDir, "parallel-results/$session-$slot.json")
}

/** Each manifest subclass runs in its own process. Never creates the main AppContainer or touches its database. */
abstract class SearchWorkerService : Service() {
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val cancelled = AtomicBoolean(false)
    private var task: Job? = null
    private val engine by lazy { DungeonEngine(File(filesDir, "search-engine/${javaClass.simpleName}")) }
    private val handler = Handler(Looper.getMainLooper()) { message ->
        when (message.what) {
            SearchProtocol.CANCEL -> { cancelled.set(true); task?.cancel() }
            SearchProtocol.RUN -> start(message)
        }
        true
    }
    private val messenger = Messenger(handler)
    override fun onBind(intent: Intent?) = messenger.binder

    private fun start(message: Message) {
        val reply = message.replyTo ?: return
        val session = message.data.getString(SearchProtocol.SESSION) ?: return
        val slot = message.arg1
        if (task?.isActive == true) return
        val requestJson = message.data.getString(SearchProtocol.REQUEST)
        val laneJson = message.data.getString(SearchProtocol.LANE)
        cancelled.set(false)
        task = scope.launch {
            var lane: Lane? = null
            fun emit(kind: Int, reason: String? = null): Boolean = try {
                reply.send(Message.obtain(null, kind).apply {
                    arg1 = slot
                    data = Bundle().apply {
                        putString(SearchProtocol.SESSION, session)
                        putString(SearchProtocol.LANE, lane?.let { gson.toJson(it) })
                        putString(SearchProtocol.REASON, reason)
                    }
                }); true
            } catch (_: RemoteException) { false }
            try {
                val request = gson.fromJson(requestJson, SearchRequest::class.java)?.validated() ?: error("缺少搜索请求")
                val decoded = gson.fromJson(laneJson, Lane::class.java) ?: error("缺少搜索分段")
                lane = Lane(decoded.start(), decoded.length(), decoded.scanned())
                val job = currentCoroutineContext().job
                var lastProgress = 0L
                while (!lane!!.exhausted()) {
                    ensureActive()
                    if (cancelled.get()) throw CancellationException()
                    val seed = lane!!.nextSeed()
                    val report = engine.scout(ScoutRequest(SeedCodec.toCode(seed), null, request.floors(), request.challenges()),
                        { cancelled.get() || !job.isActive }) { }
                    ensureActive()
                    val advanced = lane!!.advance()
                    if (ConditionMatcher.matches(report, request)) {
                        val file = SearchProtocol.resultFile(this@SearchWorkerService, session, slot)
                        file.parentFile!!.mkdirs()
                        file.writeText(gson.toJson(report))
                        lane = advanced
                        emit(SearchProtocol.FOUND)
                        return@launch
                    }
                    lane = advanced
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastProgress >= 500) {
                        if (!emit(SearchProtocol.PROGRESS)) throw CancellationException("搜索控制端已结束")
                        lastProgress = now
                    }
                }
                emit(SearchProtocol.EXHAUSTED)
            } catch (_: CancellationException) { emit(SearchProtocol.STOPPED) }
            catch (error: Exception) {
                Log.e("SoSPD", "并行计算分段失败", error)
                emit(SearchProtocol.ERROR, "一路搜索中断，进度已保存，可重新继续")
            }
        }
    }

    override fun onUnbind(intent: Intent?): Boolean { cancelled.set(true); task?.cancel(); return false }
    override fun onDestroy() { cancelled.set(true); scope.cancel(); handler.removeCallbacksAndMessages(null); super.onDestroy() }
}

class SearchWorkerOne : SearchWorkerService()
class SearchWorkerTwo : SearchWorkerService()
class SearchWorkerThree : SearchWorkerService()
class SearchWorkerFour : SearchWorkerService()
