package io.github.sospd.work

import android.content.*
import android.os.*
import com.google.gson.Gson
import io.github.sospd.domain.Models.SearchRequest
import io.github.sospd.domain.SearchPartitions.Lane
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class SearchEvent(val kind: Int, val slot: Int, val lane: Lane? = null, val reason: String? = null)

internal class SearchWorkerClient private constructor(private val context: Context, val slot: Int,
    private val session: String, private val events: Channel<SearchEvent>) {
    private val gson = Gson()
    private var remote: Messenger? = null
    private var bound = false
    private var closed = false
    private lateinit var connection: ServiceConnection
    private val receiver = Messenger(Handler(Looper.getMainLooper()) { message ->
        if (!closed && message.data.getString(SearchProtocol.SESSION) == session) {
            try {
                val lane = message.data.getString(SearchProtocol.LANE)?.let {
                    val raw = gson.fromJson(it, Lane::class.java)
                    Lane(raw.start(), raw.length(), raw.scanned())
                }
                events.trySend(SearchEvent(message.what, slot, lane, message.data.getString(SearchProtocol.REASON)))
            } catch (_: Exception) { events.trySend(SearchEvent(SearchProtocol.ERROR, slot, reason = "无法读取分段进度")) }
        }
        true
    })

    fun start(request: SearchRequest, lane: Lane) {
        remote!!.send(Message.obtain(null, SearchProtocol.RUN).apply {
            arg1 = slot; replyTo = receiver
            data = Bundle().apply {
                putString(SearchProtocol.SESSION, session)
                putString(SearchProtocol.REQUEST, gson.toJson(request))
                putString(SearchProtocol.LANE, gson.toJson(lane))
            }
        })
    }
    fun cancel() { runCatching { remote?.send(Message.obtain(null, SearchProtocol.CANCEL)) } }
    fun close() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { close() }; return
        }
        if (closed) return
        closed = true
        if (bound) { runCatching { context.unbindService(connection) }; bound = false }
        remote = null
    }

    companion object {
        private val services = arrayOf(SearchWorkerOne::class.java, SearchWorkerTwo::class.java,
            SearchWorkerThree::class.java, SearchWorkerFour::class.java)
        suspend fun connect(context: Context, slot: Int, session: String, events: Channel<SearchEvent>): SearchWorkerClient =
            suspendCancellableCoroutine { continuation ->
                val client = SearchWorkerClient(context, slot, session, events)
                client.connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                        if (client.closed) return
                        client.remote = Messenger(binder)
                        if (continuation.isActive) continuation.resume(client) { _, value, _ -> value.close() }
                        else client.close()
                    }
                    override fun onServiceDisconnected(name: ComponentName) {
                        if (continuation.isActive) continuation.resumeWithException(IllegalStateException("计算进程连接中断"))
                        else if (!client.closed) events.trySend(SearchEvent(SearchProtocol.ERROR, slot, reason = "一路计算进程结束，进度已保存，可重新继续"))
                    }
                    override fun onBindingDied(name: ComponentName) { onServiceDisconnected(name) }
                    override fun onNullBinding(name: ComponentName) {
                        if (continuation.isActive) continuation.resumeWithException(IllegalStateException("无法连接计算进程"))
                        client.close()
                    }
                }
                continuation.invokeOnCancellation { Handler(Looper.getMainLooper()).post { client.close() } }
                try {
                    client.bound = context.bindService(Intent(context, services[slot]), client.connection,
                        Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT)
                    if (!client.bound && continuation.isActive) continuation.resumeWithException(IllegalStateException("无法启动计算进程"))
                } catch (error: Exception) {
                    client.close()
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
    }
}
