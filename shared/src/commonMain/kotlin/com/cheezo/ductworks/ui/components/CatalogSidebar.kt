package com.cheezo.ductworks.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cheezo.ductworks.model.CatalogCategory
import com.cheezo.ductworks.model.CatalogItem
import com.cheezo.ductworks.model.Point2D
import com.cheezo.ductworks.ui.CadEditorState

/**
 * Planner 5D-grade Catalog Panel with category navigation, room templates,
 * search filtering, and one-click architectural element placement.
 */
@Composable
fun CadCatalogSidebar(
    editorState: CadEditorState,
    getCanvasCenterWorld: () -> Point2D,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFFFFFFFF),
        contentColor = Color(0xFF0F172A),
        shadowElevation = 3.dp,
        modifier = modifier
            .width(320.dp)
            .fillMaxHeight()
            .border(width = 1.dp, color = Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header with title and collapse button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("📦", fontSize = 18.sp)
                    Text(
                        "Каталог Planner 5D",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFF1F5F9))
                        .clickable { editorState.isCatalogOpen = false },
                    contentAlignment = Alignment.Center
                ) {
                    Text("◀", fontSize = 12.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)

            // Search Bar
            OutlinedTextField(
                value = editorState.catalogSearchQuery,
                onValueChange = { editorState.catalogSearchQuery = it },
                placeholder = { Text("Поиск мебели, комнат, ОВК...", fontSize = 12.sp, color = Color(0xFF94A3B8)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF2563EB),
                    unfocusedBorderColor = Color(0xFFE2E8F0),
                    focusedContainerColor = Color(0xFFF8FAFC),
                    unfocusedContainerColor = Color(0xFFF8FAFC)
                ),
                shape = RoundedCornerShape(8.dp)
            )

            // Category Chips (Horizontal Scroll)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CatalogCategory.values().forEach { cat ->
                    val isSelected = editorState.activeCatalogCategory == cat
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) Color(0xFF0F172A) else Color(0xFFF1F5F9))
                            .clickable {
                                editorState.activeCatalogCategory = cat
                                editorState.catalogSearchQuery = ""
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            cat.titleRu,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isSelected) Color.White else Color(0xFF475569),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp, modifier = Modifier.padding(top = 4.dp))

            // Body: Room Templates OR Catalog Items Grid
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (editorState.activeCatalogCategory == CatalogCategory.ROOMS && editorState.catalogSearchQuery.isBlank()) {
                    RoomTemplatesList(
                        editorState = editorState,
                        getCanvasCenterWorld = getCanvasCenterWorld
                    )
                } else {
                    CatalogItemsGrid(
                        editorState = editorState,
                        getCanvasCenterWorld = getCanvasCenterWorld
                    )
                }
            }
        }
    }
}

/**
 * Ready-made room templates (Planner 5D Room Presets).
 */
@Composable
private fun RoomTemplatesList(
    editorState: CadEditorState,
    getCanvasCenterWorld: () -> Point2D
) {
    val templates = listOf(
        RoomTemplateItem("◻", "Прямоугольная 4×3 м", "12.0 м²", 400f, 300f, isLShaped = false),
        RoomTemplateItem("🛏️", "Спальня 5×4 м", "20.0 м²", 500f, 400f, isLShaped = false),
        RoomTemplateItem("🛋️", "Гостиная 6×4.5 м", "27.0 м²", 600f, 450f, isLShaped = false),
        RoomTemplateItem("🍳", "Кухня 4×3.5 м", "14.0 м²", 400f, 350f, isLShaped = false),
        RoomTemplateItem("🚿", "Санузел 3×2 м", "6.0 м²", 300f, 200f, isLShaped = false),
        RoomTemplateItem("🚪", "Коридор 4×1.8 м", "7.2 м²", 400f, 180f, isLShaped = false),
        RoomTemplateItem("🏢", "Студия 8×5 м", "40.0 м²", 800f, 500f, isLShaped = false),
        RoomTemplateItem("◲", "L-образная комната", "22.0 м²", 0f, 0f, isLShaped = true)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Готовые шаблоны комнат:",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF475569)
        )

        for (tmpl in templates) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF8FAFC))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                    .clickable {
                        val center = getCanvasCenterWorld()
                        if (tmpl.isLShaped) {
                            editorState.addLShapedRoomAt(center)
                        } else {
                            editorState.addCustomRectangularRoom(center, tmpl.widthCm, tmpl.heightCm, tmpl.name)
                        }
                    }
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(tmpl.icon, fontSize = 20.sp)
                        Column {
                            Text(tmpl.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                            Text(tmpl.areaText, fontSize = 11.sp, color = Color(0xFF2563EB), fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFEFF6FF))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("+ Добавить", fontSize = 10.sp, color = Color(0xFF1D4ED8), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private data class RoomTemplateItem(
    val icon: String,
    val name: String,
    val areaText: String,
    val widthCm: Float,
    val heightCm: Float,
    val isLShaped: Boolean
)

/**
 * Grid of architectural items (doors, windows, furniture, sanitary, HVAC).
 */
@Composable
private fun CatalogItemsGrid(
    editorState: CadEditorState,
    getCanvasCenterWorld: () -> Point2D
) {
    val query = editorState.catalogSearchQuery.trim().lowercase()
    val activeCategory = editorState.activeCatalogCategory

    val filteredItems = remember(query, activeCategory) {
        CatalogItem.ALL_ITEMS.filter { item ->
            val matchesQuery = query.isBlank() || item.nameRu.lowercase().contains(query)
            val matchesCat = if (query.isNotBlank()) true else item.category == activeCategory
            matchesQuery && matchesCat
        }
    }

    if (filteredItems.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Элементы не найдены", fontSize = 12.sp, color = Color(0xFF94A3B8))
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 130.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filteredItems) { item ->
                CatalogItemCard(
                    item = item,
                    onClick = {
                        val center = getCanvasCenterWorld()
                        editorState.addCatalogItemAt(item, center)
                    }
                )
            }
        }
    }
}

@Composable
private fun CatalogItemCard(
    item: CatalogItem,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF8FAFC))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Crisp blueprint symbol
            Box(
                modifier = Modifier
                    .size(48.dp, 38.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFF1F5F9))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                val drawableRes = CadResourceResolver.getItemDrawable(item.svgResName)
                if (drawableRes != null) {
                    androidx.compose.foundation.Image(
                        painter = org.jetbrains.compose.resources.painterResource(drawableRes),
                        contentDescription = item.nameRu,
                        modifier = Modifier.size(34.dp, 28.dp)
                    )
                }
            }

            Text(
                item.nameRu,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0F172A),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 14.sp,
                modifier = Modifier.heightIn(min = 28.dp)
            )

            // Dimensions badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFF1F5F9))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    "${item.defaultWidthCm.toInt()} × ${item.defaultDepthCm.toInt()} см",
                    fontSize = 9.sp,
                    color = Color(0xFF475569),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
