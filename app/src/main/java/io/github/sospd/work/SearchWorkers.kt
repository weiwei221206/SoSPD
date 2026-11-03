package io.github.sospd.work

import android.app.ActivityManager
import android.content.Context

object SearchWorkers {
    fun maximum() = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
    fun resolve(context: Context, selected: Int): Int {
        if (selected > 0) return selected.coerceIn(1, maximum())
        val manager = context.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }
        return if (manager.isLowRamDevice || memory.totalMem < 3L * 1024 * 1024 * 1024) 1 else minOf(2, maximum())
    }
}
