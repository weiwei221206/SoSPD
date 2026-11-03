package io.github.sospd.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import io.github.sospd.domain.Models.Category
import io.github.sospd.domain.Models.SearchDraft
import io.github.sospd.domain.Models.ScanRegion
import com.google.gson.Gson
import java.io.IOException

private val Context.preferences by preferencesDataStore("settings")

enum class ThemeMode(val title: String) { SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色") }
data class UserPreferences(
    val floors: Int = 4,
    val challenges: Int = 0,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val hiddenCategories: Set<String> = emptySet(),
)

class PreferencesRepository(private val context: Context, private val gson: Gson = Gson()) {
    private val floors = intPreferencesKey("floors")
    private val challenges = intPreferencesKey("challenges")
    private val theme = stringPreferencesKey("theme")
    private val hidden = stringSetPreferencesKey("hidden_categories")
    private val draft = stringPreferencesKey("search_draft")
    private val region = stringPreferencesKey("scan_region")
    val scanRegion = context.preferences.data.catch {
        if (it is IOException) emit(emptyPreferences()) else throw it
    }.map { values -> runCatching { values[region]?.let { gson.fromJson(it, ScanRegion::class.java) } }.getOrNull() }
    suspend fun saveScanRegion(value: ScanRegion) { context.preferences.edit { it[region] = gson.toJson(value) } }
    val searchDraft = context.preferences.data.catch {
        if (it is IOException) emit(emptyPreferences()) else throw it
    }.map { values ->
        runCatching { values[draft]?.let { gson.fromJson(it, SearchDraft::class.java) } }.getOrNull()
            ?: SearchDraft(emptyList(), true)
    }
    suspend fun saveSearchDraft(value: SearchDraft) { context.preferences.edit { it[draft] = gson.toJson(value) } }
    val settings = context.preferences.data.catch {
        if (it is IOException) emit(emptyPreferences()) else throw it
    }.map {
        UserPreferences(
            (it[floors] ?: 4).coerceIn(1, 24), (it[challenges] ?: 0) and 511,
            runCatching { ThemeMode.valueOf(it[theme] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
            it[hidden] ?: emptySet(),
        )
    }
    suspend fun setFloors(value: Int) { context.preferences.edit { it[floors] = value.coerceIn(1, 24) } }
    suspend fun setChallenges(value: Int) { context.preferences.edit { it[challenges] = value and 511 } }
    suspend fun setTheme(value: ThemeMode) { context.preferences.edit { it[theme] = value.name } }
    suspend fun toggleCategory(category: Category) {
        context.preferences.edit {
            val values = (it[hidden] ?: emptySet()).toMutableSet()
            if (!values.add(category.name)) values.remove(category.name)
            it[hidden] = values
        }
    }
}

