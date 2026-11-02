package io.github.sospd.work

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import io.github.sospd.*
import io.github.sospd.data.TaskEntity
import io.github.sospd.domain.ConditionMatcher
import io.github.sospd.domain.Models.*
import io.github.sospd.domain.SeedCodec
import kotlinx.coroutines.*
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

data class WorkState(
    val taskId: String,
    val searching: Boolean,
    val count: Long = 0,
    val floor: Int = 0,
    val totalFloors: Int,
    val elapsedMillis: Long = 0,
    val stopping: Boolean = false,
)

class DungeonTaskService : Service() {
    private val container get() = (application as SospdApplication).container
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cancelled = AtomicBoolean(false)
    private var task: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(CHANNEL, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
        channel.description = getString(R.string.channel_description)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    @SuppressLint("WakelockTimeout")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            cancelled.set(true)
            container.work.value = container.work.value?.copy(stopping = true)
            return START_NOT_STICKY
        }
        if (task?.isActive == true) return START_NOT_STICKY
        cancelled.set(false)
        val notification = notification("正在准备计算", "可随时停止", true)
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION, notification,
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIFICATION, notification)
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SoSPD:seedSearch").apply { acquire() }
        task = scope.launch {
            var row: TaskEntity? = null
            try {
                container.ready.await()
                row = if (intent?.action == ACTION_RESUME) {
                    container.tasks.find(intent.getStringExtra(EXTRA_ID) ?: "")
                        ?.takeIf { it.kind == "SEARCH" && it.status in setOf("CANCELLED", "INTERRUPTED", "FOUND", "FAILED") }
                        ?: error("找不到可继续的搜索")
                } else {
                    val kind = if (intent?.action == ACTION_SEARCH) "SEARCH" else "SCOUT"
                    val json = intent?.getStringExtra(EXTRA_REQUEST) ?: error("缺少计算请求")
                    TaskEntity().apply {
                        id = UUID.randomUUID().toString(); this.kind = kind; requestJson = json
                        startedAt = System.currentTimeMillis()
                        if (kind == "SEARCH") {
                            val request = container.gson.fromJson(json, SearchRequest::class.java)
                            label = "${request.conditions().size} 个条件 · ${if (request.matchAll()) "全部满足" else "任一满足"}"
                            startSeed = Math.floorMod(SecureRandom().nextLong(), SeedCodec.TOTAL_SEEDS)
                            nextSeed = startSeed
                        } else {
                            val request = container.gson.fromJson(json, ScoutRequest::class.java)
                            label = request.dailyDate()?.let { "$it 每日挑战" } ?: SeedCodec.toCode(SeedCodec.fromText(request.input()))
                        }
                    }
                }
                row.status = "RUNNING"; row.updatedAt = System.currentTimeMillis(); row.error = null
                container.tasks.save(row); container.historyChanged()
                if (row.kind == "SEARCH") search(row) else scout(row)
            } catch (_: java.util.concurrent.CancellationException) {
                row?.status = "CANCELLED"
            } catch (e: Exception) {
                Log.e("SoSPD", "计算任务失败", e)
                row?.status = "FAILED"
                row?.error = "计算未能完成，请重试。"
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    row?.let { it.updatedAt = System.currentTimeMillis(); container.tasks.save(it) }
                    container.historyChanged()
                    container.work.value = null
                    wakeLock?.let { if (it.isHeld) it.release() }
                    wakeLock = null
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    row?.takeIf { it.status == "FOUND" }?.let { completed(it) }
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun scout(row: TaskEntity) {
        val request = container.gson.fromJson(row.requestJson, ScoutRequest::class.java)
        val started = SystemClock.elapsedRealtime()
        val report = container.engine.scout(request, { cancelled.get() }) { floor ->
            val elapsed = SystemClock.elapsedRealtime() - started
            container.work.value = WorkState(row.id, false, floor = floor, totalFloors = request.floors(), elapsedMillis = elapsed)
            update("正在探查", "第 $floor / ${request.floors()} 层", false, floor, request.floors())
        }
        row.reportJson = container.gson.toJson(report)
        row.elapsedMillis = SystemClock.elapsedRealtime() - started
        row.status = "COMPLETED"
    }

    private fun search(row: TaskEntity) {
        val request = container.gson.fromJson(row.requestJson, SearchRequest::class.java)
        val started = SystemClock.elapsedRealtime()
        val priorElapsed = row.elapsedMillis
        var lastCheckpoint = 0L
        while (row.searchedCount < SeedCodec.TOTAL_SEEDS) {
            if (cancelled.get()) throw java.util.concurrent.CancellationException()
            val seed = row.nextSeed
            val report = container.engine.scout(ScoutRequest(SeedCodec.toCode(seed), null, request.floors(), request.challenges()),
                { cancelled.get() }) { }
            row.searchedCount++
            row.nextSeed = SeedCodec.next(seed)
            row.elapsedMillis = priorElapsed + SystemClock.elapsedRealtime() - started
            if (ConditionMatcher.matches(report, request)) {
                row.reportJson = container.gson.toJson(report)
                row.status = "FOUND"
                return
            }
            val now = SystemClock.elapsedRealtime()
            if (now - lastCheckpoint >= 1000) {
                row.updatedAt = System.currentTimeMillis()
                container.tasks.save(row)
                container.work.value = WorkState(row.id, true, row.searchedCount,
                    totalFloors = request.floors(), elapsedMillis = row.elapsedMillis)
                update("正在寻找种子", "已搜索 ${row.searchedCount} 个 · 可随时停止", true)
                lastCheckpoint = now
            }
        }
        row.status = "EXHAUSTED"
    }

    private fun notification(title: String, text: String, indeterminate: Boolean, progress: Int = 0, max: Int = 0): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, javaClass).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(text).setContentIntent(open).setOngoing(true)
            .setOnlyAlertOnce(true).setProgress(max, progress, indeterminate)
            .addAction(0, "停止", stop).build()
    }
    private fun update(title: String, text: String, indeterminate: Boolean, progress: Int = 0, max: Int = 0) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(title, text, indeterminate, progress, max))
    }
    @SuppressLint("MissingPermission")
    private fun completed(row: TaskEntity) {
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java)
            .putExtra(EXTRA_ID, row.id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        getSystemService(NotificationManager::class.java).notify(RESULT_NOTIFICATION,
            NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("找到符合条件的种子").setContentText("点击查看探查结果")
                .setContentIntent(open).setAutoCancel(true).build())
    }
    override fun onDestroy() {
        cancelled.set(true)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "seed_computation"
        private const val NOTIFICATION = 10
        private const val RESULT_NOTIFICATION = 11
        private const val ACTION_SCOUT = "io.github.sospd.SCOUT"
        private const val ACTION_SEARCH = "io.github.sospd.SEARCH"
        private const val ACTION_STOP = "io.github.sospd.STOP"
        private const val ACTION_RESUME = "io.github.sospd.RESUME"
        const val EXTRA_ID = "task_id"
        private const val EXTRA_REQUEST = "request"
        fun scout(context: Context, request: ScoutRequest) = start(context, ACTION_SCOUT, request)
        fun search(context: Context, request: SearchRequest) = start(context, ACTION_SEARCH, request)
        private fun start(context: Context, action: String, request: Any) {
            val container = (context.applicationContext as SospdApplication).container
            context.startForegroundService(Intent(context, DungeonTaskService::class.java).setAction(action)
                .putExtra(EXTRA_REQUEST, container.gson.toJson(request)))
        }
        fun resume(context: Context, id: String) {
            context.startForegroundService(Intent(context, DungeonTaskService::class.java).setAction(ACTION_RESUME).putExtra(EXTRA_ID, id))
        }
        fun stop(context: Context) {
            context.startService(Intent(context, DungeonTaskService::class.java).setAction(ACTION_STOP))
        }
    }
}
