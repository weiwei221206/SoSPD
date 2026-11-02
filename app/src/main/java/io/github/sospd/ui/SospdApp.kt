@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.github.sospd.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sospd.data.TaskEntity
import io.github.sospd.BuildConfig
import io.github.sospd.domain.Models.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class Page(val title: String) { SCOUT("探查"), SEARCH("找种"), CATALOG("图鉴"), SETTINGS("设置") }

@Composable
fun SospdApp(model: AppViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val work by model.work.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(Page.SCOUT) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val pageStates = rememberSaveableStateHolder()
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); model.clearError() }
    }
    BackHandler(state.report != null) { model.closeReport() }
    SospdTheme(state.settings.theme) {
        Scaffold(
            topBar = {
                TopAppBar(title = {
                    Text(if (state.report == null) "Seeds of SPD" else "探查结果", fontWeight = FontWeight.SemiBold)
                }, navigationIcon = {
                    if (state.report != null) IconButton(model::closeReport) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                }, actions = {
                    if (state.report == null) {
                        SuggestionChip({}, { Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.labelSmall) }, enabled = false)
                        IconButton({ showHistory = true }) { Icon(Icons.Outlined.History, "最近记录") }
                    }
                }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh))
            },
            bottomBar = {
                if (state.report == null) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Page.entries.forEach { destination ->
                        NavigationBarItem(page == destination, { page = destination }, icon = {
                            Icon(when (destination) {
                                Page.SCOUT -> Icons.Outlined.TravelExplore
                                Page.SEARCH -> Icons.Outlined.AutoAwesome
                                Page.CATALOG -> Icons.AutoMirrored.Outlined.MenuBook
                                Page.SETTINGS -> Icons.Outlined.Settings
                            }, null)
                        }, label = { Text(destination.title) })
                    }
                }
            }, snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
                work?.let { current ->
                    Box(Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                        BusyCard(current, model::stop)
                    }
                }
                if (state.report != null) {
                    ReportPage(state.report!!, state.reportTask, state.settings.hiddenCategories,
                        state.catalog, work == null, model::resume)
                } else pageStates.SaveableStateProvider(page.name) { when (page) {
                    Page.SCOUT -> ScoutPage(state, work == null, model)
                    Page.SEARCH -> SearchPage(state, work == null, model)
                    Page.CATALOG -> CatalogPage(state.catalog, state.loading)
                    Page.SETTINGS -> SettingsPage(state, model)
                } }
            }
        }
        if (showHistory) ModalBottomSheet(onDismissRequest = { showHistory = false }) {
            Text("最近记录", Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.headlineSmall)
            if (state.history.isEmpty()) Text("完成探查或开始找种后，记录会保存在这里。", Modifier.padding(24.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp), contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.history, key = { it.id }) { row ->
                    HistoryCard(row, work == null, onOpen = { model.openReport(row); showHistory = false },
                        onResume = { model.resume(row); showHistory = false })
                }
            }
        }
    }
}

@Composable
fun HistoryCard(row: TaskEntity, idle: Boolean, onOpen: () -> Unit, onResume: () -> Unit) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (row.kind == "SEARCH") Icons.Outlined.AutoAwesome else Icons.Outlined.TravelExplore, null,
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text(row.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        }
        val status = when (row.status) {
            "FOUND" -> "已找到"; "COMPLETED" -> "已完成"; "RUNNING" -> "计算中"; "CANCELLED" -> "已停止"
            "INTERRUPTED" -> "已中断"; "EXHAUSTED" -> "已遍历全部种子"; else -> "未完成"
        }
        val time = remember(row.updatedAt) { DateTimeFormatter.ofPattern("MM-dd HH:mm")
            .format(Instant.ofEpochMilli(row.updatedAt).atZone(ZoneId.systemDefault())) }
        Text("$status · $time${if (row.kind == "SEARCH") " · 已搜索 ${row.searchedCount} 个" else ""}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (row.reportJson != null) TextButton(onOpen) { Text("查看结果") }
            if (row.kind == "SEARCH" && row.status in setOf("FOUND", "CANCELLED", "INTERRUPTED", "FAILED")) {
                TextButton(onResume, enabled = idle) { Text("继续搜索") }
            }
        }
    }
}
