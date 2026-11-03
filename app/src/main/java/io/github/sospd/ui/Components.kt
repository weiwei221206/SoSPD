@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.github.sospd.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import io.github.sospd.R
import io.github.sospd.work.WorkState
import kotlin.math.roundToInt

@Composable
fun SectionTitle(title: String, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable
fun DepthControl(value: Int, onChange: (Int) -> Unit) {
    var current by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("最大深度", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text("${current.roundToInt()} 层", color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
    Slider(current, { current = it }, valueRange = 1f..24f, steps = 22,
        onValueChangeFinished = { onChange(current.roundToInt()) })
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("第 1 层", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("第 24 层", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun BusyCard(work: WorkState, stop: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (work.stopping) "正在停止…" else if (work.searching) "正在寻找种子" else "正在探查地牢",
                        fontWeight = FontWeight.SemiBold)
                    Text(if (work.searching) (if (work.parallelism > 1) "${work.parallelism} 路并行 · " else "") + "已搜索 ${work.count} 个 · ${duration(work.elapsedMillis)}"
                        else if (work.floor == 0) "正在准备" else "第 ${work.floor} / ${work.totalFloors} 层",
                        style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(stop, enabled = !work.stopping) {
                    Icon(Icons.Outlined.Stop, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("停止")
                }
            }
            if (work.searching || work.floor == 0) LinearProgressIndicator(Modifier.fillMaxWidth())
            else LinearProgressIndicator(progress = { work.floor.toFloat() / work.totalFloors }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun ItemIcon(image: Int, modifier: Modifier = Modifier.size(40.dp)) {
    if (image < 0) {
        Icon(Icons.Outlined.MeetingRoom, null, modifier.padding(6.dp), tint = MaterialTheme.colorScheme.primary)
        return
    }
    val context = LocalContext.current
    val sheet = remember { BitmapFactory.decodeResource(context.resources, R.drawable.item_sheet) }
    val bitmap = remember(image) {
        val x = image % 16 * 16; val y = image / 16 * 16
        if (x + 16 <= sheet.width && y + 16 <= sheet.height)
            Bitmap.createBitmap(sheet, x, y, 16, 16).asImageBitmap() else null
    }
    if (bitmap != null) Image(bitmap, null, modifier, filterQuality = FilterQuality.None)
    else Icon(Icons.Outlined.Inventory2, null, modifier.padding(6.dp))
}

data class ChallengeOption(val mask: Int, val name: String)
val challengeOptions = listOf(
    ChallengeOption(128, "精英强敌"), ChallengeOption(256, "绝命头目"), ChallengeOption(1, "缩餐节食"),
    ChallengeOption(2, "信念护体"), ChallengeOption(4, "恐药异症"), ChallengeOption(8, "荒芜之地"),
    ChallengeOption(16, "集群智能"), ChallengeOption(32, "没入黑暗"), ChallengeOption(64, "禁忌咒文"),
)

@Composable
fun ChallengesDialog(challenges: Int, toggle: (Int) -> Unit, dismiss: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text("挑战设置") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Text("挑战会影响地牢生成，请与游戏中的设置保持一致。", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                challengeOptions.forEach { option ->
                    Row(Modifier.fillMaxWidth().toggleable(challenges and option.mask != 0,
                        role = Role.Checkbox, onValueChange = { toggle(option.mask) }), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(challenges and option.mask != 0, null)
                        Text(option.name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }, confirmButton = { TextButton(dismiss) { Text("完成") } })
}

fun duration(millis: Long): String {
    val seconds = millis / 1000
    return if (seconds < 60) "$seconds 秒" else "${seconds / 60} 分 ${seconds % 60} 秒"
}
