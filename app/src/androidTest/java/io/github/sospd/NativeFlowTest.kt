package io.github.sospd

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ApplicationProvider
import org.junit.*
import org.junit.rules.ExternalResource
import io.github.sospd.work.DungeonTaskService
import io.github.sospd.domain.Models.*
import io.github.sospd.domain.ConditionMatcher
import io.github.sospd.data.TaskEntity
import io.github.sospd.ui.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class NativeFlowTest {
    @get:Rule(order = 0) val resetPreferences = object : ExternalResource() {
        override fun before() = runBlocking {
            val container = ApplicationProvider.getApplicationContext<SospdApplication>().container
            container.ready.await()
            container.preferences.setFloors(4)
            container.preferences.setChallenges(0)
            container.preferences.setSearchWorkers(0)
            container.preferences.saveSearchDraft(SearchDraft(emptyList(), true))
        }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Before fun awaitReady() {
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                "pm grant io.github.sospd ${Manifest.permission.POST_NOTIFICATIONS}").close()
        }
        compose.waitUntil(15_000) { !compose.activity.application.let { (it as SospdApplication).container.engine.catalog().isEmpty() } }
        compose.waitForIdle()
        compose.onNodeWithText("图鉴").performClick()
        compose.waitUntil(15_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("探查").performClick()
    }

    @After fun stopRemainingTask() {
        val container = (compose.activity.application as SospdApplication).container
        if (container.work.value != null) {
            compose.runOnUiThread { DungeonTaskService.stop(compose.activity) }
            compose.waitUntil(15_000) { container.work.value == null }
        }
    }

    @Test fun nativeCatalogFindsChineseItem() {
        compose.onNodeWithText("Seeds of SPD").assertIsDisplayed()
        compose.onAllNodesWithText("最近探查").assertCountEquals(0)
        compose.onNodeWithText("图鉴").performClick()
        compose.onAllNodesWithText("搜索中文物品或房间名称").assertCountEquals(0)
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
        compose.onNodeWithContentDescription("复制种子").performClick()
        compose.onAllNodesWithText("分享").assertCountEquals(0)
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
        compose.onNodeWithText(java.time.LocalDate.now().toString()).assertIsDisplayed()
        compose.onAllNodesWithText("日期遵循", substring = true).assertCountEquals(0)
        compose.onNodeWithText("开始探查").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("探查结果").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("每日挑战", substring = true).assertIsDisplayed()
    }

    @Test fun reportedSeed666CanBeScoutedTo24FloorsFromTheUi() {
        val container = (compose.activity.application as SospdApplication).container
        runBlocking { container.preferences.setFloors(24) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("探查至 24 层 · 无挑战").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction()).performTextReplacement("666")
        compose.onNodeWithText("开始探查").performClick()
        compose.waitUntil(20_000) { container.work.value == null && compose.onAllNodesWithText("4.0.2 · 24 层 · 无挑战").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.onNodeWithText("4.0.2 · 24 层 · 无挑战").assertIsDisplayed()
    }

    @Test fun alternatingDailyAndCustomScoutsKeepSeparateResultsAndQuestFloors() {
        val container = (compose.activity.application as SospdApplication).container
        runBlocking { container.preferences.setFloors(24) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("探查至 24 层 · 无挑战").fetchSemanticsNodes().isNotEmpty() }
        fun scout(daily: Boolean): DungeonReport {
            compose.onNodeWithText(if (daily) "每日挑战" else "指定种子").performClick()
            if (!daily) compose.onNode(hasSetTextAction()).performTextReplacement("666")
            compose.onNodeWithText("开始探查").performClick()
            compose.waitUntil(20_000) { container.work.value == null && compose.onAllNodesWithText("探查结果").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(if (daily) "${java.time.LocalDate.now()} · 每日挑战" else "AAA-AAA-AZQ").assertIsDisplayed()
            val row = runBlocking(Dispatchers.IO) { container.tasks.recent().first() }
            val report = container.gson.fromJson(row.reportJson, DungeonReport::class.java)
            Assert.assertEquals(if (daily) java.time.LocalDate.now().toString() else null, report.dailyDate())
            val questFloors = report.floors().filter { f -> f.items().any { it.impQuestReward() } }
            Assert.assertEquals(1, questFloors.size)
            Assert.assertTrue(questFloors.single().depth() in 17..19)
            Assert.assertEquals(6, questFloors.single().items().count { it.impQuestReward() })
            Assert.assertFalse(report.floors().flatMap { it.items() }.any { it.id() == "items.EnergyCrystal" })
            compose.onNodeWithContentDescription("返回").performClick()
            return report
        }
        val custom = scout(false)
        val daily = scout(true)
        Assert.assertEquals(custom, scout(false))
        Assert.assertEquals(daily, scout(true))
    }

    @Test fun wealthPlusThreeSearchCrossesReportedSeedAndCanResume() {
        val container = (compose.activity.application as SospdApplication).container
        val request = SearchRequest(listOf(Condition("items.rings.RingOfWealth", Category.RING, 3)), true, 24, 0)
        val row = TaskEntity().apply {
            id = java.util.UUID.randomUUID().toString(); kind = "SEARCH"; status = "INTERRUPTED"
            requestJson = container.gson.toJson(request); label = "财富戒指 +3"
            startSeed = 666; nextSeed = 666; startedAt = System.currentTimeMillis(); updatedAt = startedAt
        }
        runBlocking(Dispatchers.IO) { container.tasks.save(row) }
        compose.runOnUiThread { DungeonTaskService.resume(compose.activity, row.id) }
        compose.waitUntil(20_000) {
            (container.work.value?.count ?: 0) >= 3 || runBlocking(Dispatchers.IO) {
                container.tasks.find(row.id)?.status == "FOUND"
            }
        }
        if (container.work.value != null) {
            compose.onNodeWithText("停止").performClick()
            compose.waitUntil(15_000) { container.work.value == null }
            val stopped = runBlocking(Dispatchers.IO) { container.tasks.find(row.id)!! }
            Assert.assertEquals("CANCELLED", stopped.status)
            Assert.assertTrue(stopped.searchedCount >= 3)
            compose.runOnUiThread { DungeonTaskService.resume(compose.activity, row.id) }
            compose.waitUntil(20_000) { (container.work.value?.count ?: 0) > stopped.searchedCount || runBlocking(Dispatchers.IO) { container.tasks.find(row.id)?.status == "FOUND" } }
        }
        val current = runBlocking(Dispatchers.IO) { container.tasks.find(row.id)!! }
        Assert.assertNotEquals("FAILED", current.status)
        if (current.status == "FOUND") {
            Assert.assertTrue(ConditionMatcher.matches(container.gson.fromJson(current.reportJson, DungeonReport::class.java), request))
        }
    }

    @Test fun successfulSearchRetainsConditionsUntilManualRemovalAndReloadsThem() {
        val container = (compose.activity.application as SospdApplication).container
        compose.onNodeWithText("找种").performClick()
        compose.onNodeWithText("任一满足").performClick()
        compose.onNodeWithText("排除小恶魔任务").performClick().assertIsOn()
        compose.onNodeWithText("添加").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("治疗药剂")
        compose.onNode(hasText("治疗药剂") and !hasSetTextAction()).performClick()
        compose.onNodeWithText("确定").performClick()
        compose.onNodeWithText("开始找种").performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("探查结果").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("治疗药剂").assertIsDisplayed()
        compose.onNodeWithText("排除小恶魔任务").assertIsOn()
        val found = runBlocking(Dispatchers.IO) { container.tasks.recent().first() }
        Assert.assertTrue(container.gson.fromJson(found.requestJson, SearchRequest::class.java).excludeImpQuest())
        val restored = AppViewModel(compose.activity.application)
        val store = ViewModelStore().apply { put("restored", restored) }
        try {
            compose.waitUntil(15_000) { !restored.state.value.loading }
            Assert.assertEquals("items.potions.PotionOfHealing", restored.state.value.conditions.single().id())
            Assert.assertFalse(restored.state.value.matchAll)
            Assert.assertTrue(restored.state.value.excludeImpQuest)
        } finally { store.clear() }
        compose.onNodeWithContentDescription("移除条件").performClick()
        compose.waitUntil(10_000) { runBlocking { container.preferences.searchDraft.first().conditions().isEmpty() } }
    }

    @Test fun olderSavedSearchesLoadWithImpExclusionDisabled() {
        val gson = (compose.activity.application as SospdApplication).container.gson
        val legacyConditions = """[{"id":"items.potions.PotionOfHealing","category":"POTION"}]"""
        Assert.assertFalse(gson.fromJson("""{"conditions":$legacyConditions,"matchAll":true,"floors":4,"challenges":0}""",
            SearchRequest::class.java).excludeImpQuest())
        Assert.assertFalse(gson.fromJson("""{"conditions":$legacyConditions,"matchAll":true}""", SearchDraft::class.java).excludeImpQuest())
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
