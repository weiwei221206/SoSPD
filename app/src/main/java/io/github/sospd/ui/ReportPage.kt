@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.github.sospd.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sospd.data.TaskEntity
import io.github.sospd.domain.Models.*

@Composable
fun ReportPage(report: DungeonReport, task: TaskEntity?, hidden: Set<String>, catalog: List<CatalogEntry>, idle: Boolean,
               resume: (TaskEntity) -> Unit) {
    val context = LocalContext.current
    var floorIndex by rememberSaveable(report.seed(), report.challenges()) { mutableIntStateOf(0) }
    var rooms by rememberSaveable { mutableStateOf(false) }
    var categoryName by rememberSaveable { mutableStateOf<String?>(null) }
    val floor = report.floors()[floorIndex.coerceIn(0, report.floors().lastIndex)]
    val visible = floor.items().filter { it.category().name !in hidden && (categoryName == null || it.category().name == categoryName) }
    Column(Modifier.widthIn(max = 900.dp).fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(report.dailyDate()?.let { "$it · 每日挑战" } ?: report.seedCode(),
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = if (report.dailyDate() == null) FontFamily.Monospace else FontFamily.Default,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (report.dailyDate() == null) IconButton({
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("地牢种子", report.seedCode()))
                }) { Icon(Icons.Outlined.ContentCopy, "复制种子") }
            }
            Text("4.0.2 · ${report.floors().size} 层 · ${if (report.challenges() == 0) "无挑战" else "${Integer.bitCount(report.challenges())} 项挑战"}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (task?.kind == "SEARCH" && task.status == "FOUND") TextButton({ resume(task) }, enabled = idle) { Text("继续找种") }
        }
        ScrollableTabRow(selectedTabIndex = floorIndex.coerceIn(0, report.floors().lastIndex), edgePadding = 20.dp,
            containerColor = MaterialTheme.colorScheme.background) {
            report.floors().forEachIndexed { index, value -> Tab(index == floorIndex, { floorIndex = index }, text = { Text("${value.depth()} 层") }) }
        }
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                SegmentedButton(!rooms, { rooms = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("物品") }
                SegmentedButton(rooms, { rooms = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("房间") }
            }
            Spacer(Modifier.width(16.dp))
            Text(floor.feeling(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!rooms) LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(categoryName == null, { categoryName = null }, { Text("全部") }) }
            items(Category.entries.filter { it != Category.ROOM && it.name !in hidden }) { category ->
                FilterChip(categoryName == category.name, { categoryName = category.name }, { Text(category.title) })
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (rooms) {
                if (floor.rooms().isEmpty()) item { EmptyResult("这一层没有可列出的普通房间。") }
                itemsIndexed(floor.rooms()) { _, room ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        ListItem(headlineContent = { Text(room.name()) }, supportingContent = { Text(room.type()) },
                            leadingContent = { ItemIcon(-1) }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow))
                    }
                }
            } else {
                if (visible.isEmpty()) item { EmptyResult("当前分类下没有可显示的物品。") }
                itemsIndexed(visible) { _, item ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            ItemIcon(item.image()); Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(item.name(), style = MaterialTheme.typography.titleSmall)
                                Text(item.source() + (if (item.cursed()) " · 已诅咒" else ""),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (item.appearance().isNotBlank()) Text("外观：${item.appearance()}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                if (!item.reliable()) Text("受游戏运行环境影响，不计入确定找种条件",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyResult(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 36.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
