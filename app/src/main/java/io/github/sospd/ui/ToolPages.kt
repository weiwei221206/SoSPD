@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.github.sospd.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.sospd.data.ThemeMode
import io.github.sospd.domain.Models.*
import java.time.*

@Composable
fun ScoutPage(state: AppUiState, idle: Boolean, model: AppViewModel) {
    var input by rememberSaveable { mutableStateOf("") }
    var daily by rememberSaveable { mutableStateOf(false) }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var showDate by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LazyColumn(Modifier.widthIn(max = 760.dp).fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Text("种子探查", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("查看地牢中的物品、房间和任务奖励。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Panel {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(!daily, { daily = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("指定种子") }
                    SegmentedButton(daily, { daily = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("每日挑战") }
                }
                if (!daily) {
                    OutlinedTextField(input, { input = it }, label = { Text("地牢种子") },
                        placeholder = { Text("例如 ABC-DEF-GHI") }, supportingText = { Text("支持种子码、数字或游戏内自定义文字") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                        trailingIcon = {
                            Row {
                                IconButton({
                                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                                    val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                                    if (!text.isNullOrBlank()) input = text
                                    else Toast.makeText(context, "剪贴板中没有可粘贴的文本", Toast.LENGTH_SHORT).show()
                                }) { Icon(Icons.Outlined.ContentPaste, "粘贴种子") }
                                if (input.isNotEmpty()) IconButton({ input = "" }) { Icon(Icons.Outlined.Close, "清空种子") }
                            }
                        })
                } else {
                    OutlinedButton({ showDate = true }, Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                        Icon(Icons.Outlined.CalendarMonth, null); Spacer(Modifier.width(12.dp)); Text(date)
                    }
                }
                ConfigurationSummary(state)
                Button({ model.startScout(input, if (daily) date else null) }, enabled = idle && (daily || input.isNotBlank()),
                    modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Outlined.TravelExplore, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("开始探查")
                }
            }
        }
    }
    if (showDate) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = LocalDate.parse(date).toEpochDay() * 86_400_000)
        DatePickerDialog(onDismissRequest = { showDate = false }, confirmButton = {
            TextButton({ picker.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }; showDate = false }) { Text("确定") }
        }, dismissButton = { TextButton({ showDate = false }) { Text("取消") } }) { DatePicker(picker) }
    }
}

@Composable
fun SearchPage(state: AppUiState, idle: Boolean, model: AppViewModel) {
    var picker by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<CatalogEntry?>(null) }
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LazyColumn(Modifier.widthIn(max = 760.dp).fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Text("条件找种", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("选择想要的物品或房间，寻找符合条件的地牢。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Panel {
                SectionTitle("搜索条件") {
                    TextButton({ picker = true }, enabled = !state.loading) { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Text("添加") }
                }
                if (state.conditions.isEmpty()) {
                    Text("还没有条件。试试添加一枚戒指、一根法杖或一间隐藏房间。",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.conditions.forEach { condition ->
                    val entry = state.catalog.firstOrNull { it.id() == condition.id() }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        ItemIcon(entry?.image() ?: -1)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f).clickable(enabled = entry != null) { edit = entry }) {
                            Text(entry?.name() ?: "已选物品", fontWeight = FontWeight.Medium)
                            Text(condition.minimumLevel()?.let { "至少强化 +$it" } ?: "强化等级不限",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton({ model.removeCondition(condition.id()) }) { Icon(Icons.Outlined.Close, "移除条件") }
                    }
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(state.matchAll, { model.setMatchAll(true) }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("全部满足") }
                    SegmentedButton(!state.matchAll, { model.setMatchAll(false) }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("任一满足") }
                }
                Row(Modifier.fillMaxWidth().toggleable(state.excludeImpQuest, role = Role.Switch,
                    onValueChange = model::setExcludeImpQuest), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("排除小恶魔任务")
                        Text("任务奖励不参与找种匹配", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(state.excludeImpQuest, onCheckedChange = null)
                }
            }
        }
        item {
            Panel {
                ConfigurationSummary(state)
                Button({
                    if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    model.startSearch()
                }, enabled = idle && state.conditions.isNotEmpty() && !state.loading, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("开始找种")
                }
                Text("搜索在后台继续，找到第一个结果后停止。通知栏和应用内均可停止任务。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (picker) ModalBottomSheet(onDismissRequest = { picker = false }) {
        Text("添加搜索条件", Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.headlineSmall)
        CatalogBrowser(state.catalog, Modifier.fillMaxHeight(0.85f)) { entry -> picker = false; edit = entry }
    }
    edit?.let { entry ->
        var level by remember(entry.id()) { mutableStateOf(state.conditions.firstOrNull { it.id() == entry.id() }?.minimumLevel()?.toString() ?: "") }
        val valid = level.isEmpty() || (level.toIntOrNull() ?: -1) in 0..99
        AlertDialog(onDismissRequest = { edit = null }, title = { Text(entry.name()) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (entry.category() == Category.ROOM) "在指定深度内找到这个房间。" else "在指定深度内找到这件物品。")
                if (entry.upgradeable()) OutlinedTextField(level, { level = it }, label = { Text("最低强化等级") },
                    placeholder = { Text("留空表示不限") }, singleLine = true, isError = !valid,
                    supportingText = { Text("例如填 2，表示至少 +2") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        }, confirmButton = { TextButton({ model.addCondition(entry, if (entry.upgradeable()) level.toIntOrNull() else null); edit = null }, enabled = valid) { Text("确定") } },
            dismissButton = { TextButton({ edit = null }) { Text("取消") } })
    }
}

@Composable
private fun ConfigurationSummary(state: AppUiState) {
    val challenges = if (state.settings.challenges == 0) "无挑战" else "${Integer.bitCount(state.settings.challenges)} 项挑战"
    Text("探查至 ${state.settings.floors} 层 · $challenges", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun SettingsPage(state: AppUiState, model: AppViewModel) {
    var challenges by remember { mutableStateOf(false) }
    LazyColumn(Modifier.widthIn(max = 760.dp).fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Text("设置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold) }
        item {
            Panel {
                SectionTitle("外观")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(state.settings.theme == mode, { model.setTheme(mode) }, SegmentedButtonDefaults.itemShape(index, 3)) { Text(mode.title) }
                    }
                }
            }
        }
        item { Panel { SectionTitle("默认探查设置"); DepthControl(state.settings.floors, model::setFloors)
            OutlinedButton({ challenges = true }, Modifier.fillMaxWidth()) { Text("挑战设置 · 已选 ${Integer.bitCount(state.settings.challenges)} 项") }
        } }
        item {
            Panel {
                SectionTitle("结果显示分类")
                Text("只影响结果展示，不改变找种条件或地牢生成。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Category.entries.filter { it != Category.ROOM }.forEach { category ->
                        FilterChip(category.name !in state.settings.hiddenCategories, { model.toggleCategory(category) }, { Text(category.title) })
                    }
                }
            }
        }
    }
    if (challenges) ChallengesDialog(state.settings.challenges, model::toggleChallenge) { challenges = false }
}
