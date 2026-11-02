package io.github.sospd

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import io.github.sospd.work.DungeonTaskService

class NativeFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun awaitReady() {
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                "pm grant io.github.sospd ${Manifest.permission.POST_NOTIFICATIONS}").close()
        }
        compose.waitUntil(15_000) { !compose.activity.application.let { (it as SospdApplication).container.engine.catalog().isEmpty() } }
        compose.waitForIdle()
    }

    @Test fun nativeCatalogFindsChineseItem() {
        compose.onNodeWithText("Seeds of SPD").assertIsDisplayed()
        compose.onNodeWithText("图鉴").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("神射")
        compose.onNodeWithText("神射之戒").assertIsDisplayed().performClick()
        compose.onNodeWithText("关闭").assertIsDisplayed()
    }

    @Test fun seededRunProducesRealFloorResults() {
        compose.runOnUiThread {
            compose.activity.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("种子", "ABC-DEF-GHI"))
        }
        compose.onNodeWithContentDescription("粘贴种子").performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("ABC-DEF-GHI")
        compose.onNodeWithText("开始探查").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("探查结果").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("ABC-DEF-GHI").assertIsDisplayed()
        compose.onNodeWithText("1 层").assertIsDisplayed()
        compose.onNodeWithText("复制种子").performClick()
        compose.runOnUiThread {
            Assert.assertEquals("ABC-DEF-GHI", compose.activity.getSystemService(ClipboardManager::class.java)
                .primaryClip!!.getItemAt(0).text.toString())
        }
        compose.onNodeWithText("房间", useUnmergedTree = true).performClick()
        compose.onAllNodesWithText("普通", substring = true).fetchSemanticsNodes().let { Assert.assertTrue(it.isNotEmpty()) }
    }

    @Test fun depthAndChallengesAreOnlyEditableInSettings() {
        compose.onAllNodesWithText("最大深度").assertCountEquals(0)
        compose.onNodeWithText("找种").performClick()
        compose.onAllNodesWithText("最大深度").assertCountEquals(0)
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("最大深度").assertIsDisplayed()
        compose.onNodeWithText("挑战设置", substring = true).performClick()
        compose.onNodeWithText("禁忌咒文").assertIsDisplayed()
        compose.onNodeWithText("完成").performClick()
        compose.onAllNodesWithText("关于").assertCountEquals(0)
    }

    @Test fun dailyRunUsesDate() {
        compose.onNodeWithText("每日挑战").performClick()
        compose.onNodeWithText("开始探查").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("探查结果").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("每日挑战", substring = true).assertIsDisplayed()
    }

    @Test fun searchContinuesWithActivityStoppedAndCanBeCancelled() {
        val container = (compose.activity.application as SospdApplication).container
        compose.onNodeWithText("找种").performClick()
        compose.onNodeWithText("添加").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("神射")
        compose.onNodeWithText("神射之戒").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("99")
        compose.onNodeWithText("确定").performClick()
        compose.onNodeWithText("开始找种").performClick()
        compose.waitUntil(15_000) { (container.work.value?.count ?: 0) > 0 }
        val before = container.work.value!!.count
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.waitUntil(15_000) { (container.work.value?.count ?: 0) > before }
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText("停止").performClick()
        compose.waitUntil(15_000) { container.work.value == null }
        Assert.assertEquals("CANCELLED", kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { container.tasks.recent().first().status })
    }
}
