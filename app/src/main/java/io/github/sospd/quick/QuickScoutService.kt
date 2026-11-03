package io.github.sospd.quick

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import io.github.sospd.MainActivity
import io.github.sospd.R
import io.github.sospd.SospdApplication
import io.github.sospd.domain.Models.ScoutRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

data class QuickModeState(val running: Boolean = false, val selecting: Boolean = false,
                          val busy: Boolean = false, val seed: String? = null)

class QuickScoutService : Service() {
    private val container get() = (application as SospdApplication).container
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var host: OverlayHost
    private var capture: QuickScreenCapture? = null
    private var ocr: SeedOcr? = null
    private var resultWindow: QuickResultWindow? = null
    private var scanJob: Job? = null
    private var stopping = false
    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        host = OverlayHost(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "快速探查", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { stopSelf(); return START_NOT_STICKY }
        try {
            check(Settings.canDrawOverlays(this)) { "请先授予悬浮窗权限" }
            if (capture != null || container.quick.value.selecting) return START_NOT_STICKY
            if (intent?.action == SELECT_REGION) {
                foreground(projecting = false)
                container.quick.value = QuickModeState(selecting = true)
                RegionSelector(this, host, save = { region ->
                    scope.launch {
                        try {
                            container.preferences.saveScanRegion(region)
                            Toast.makeText(this@QuickScoutService, "识别范围已保存，可回到程序开启快速探查", Toast.LENGTH_LONG).show()
                        } catch (error: Exception) {
                            Log.e("SoSPD", "保存识别范围失败", error)
                            Toast.makeText(this@QuickScoutService, "识别范围保存失败，请重试", Toast.LENGTH_SHORT).show()
                        } finally { stopSelf() }
                    }
                }, cancel = { stopSelf() }).launch()
            } else if (intent?.action == START) {
                foreground(projecting = true)
                @Suppress("DEPRECATION")
                val data = intent.getParcelableExtra<Intent>(RESULT_DATA) ?: error("缺少屏幕识别授权")
                val projection = getSystemService(MediaProjectionManager::class.java)
                    .getMediaProjection(intent.getIntExtra(RESULT_CODE, Activity.RESULT_CANCELED), data)
                    ?: error("屏幕识别授权已失效")
                capture = QuickScreenCapture(this, projection) {
                    Toast.makeText(this, "屏幕识别已结束", Toast.LENGTH_SHORT).show(); stopSelf()
                }
                ocr = SeedOcr()
                container.quick.value = QuickModeState(running = true)
                resultWindow = QuickResultWindow(this, host, ::identify) { stopSelf() }.also { it.show() }
            } else stopSelf()
        } catch (error: Exception) {
            Log.e("SoSPD", "快速探查启动失败", error)
            Toast.makeText(this, "无法开启快速探查，请检查授权后重试", Toast.LENGTH_LONG).show()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun foreground(projecting: Boolean) {
        val open = PendingIntent.getActivity(this, 10, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val close = PendingIntent.getService(this, 11, Intent(this, javaClass).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (projecting) "快速探查已开启" else "正在设置识别范围")
            .setContentText(if (projecting) "点击悬浮窗识别种子；关闭后结束屏幕识别" else "切到游戏画面，点击悬浮窗开始框选")
            .setOngoing(true).setContentIntent(open).addAction(0, "关闭", close).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION, notification,
            if (projecting) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else if (projecting) startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE)
    }

    private fun identify() {
        if (scanJob?.isActive == true) return
        if (container.work.value != null) {
            resultWindow?.status("请先停止正在进行的探查或找种"); return
        }
        container.quick.update { it.copy(busy = true) }
        resultWindow?.status("正在识别种子…", calculating = true)
        scanJob = scope.launch {
            try {
                val region = container.preferences.scanRegion.first() ?: error("请先设置识别范围")
                val screen = displaySize()
                require(region.matchesDisplay(screen.x, screen.y)) { "屏幕比例已改变，请重新设置识别范围" }
                host.visible(false)
                delay(200)
                val bitmap = capture?.capture(region) ?: error("屏幕识别已结束")
                host.visible(true)
                val codes = try { withTimeout(15_000) { ocr!!.recognize(bitmap) } } finally { bitmap.recycle() }
                require(codes.isNotEmpty()) { "未识别到标准种子码，请检查范围后重试" }
                require(codes.size == 1) { "范围内有多个种子码，请缩小范围后重试" }
                val seed = codes.single()
                val settings = container.preferences.settings.first()
                val job = currentCoroutineContext().job
                resultWindow?.status("$seed · 正在探查…", calculating = true)
                val report = withContext(Dispatchers.Default) {
                    container.engine.scout(ScoutRequest(seed, null, settings.floors, settings.challenges), { !job.isActive }) { floor ->
                        scope.launch { if (!stopping) resultWindow?.status("$seed · 第 $floor / ${settings.floors} 层", calculating = true) }
                    }
                }
                resultWindow?.result(report, settings.hiddenCategories)
                container.quick.update { it.copy(seed = seed) }
            } catch (error: TimeoutCancellationException) {
                resultWindow?.status("识别超时，请重试")
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                Log.w("SoSPD", "快速探查未完成", error)
                resultWindow?.status(if (error is IllegalArgumentException) error.message ?: "识别失败，请重试" else "识别或探查失败，请重试")
            } finally {
                host.visible(true)
                if (!stopping) container.quick.update { it.copy(busy = false) }
            }
        }
    }

    override fun onDestroy() {
        stopping = true
        scope.cancel(); host.remove(); capture?.close(); capture = null
        ocr?.close(); ocr = null; resultWindow = null
        container.quick.value = QuickModeState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val SELECT_REGION = "io.github.sospd.quick.SELECT_REGION"
        const val START = "io.github.sospd.quick.START"
        const val STOP = "io.github.sospd.quick.STOP"
        const val RESULT_DATA = "capture_data"
        const val RESULT_CODE = "capture_code"
        private const val CHANNEL = "quick_scout"
        private const val NOTIFICATION = 4102
    }
}
