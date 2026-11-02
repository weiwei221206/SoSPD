package io.github.sospd

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import io.github.sospd.ui.*
import io.github.sospd.work.DungeonTaskService
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val model: AppViewModel by viewModels()
    override fun attachBaseContext(newBase: Context) {
        val configuration = Configuration(newBase.resources.configuration).apply { setLocale(Locale.SIMPLIFIED_CHINESE) }
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SospdApp(model) }
        intent.getStringExtra(DungeonTaskService.EXTRA_ID)?.let(model::openTask)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(DungeonTaskService.EXTRA_ID)?.let(model::openTask)
    }
}

