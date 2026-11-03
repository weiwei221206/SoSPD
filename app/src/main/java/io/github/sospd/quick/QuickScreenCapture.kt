package io.github.sospd.quick

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import io.github.sospd.domain.Models.ScanRegion
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

/** Maintains one projection per user authorization; copies only the selected rectangle on request. */
internal class QuickScreenCapture(context: Context, private val projection: MediaProjection,
                                  private val stopped: () -> Unit) : AutoCloseable {
    private val size = context.displaySize()
    private val thread = HandlerThread("SoSPD-quick-capture").apply { start() }
    private val handler = Handler(thread.looper)
    private val reader = ImageReader.newInstance(size.x, size.y, PixelFormat.RGBA_8888, 3)
    private var display: VirtualDisplay? = null
    private var latest: Image? = null // Owned exclusively by handler.
    private var pending: Pair<ScanRegion, CancellableContinuation<Bitmap>>? = null
    @Volatile private var closed = false
    private val callback = object : MediaProjection.Callback() {
        override fun onStop() { if (!closed) stopped() }
        override fun onCapturedContentResize(width: Int, height: Int) {
            if (!closed && (width != size.x || height != size.y)) stopped()
        }
    }

    init {
        try {
            projection.registerCallback(callback, Handler(Looper.getMainLooper()))
            reader.setOnImageAvailableListener({ source ->
                if (!closed) {
                    val image = source.acquireLatestImage()
                    if (image != null) { latest?.close(); latest = image; deliver() }
                }
            }, handler)
            display = projection.createVirtualDisplay("SoSPD quick scout", size.x, size.y, context.resources.configuration.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, null)
        } catch (error: Exception) { close(); throw error }
    }

    suspend fun capture(region: ScanRegion): Bitmap = withTimeout(4_000) {
        require(region.matchesDisplay(size.x, size.y)) { "屏幕比例已改变，请重新设置识别范围" }
        suspendCancellableCoroutine { continuation ->
            handler.post {
                if (closed) continuation.cancel()
                else { pending = region to continuation; deliver() }
            }
            continuation.invokeOnCancellation { handler.post { if (pending?.second === continuation) pending = null } }
        }
    }

    private fun deliver() {
        val (region, continuation) = pending ?: return
        val image = latest ?: return
        pending = null; latest = null
        try {
            val left = (region.left() * image.width).roundToInt().coerceIn(0, image.width - 1)
            val top = (region.top() * image.height).roundToInt().coerceIn(0, image.height - 1)
            val right = (region.right() * image.width).roundToInt().coerceIn(left + 1, image.width)
            val bottom = (region.bottom() * image.height).roundToInt().coerceIn(top + 1, image.height)
            val width = right - left; val height = bottom - top
            val plane = image.planes[0]
            check(plane.pixelStride == 4) { "不支持的屏幕图像格式" }
            val pixels = ByteBuffer.allocate(width * height * 4)
            val row = ByteArray(width * 4)
            for (y in top until bottom) {
                plane.buffer.position(y * plane.rowStride + left * plane.pixelStride)
                plane.buffer.get(row); pixels.put(row)
            }
            pixels.rewind()
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(pixels)
            if (continuation.isActive) continuation.resume(bitmap) { _, value, _ -> value.recycle() }
            else bitmap.recycle()
        } catch (error: Exception) { if (continuation.isActive) continuation.resumeWithException(error) }
        finally { image.close() }
    }

    override fun close() {
        if (closed) return
        closed = true
        display?.release(); display = null
        projection.unregisterCallback(callback); projection.stop()
        handler.post {
            pending?.second?.cancel(); pending = null
            latest?.close(); latest = null
            reader.close(); thread.quitSafely()
        }
    }
}
