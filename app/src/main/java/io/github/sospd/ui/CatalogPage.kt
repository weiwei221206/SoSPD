package io.github.sospd.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sospd.domain.Models.*

@Composable
fun CatalogPage(catalog: List<CatalogEntry>, loading: Boolean) {
    var selected by remember { mutableStateOf<CatalogEntry?>(null) }
    Column(Modifier.widthIn(max = 1000.dp).fillMaxSize()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("物品与房间", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (!loading) Text("${catalog.size} 项", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else CatalogBrowser(catalog, Modifier.weight(1f)) { selected = it }
    }
    selected?.let { entry ->
        AlertDialog(onDismissRequest = { selected = null }, icon = { ItemIcon(entry.image(), Modifier.size(52.dp)) },
            title = { Text(entry.name()) }, text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(entry.category().title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(entry.description())
                }
            }, confirmButton = { TextButton({ selected = null }) { Text("关闭") } })
    }
}

@Composable
fun CatalogBrowser(catalog: List<CatalogEntry>, modifier: Modifier = Modifier, onSelect: (CatalogEntry) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var categoryName by rememberSaveable { mutableStateOf<String?>(null) }
    val entries = remember(catalog, query, categoryName) {
        catalog.filter { (categoryName == null || it.category().name == categoryName) && (query.isBlank() || it.name().contains(query.trim())) }
    }
    Column(modifier.fillMaxWidth()) {
        OutlinedTextField(query, { query = it }, placeholder = { Text("搜索物品或房间名称") }, singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, null) }, shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(categoryName == null, { categoryName = null }, { Text("全部") }) }
            items(Category.entries) { category -> FilterChip(categoryName == category.name, { categoryName = category.name }, { Text(category.title) }) }
        }
        if (entries.isEmpty()) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Text("没有找到匹配项目", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else LazyVerticalGrid(GridCells.Adaptive(152.dp), modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(entries, key = { it.id() }) { entry ->
                Card(onClick = { onSelect(entry) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ItemIcon(entry.image(), Modifier.size(44.dp))
                        Text(entry.name(), style = MaterialTheme.typography.titleSmall, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(entry.category().title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
