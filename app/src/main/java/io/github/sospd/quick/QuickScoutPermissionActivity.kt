package io.github.sospd.quick

import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import io.github.sospd.SospdApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Keeps system consent in a visible Activity. Projection tokens are never saved or reused. */
class QuickScoutPermissionActivity : ComponentActivity() {
    private val overlayPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Settings.canDrawOverlays(this)) continueStart() else leave("未授予悬浮窗权限")
    }
    private val capturePermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            startForegroundService(Intent(this, QuickScoutService::class.java).setAction(QuickScoutService.START)
                .putExtra(QuickScoutService.RESULT_CODE, result.resultCode).putExtra(QuickScoutService.RESULT_DATA, data))
            moveTaskToBack(true); finish()
        } else leave("未开启屏幕识别")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        if (!Settings.canDrawOverlays(this)) {
            overlayPermission.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } else continueStart()
    }

    private fun continueStart() {
        if (intent.action == QuickScoutService.SELECT_REGION) {
            startForegroundService(Intent(this, QuickScoutService::class.java).setAction(QuickScoutService.SELECT_REGION))
            moveTaskToBack(true); finish()
        } else lifecycleScope.launch {
            val container = (application as SospdApplication).container
            val region = container.preferences.scanRegion.first()
            val screen = displaySize()
            if (region == null || !region.matchesDisplay(screen.x, screen.y)) {
                leave("请先设置当前屏幕方向的识别范围"); return@launch
            }
            val manager = getSystemService(MediaProjectionManager::class.java)
            capturePermission.launch(if (Build.VERSION.SDK_INT >= 34)
                manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()) else manager.createScreenCaptureIntent())
        }
    }

    private fun leave(message: String) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); finish() }
}
