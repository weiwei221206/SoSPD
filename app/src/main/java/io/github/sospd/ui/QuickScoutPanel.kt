package io.github.sospd.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.sospd.quick.QuickScoutPermissionActivity
import io.github.sospd.quick.QuickScoutService

@Composable
fun QuickScoutPanel(state: AppUiState, idle: Boolean, model: AppViewModel) {
    val context = LocalContext.current
    val quick by model.quick.collectAsState()
    Panel {
        SectionTitle("快速探查")
        Text("在游戏画面中识别标准种子码，通过悬浮窗逐层查看物品与来源。",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (state.scanRegion == null) "尚未设置识别范围" else "识别范围已保存",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        OutlinedButton({ context.startActivity(Intent(context, QuickScoutPermissionActivity::class.java).setAction(QuickScoutService.SELECT_REGION)) },
            enabled = !quick.running && !quick.selecting, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.scanRegion == null) "设置识别范围" else "重新设置识别范围")
        }
        Button({
            if (quick.running || quick.selecting) context.startService(Intent(context, QuickScoutService::class.java).setAction(QuickScoutService.STOP))
            else context.startActivity(Intent(context, QuickScoutPermissionActivity::class.java).setAction(QuickScoutService.START))
        }, enabled = quick.running || quick.selecting || (idle && state.scanRegion != null), modifier = Modifier.fillMaxWidth()) {
            Text(if (quick.running) "关闭快速探查" else if (quick.selecting) "取消范围设置" else "开启快速探查")
        }
        Text("沿用上方探查设置。本机识别；首次使用需授予悬浮窗与屏幕识别权限。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
