package io.github.sospd.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.sospd.SospdApplication
import io.github.sospd.data.*
import io.github.sospd.domain.Models.*
import io.github.sospd.domain.SeedCodec
import io.github.sospd.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate

data class AppUiState(
    val loading: Boolean = true,
    val catalog: List<CatalogEntry> = emptyList(),
    val settings: UserPreferences = UserPreferences(),
    val history: List<TaskEntity> = emptyList(),
    val report: DungeonReport? = null,
    val reportTask: TaskEntity? = null,
    val conditions: List<Condition> = emptyList(),
    val matchAll: Boolean = true,
    val excludeImpQuest: Boolean = false,
    val error: String? = null,
    val scanRegion: ScanRegion? = null,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as SospdApplication).container
    private val mutableState = MutableStateFlow(AppUiState())
    val state = mutableState.asStateFlow()
    val work = container.work.asStateFlow()
    val quick = container.quick.asStateFlow()
    private var awaitingTaskId: String? = null
    init {
        viewModelScope.launch { container.preferences.settings.collect { p -> mutableState.update { it.copy(settings = p) } } }
        viewModelScope.launch { container.preferences.scanRegion.collect { region -> mutableState.update { it.copy(scanRegion = region) } } }
        viewModelScope.launch {
            try {
                val (catalog, draft) = withContext(Dispatchers.IO) {
                    container.ready.await()
                    container.engine.catalog() to container.preferences.searchDraft.first()
                }
                val ids = catalog.map { it.id() }.toSet()
                mutableState.update { it.copy(catalog = catalog, loading = false,
                    conditions = draft.conditions().filter { condition -> condition.id() in ids }, matchAll = draft.matchAll(),
                    excludeImpQuest = draft.excludeImpQuest()) }
            } catch (e: Exception) {
                android.util.Log.e("SoSPD", "图鉴加载失败", e)
                mutableState.update { it.copy(loading = false, error = "图鉴加载失败，请重新打开应用。") }
            }
        }
        viewModelScope.launch {
            container.historyVersion.collect {
                val rows = withContext(Dispatchers.IO) { container.ready.await(); container.tasks.recent() }
                mutableState.update { it.copy(history = rows) }
                val completed = rows.firstOrNull { row -> row.id == awaitingTaskId && row.status in setOf("FOUND", "COMPLETED", "FAILED", "CANCELLED", "EXHAUSTED") }
                if (completed != null) {
                    awaitingTaskId = null
                    if (completed.status in setOf("FOUND", "COMPLETED")) openReport(completed)
                    if (completed.status == "FAILED") showError(completed.error ?: "计算未能完成，请重试。")
                }
            }
        }
    }

    fun startScout(input: String, date: String?) {
        if (work.value != null || quick.value.busy) return
        try {
            if (date == null) SeedCodec.fromText(input) else LocalDate.parse(date)
            val p = state.value.settings
            start(false) { DungeonTaskService.scout(getApplication(), ScoutRequest(input, date, p.floors, p.challenges)) }
        } catch (e: IllegalArgumentException) { showError(e.message ?: "请输入有效种子") }
    }
    fun startSearch() {
        if (work.value != null || quick.value.busy) return
        try {
            val s = state.value
            val request = SearchRequest(s.conditions, s.matchAll, s.settings.floors, s.settings.challenges, s.excludeImpQuest)
            start(true) { DungeonTaskService.search(getApplication(), request) }
        } catch (e: IllegalArgumentException) { showError(e.message ?: "请检查找种条件") }
    }
    fun resume(row: TaskEntity) {
        if (work.value != null || quick.value.busy) return
        closeReport()
        start(true) { DungeonTaskService.resume(getApplication(), row.id) }
    }
    private fun start(searching: Boolean, action: () -> String) {
        container.work.value = WorkState("pending", searching, totalFloors = state.value.settings.floors)
        try { awaitingTaskId = action() } catch (e: Exception) {
            container.work.value = null; awaitingTaskId = null
            showError("无法启动计算，请重试。")
        }
    }
    fun stop() { DungeonTaskService.stop(getApplication()) }
    fun setMatchAll(value: Boolean) { mutableState.update { it.copy(matchAll = value) }; saveDraft() }
    fun setExcludeImpQuest(value: Boolean) { mutableState.update { it.copy(excludeImpQuest = value) }; saveDraft() }
    fun addCondition(entry: CatalogEntry, minimumLevel: Int?) {
        mutableState.update { s -> s.copy(conditions = s.conditions.filterNot { it.id() == entry.id() }
            + Condition(entry.id(), entry.category(), minimumLevel)) }
        saveDraft()
    }
    fun removeCondition(id: String) {
        mutableState.update { s -> s.copy(conditions = s.conditions.filterNot { it.id() == id }) }
        saveDraft()
    }
    private fun saveDraft() {
        val current = state.value
        container.scope.launch(Dispatchers.Main.immediate) {
            container.preferences.saveSearchDraft(SearchDraft(current.conditions, current.matchAll, current.excludeImpQuest))
        }
    }
    fun openReport(row: TaskEntity) {
        if (row.reportJson == null) return
        runCatching { container.gson.fromJson(row.reportJson, DungeonReport::class.java) }
            .onSuccess { report -> mutableState.update { it.copy(report = report, reportTask = row) } }
            .onFailure { showError("这份结果无法读取，请重新探查。") }
    }
    fun openTask(id: String) {
        viewModelScope.launch {
            val row = withContext(Dispatchers.IO) { container.ready.await(); container.tasks.find(id) }
            if (row != null) openReport(row)
        }
    }
    fun closeReport() { mutableState.update { it.copy(report = null, reportTask = null) } }
    fun setFloors(value: Int) { viewModelScope.launch { container.preferences.setFloors(value) } }
    fun toggleChallenge(mask: Int) {
        val next = state.value.settings.challenges xor mask
        mutableState.update { it.copy(settings = it.settings.copy(challenges = next)) }
        viewModelScope.launch { container.preferences.setChallenges(next) }
    }
    fun setTheme(value: ThemeMode) { viewModelScope.launch { container.preferences.setTheme(value) } }
    fun toggleCategory(value: Category) { viewModelScope.launch { container.preferences.toggleCategory(value) } }
    fun clearError() { mutableState.update { it.copy(error = null) } }
    private fun showError(message: String) { mutableState.update { it.copy(error = message) } }
}

