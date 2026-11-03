package io.github.sospd

import android.app.Application
import androidx.room.Room
import com.google.gson.Gson
import io.github.sospd.data.AppDatabase
import io.github.sospd.data.PreferencesRepository
import io.github.sospd.engine.DungeonEngine
import io.github.sospd.work.WorkState
import io.github.sospd.quick.QuickModeState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

class SospdApplication : Application() {
    val container by lazy { AppContainer(this) }
}

class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database = Room.databaseBuilder(app, AppDatabase::class.java, "sospd.db").build()
    val tasks = database.tasks()
    val gson = Gson()
    val preferences = PreferencesRepository(app, gson)
    val engine by lazy { DungeonEngine(File(app.filesDir, "engine")) }
    val work = MutableStateFlow<WorkState?>(null)
    val quick = MutableStateFlow(QuickModeState())
    val historyVersion = MutableStateFlow(0L)
    // A process can die without Service.onDestroy(). Never silently restart an interrupted search.
    val ready = scope.async { tasks.markInterrupted() }
    fun historyChanged() { historyVersion.value += 1 }
}

