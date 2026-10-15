package com.cheezo.ductworks.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cheezo.ductworks.model.*
import com.cheezo.ductworks.ui.CadEditorState
import com.cheezo.ductworks.ui.CadTool
import com.cheezo.ductworks.ui.LeftNavTab
import org.jetbrains.compose.resources.painterResource

/**
 * Professional CAD Left Navigation Panel (AutoCAD / Figma / Planner 5D Standard).
 * Slim vector rail (58dp) with an architectural expandable drawer (280dp).
 * Free of emojis and low-grade AI artifacts.
 */
@Composable
fun UnifiedLeftNav(
    editorState: CadEditorState,
    getCanvasCenterWorld: () -> Point2D,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxHeight()
    ) {
        // 1. Slim Navigation Rail (58dp)
        Surface(
            color = Color(0xFFFFFFFF),
            contentColor = Color(0xFF0F172A),
            shadowElevation = 2.dp,
            modifier = Modifier
                .width(58.dp)
                .fillMaxHeight()
                .border(width = 1.dp, color = Color(0xFFE2E8F0))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Tab Buttons with pure vector icons
                LeftRailTabButton(
                    icon = {
                        val isSel = editorState.isLeftDrawerOpen && editorState.activeLeftNavTab == LeftNavTab.ROOMS
                        CadIcons.RoomsTab(tint = if (isSel) Color(0xFF1D4ED8) else Color(0xFF64748B))
                    },
                    label = "Комнаты",
                    isActive = editorState.isLeftDrawerOpen && editorState.activeLeftNavTab == LeftNavTab.ROOMS,
                    onClick = { editorState.toggleLeftNavTab(LeftNavTab.ROOMS) }
                )

                LeftRailTabButton(
                    icon = {
                        val isSel = editorState.isLeftDrawerOpen && editorState.activeLeftNavTab == LeftNavTab.OPENINGS
                        CadIcons.OpeningsTab(tint = if (isSel) Color(0xFF1D4ED8) else Color(0xFF64748B))
                    },
                    label = "Проемы",
                    isActive = editorState.isLeftDrawerOpen && editorState.activeLeftNavTab == LeftNavTab.OPENINGS,
                    onClick = { editorState.toggleLeftNavTab(LeftNavTab.OPENINGS) }
                )

                LeftRailTabButton(
                    icon = {
                        val isSel = editorState.isLeftDrawerOpen && editorState.activeLeftNavTab == LeftNavTab.FURNITURE
                        CadIcons.FurnitureTab(tint = if (isSel) Color(0xFF1D4ED8) else Color(0xFF64748B))
                    },
                    label = "Мебель",
                    isActive = editorState.isLeftDrawerOpen && editorState.activeLeftNavTab == LeftNavTab.FURNITURE,
                    onClick = { editorState.toggleLeftNavTab(LeftNavTab.FURNITURE) }
                )

                LeftRailTabButton(
                    icon = {
                        val isSel = editorState.isLeftDrawerOpen && editorState.activeLeftNavTab == LeftNavTab.TOOLS
                        CadIcons.ToolsTab(tint = if (isSel) Color(0xFF1D4ED8) else Color(0xFF64748B))
                    },
                    label = "Чертёж",
                    isActive = editorState.isLeftDrawerOpen && editorState.activeLeftNavTab == LeftNavTab.TOOLS,
                    onClick = { editorState.toggleLeftNavTab(LeftNavTab.TOOLS) }
                )

                Spacer(Modifier.weight(1f))
            }
        }

        // 2. Expandable Architectural Drawer (280dp)
        AnimatedVisibility(
            visible = editorState.isLeftDrawerOpen,
            enter = expandHorizontally() + fadeIn(),
            exit = shrinkHorizontally() + fadeOut()
        ) {
            Surface(
                color = Color(0xFFFFFFFF),
                contentColor = Color(0xFF0F172A),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight()
                    .border(width = 1.dp, color = Color(0xFFE2E8F0))
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Drawer Header
                    val activeTab = editorState.activeLeftNavTab ?: LeftNavTab.ROOMS
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
                            when (activeTab) {
                                LeftNavTab.ROOMS -> CadIcons.RoomsTab(tint = Color(0xFF0F172A))
                                LeftNavTab.OPENINGS -> CadIcons.OpeningsTab(tint = Color(0xFF0F172A))
                                LeftNavTab.FURNITURE -> CadIcons.FurnitureTab(tint = Color(0xFF0F172A))
                                LeftNavTab.TOOLS -> CadIcons.ToolsTab(tint = Color(0xFF0F172A))
                            }
                            Text(
                                activeTab.titleRu,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0F172A)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFF1F5F9))
                                .clickable { editorState.isLeftDrawerOpen = false },
                            contentAlignment = Alignment.Center
                        ) {
                            CadIcons.Close(tint = Color(0xFF64748B))
                        }
                    }

                    HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)

                    // Drawer Body Content depending on Active Tab
                    when (activeTab) {
                        LeftNavTab.ROOMS -> {
                            RoomTemplatesPanel(
                                editorState = editorState,
                                getCanvasCenterWorld = getCanvasCenterWorld
                            )
                        }
                        LeftNavTab.OPENINGS -> {
                            CatalogCategoryPanel(
                                category = CatalogCategory.DOORS_WINDOWS,
                                editorState = editorState,
                                getCanvasCenterWorld = getCanvasCenterWorld
                            )
                        }
                        LeftNavTab.FURNITURE -> {
                            FurnitureCategoryPanel(
                                editorState = editorState,
                                getCanvasCenterWorld = getCanvasCenterWorld
                            )
                        }
                        LeftNavTab.TOOLS -> {
                            CadToolsPanel(
                                editorState = editorState
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeftRailTabButton(
    icon: @Composable () -> Unit,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isActive) Color(0xFFEFF6FF) else Color.Transparent
    val border = if (isActive) Color(0xFFBFDBFE) else Color.Transparent
    val textCol = if (isActive) Color(0xFF1D4ED8) else Color(0xFF475569)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            icon()
            Text(
                label,
                fontSize = 10.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                color = textCol,
                maxLines = 1
            )
        }
    }
}

/**
 * Architectural room presets with schematic blueprint dimensions and pre-placed interior furniture.
 */
@Composable
private fun RoomTemplatesPanel(
    editorState: CadEditorState,
    getCanvasCenterWorld: () -> Point2D
) {
    val templates = listOf(
        // Профессиональные помещения
        RoomPreset(
            name = "Серверная / ЦОД",
            dims = "6.0 × 4.0 м",
            area = "24.0 м²",
            w = 600f,
            h = 400f,
            isL = false,
            interiorCatalogIds = listOf(
                Pair("server_rack_42u", Point2D(-180f, -80f)),
                Pair("server_rack_42u", Point2D(-180f, 0f)),
                Pair("server_rack_42u", Point2D(-180f, 80f)),
                Pair("server_cabinet_double", Point2D(160f, -80f)),
                Pair("datacenter_cooling", Point2D(160f, 80f)),
                Pair("ups_industrial", Point2D(0f, 130f)),
                Pair("door_server_rack", Point2D(0f, -200f))
            )
        ),
        RoomPreset(
            name = "Офис Open Space",
            dims = "8.0 × 6.0 м",
            area = "48.0 м²",
            w = 800f,
            h = 600f,
            isL = false,
            interiorCatalogIds = listOf(
                Pair("pc_workstation", Point2D(-160f, -120f)),
                Pair("office_chair", Point2D(-160f, -60f)),
                Pair("pc_workstation", Point2D(160f, -120f)),
                Pair("office_chair", Point2D(160f, -60f)),
                Pair("pc_workstation", Point2D(-160f, 120f)),
                Pair("office_chair", Point2D(-160f, 60f)),
                Pair("pc_workstation", Point2D(160f, 120f)),
                Pair("office_chair", Point2D(160f, 60f)),
                Pair("filing_cabinet", Point2D(0f, -240f)),
                Pair("door_single", Point2D(-390f, 0f))
            )
        ),
        RoomPreset(
            name = "Кабинет руководителя",
            dims = "5.0 × 4.0 м",
            area = "20.0 м²",
            w = 500f,
            h = 400f,
            isL = false,
            interiorCatalogIds = listOf(
                Pair("desk_office", Point2D(-80f, 60f)),
                Pair("office_chair", Point2D(-80f, 130f)),
                Pair("armchair", Point2D(80f, -80f)),
                Pair("armchair", Point2D(150f, -80f)),
                Pair("coffee_table", Point2D(115f, -20f)),
                Pair("bookshelf", Point2D(-180f, -150f)),
                Pair("door_entrance", Point2D(-240f, 0f))
            )
        ),
        RoomPreset(
            name = "Конференц-зал",
            dims = "7.0 × 5.0 м",
            area = "35.0 м²",
            w = 700f,
            h = 500f,
            isL = false,
            interiorCatalogIds = listOf(
                Pair("conference_table", Point2D(0f, 0f)),
                Pair("tv_stand", Point2D(0f, 210f)),
                Pair("door_double", Point2D(-340f, 0f))
            )
        ),
        RoomPreset(
            name = "Диспетчерская",
            dims = "6.0 × 5.0 м",
            area = "30.0 м²",
            w = 600f,
            h = 500f,
            isL = false,
            interiorCatalogIds = listOf(
                Pair("dispatch_console", Point2D(-80f, 50f)),
                Pair("dispatch_console", Point2D(100f, 50f)),
                Pair("office_chair", Point2D(-80f, 120f)),
                Pair("office_chair", Point2D(100f, 120f)),
                Pair("metal_rack_storage", Point2D(-240f, -160f)),
                Pair("door_tech_double", Point2D(0f, -240f))
            )
        ),
        RoomPreset(
            name = "Архив / Склад",
            dims = "5.0 × 3.5 м",
            area = "17.5 м²",
            w = 500f,
            h = 350f,
            isL = false,
            interiorCatalogIds = listOf(
                Pair("metal_rack_storage", Point2D(-160f, -100f)),
                Pair("metal_rack_storage", Point2D(-160f, 0f)),
                Pair("metal_rack_storage", Point2D(-160f, 100f)),
                Pair("metal_rack_storage", Point2D(160f, -100f)),
                Pair("metal_rack_storage", Point2D(160f, 0f)),
                Pair("desk_office", Point2D(0f, 100f)),
                Pair("door_fireproof", Point2D(0f, -165f))
            )
        ),
        RoomPreset(
            name = "Зона ресепшн",
            dims = "6.0 × 4.5 м",
            area = "27.0 м²",
            w = 600f,
            h = 450f,
            isL = false,
            interiorCatalogIds = listOf(
                Pair("reception_desk", Point2D(-80f, 80f)),
                Pair("office_chair", Point2D(-80f, 140f)),
                Pair("sofa_straight", Point2D(120f, -100f)),
                Pair("coffee_table", Point2D(120f, -30f)),
                Pair("door_entrance", Point2D(-280f, 0f))
            )
        ),

        // Жилые помещения
        RoomPreset(
            name = "Гостиная",
            dims = "6.0 × 4.5 м",
            area = "27.0 м²",
            w = 600f,
            h = 450f,
            isL = false,
            interiorCatalogIds = listOf(
                Pair("sofa_straight", Point2D(0f, 120f)),
                Pair("coffee_table", Point2D(0f, 30f)),
                Pair("tv_stand", Point2D(0f, -160f)),
                Pair("floor_lamp", Point2D(-180f, 120f))
            )
        ),
        RoomPreset(
            name = "Спальня",
            dims = "5.0 × 4.0 м",
            area = "20.0 м²",
            w = 500f,
            h = 400f,
            isL = false,
            interiorCatalogIds = listOf(
                Pair("bed_double", Point2D(0f, 50f)),
                Pair("nightstand", Point2D(-140f, 110f)),
                Pair("nightstand", Point2D(140f, 110f)),
                Pair("wardrobe", Point2D(0f, -150f))
            )
        ),
        RoomPreset(
            name = "Кухня и Столовая",
            dims = "4.5 × 3.5 м",
            area = "15.8 м²",
            w = 450f,
            h = 350f,
            isL = false,
            interiorCatalogIds = listOf(
                Pair("kitchen_counter", Point2D(-80f, -130f)),
                Pair("cooking_stove", Point2D(80f, -130f)),
                Pair("dining_table_4", Point2D(0f, 60f))
            )
        ),
        RoomPreset(
            name = "Санузел",
            dims = "3.0 × 2.0 м",
            area = "6.0 м²",
            w = 300f,
            h = 200f,
            isL = false,
            interiorCatalogIds = listOf(
                Pair("bathtub", Point2D(-70f, 0f)),
                Pair("toilet", Point2D(80f, 50f)),
                Pair("washbasin", Point2D(80f, -50f))
            )
        ),
        RoomPreset("L-конфигурация", "L-тип", "22.0 м²", 0f, 0f, isL = true)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Готовые планировки с интерьером:", fontSize = 11.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)

        for (tmpl in templates) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF8FAFC))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                    .clickable {
                        val center = getCanvasCenterWorld()
                        if (tmpl.isL) {
                            editorState.addLShapedRoomAt(center)
                        } else if (tmpl.interiorCatalogIds.isNotEmpty()) {
                            editorState.addFurnishedRoom(center, tmpl.w, tmpl.h, tmpl.name, tmpl.interiorCatalogIds)
                        } else {
                            editorState.addCustomRectangularRoom(center, tmpl.w, tmpl.h, tmpl.name)
                        }
                    }
                    .padding(10.dp)
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
                        // Crisp architectural 2D preview picture for the room template
                        RoomThumbnail(
                            preset = tmpl,
                            modifier = Modifier
                                .size(44.dp, 36.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(4.dp))
                        )

                        Column {
                            Text(tmpl.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(tmpl.dims, fontSize = 10.sp, color = Color(0xFF64748B))
                                Text("•", fontSize = 10.sp, color = Color(0xFFCBD5E1))
                                Text(tmpl.area, fontSize = 10.sp, color = Color(0xFF2563EB), fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFFFFFFF))
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Добавить", fontSize = 10.sp, color = Color(0xFF1E293B), fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

/**
 * Procedural architectural floor plan thumbnail drawing for room template cards.
 */
@Composable
private fun RoomThumbnail(preset: RoomPreset, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val wallStroke = 2f.dp.toPx()

        // 1. Floor slab fill
        drawRect(Color(0xFFFEF3C7), Offset(2f, 2f), Size(w - 4f, h - 4f))

        // 2. Perimeter walls
        drawRect(Color(0xFF1E293B), Offset(2f, 2f), Size(w - 4f, h - 4f), style = Stroke(wallStroke))

        // 3. Mini blueprint interior layout
        when {
            preset.name.contains("Серверная") || preset.name.contains("ЦОД") -> {
                // Server racks (black blocks with green/blue LED dots)
                drawRect(Color(0xFF0F172A), Offset(w * 0.15f, h * 0.2f), Size(w * 0.22f, h * 0.6f))
                drawRect(Color(0xFF0F172A), Offset(w * 0.63f, h * 0.2f), Size(w * 0.22f, h * 0.6f))
                drawCircle(Color(0xFF22C55E), 1.5f, Offset(w * 0.26f, h * 0.35f))
                drawCircle(Color(0xFF3B82F6), 1.5f, Offset(w * 0.74f, h * 0.35f))
                // Door gap
                drawRect(Color.White, Offset(w * 0.4f, 0f), Size(w * 0.2f, wallStroke * 1.5f))
            }
            preset.name.contains("Офис") || preset.name.contains("Open Space") -> {
                // 4 Desks
                drawRect(Color(0xFFCBD5E1), Offset(w * 0.18f, h * 0.2f), Size(w * 0.26f, h * 0.22f))
                drawRect(Color(0xFFCBD5E1), Offset(w * 0.56f, h * 0.2f), Size(w * 0.26f, h * 0.22f))
                drawRect(Color(0xFFCBD5E1), Offset(w * 0.18f, h * 0.58f), Size(w * 0.26f, h * 0.22f))
                drawRect(Color(0xFFCBD5E1), Offset(w * 0.56f, h * 0.58f), Size(w * 0.26f, h * 0.22f))
                // Chairs
                drawCircle(Color(0xFF475569), 2.5f, Offset(w * 0.31f, h * 0.46f))
                drawCircle(Color(0xFF475569), 2.5f, Offset(w * 0.69f, h * 0.46f))
            }
            preset.name.contains("Конференц") || preset.name.contains("Переговорная") -> {
                // Large conference table with chairs
                drawRoundRect(Color(0xFFE2E8F0), Offset(w * 0.22f, h * 0.3f), Size(w * 0.56f, h * 0.4f), CornerRadius(4f, 4f))
                drawRoundRect(Color(0xFF475569), Offset(w * 0.22f, h * 0.3f), Size(w * 0.56f, h * 0.4f), CornerRadius(4f, 4f), style = Stroke(1f))
                for (i in 0..2) {
                    val cx = w * 0.32f + i * (w * 0.18f)
                    drawCircle(Color(0xFF94A3B8), 2.5f, Offset(cx, h * 0.2f))
                    drawCircle(Color(0xFF94A3B8), 2.5f, Offset(cx, h * 0.8f))
                }
            }
            preset.name.contains("Кабинет") -> {
                // Executive desk & chair + armchairs
                drawRect(Color(0xFFE2E8F0), Offset(w * 0.2f, h * 0.55f), Size(w * 0.4f, h * 0.25f))
                drawCircle(Color(0xFF334155), 3f, Offset(w * 0.4f, h * 0.88f))
                drawRoundRect(Color(0xFFCBD5E1), Offset(w * 0.68f, h * 0.25f), Size(w * 0.2f, h * 0.2f), CornerRadius(2f, 2f))
                drawRoundRect(Color(0xFFCBD5E1), Offset(w * 0.68f, h * 0.55f), Size(w * 0.2f, h * 0.2f), CornerRadius(2f, 2f))
            }
            preset.name.contains("Диспетчерская") -> {
                // Curved console with 3 screens
                drawRoundRect(Color(0xFF1E293B), Offset(w * 0.2f, h * 0.45f), Size(w * 0.6f, h * 0.25f), CornerRadius(3f, 3f))
                drawLine(Color(0xFF38BDF8), Offset(w * 0.25f, h * 0.48f), Offset(w * 0.75f, h * 0.48f), strokeWidth = 2f)
            }
            preset.name.contains("Архив") || preset.name.contains("Склад") -> {
                // Storage shelving rows
                drawRect(Color(0xFF64748B), Offset(w * 0.15f, h * 0.2f), Size(w * 0.2f, h * 0.6f), style = Stroke(1f))
                drawRect(Color(0xFF64748B), Offset(w * 0.40f, h * 0.2f), Size(w * 0.2f, h * 0.6f), style = Stroke(1f))
                drawRect(Color(0xFF64748B), Offset(w * 0.65f, h * 0.2f), Size(w * 0.2f, h * 0.6f), style = Stroke(1f))
            }
            preset.name.contains("Ресепшн") -> {
                // Curved greeting counter & sofa
                drawRoundRect(Color(0xFF0F172A), Offset(w * 0.15f, h * 0.45f), Size(w * 0.45f, h * 0.25f), CornerRadius(2f, 2f))
                drawRoundRect(Color(0xFFCBD5E1), Offset(w * 0.65f, h * 0.25f), Size(w * 0.22f, h * 0.5f), CornerRadius(3f, 3f))
            }
            preset.name.contains("Спальня") -> {
                // Double bed with pillows
                drawRoundRect(Color(0xFFE2E8F0), Offset(w * 0.28f, h * 0.22f), Size(w * 0.44f, h * 0.55f), CornerRadius(3f, 3f))
                drawRoundRect(Color(0xFFCBD5E1), Offset(w * 0.32f, h * 0.25f), Size(w * 0.16f, h * 0.12f), CornerRadius(1.5f, 1.5f))
                drawRoundRect(Color(0xFFCBD5E1), Offset(w * 0.52f, h * 0.25f), Size(w * 0.16f, h * 0.12f), CornerRadius(1.5f, 1.5f))
            }
            preset.name.contains("Гостиная") -> {
                // Sofa & coffee table
                drawRoundRect(Color(0xFFCBD5E1), Offset(w * 0.25f, h * 0.55f), Size(w * 0.5f, h * 0.25f), CornerRadius(3f, 3f))
                drawRoundRect(Color(0xFF94A3B8), Offset(w * 0.35f, h * 0.32f), Size(w * 0.3f, h * 0.14f), CornerRadius(2f, 2f))
            }
            preset.name.contains("Кухня") -> {
                // Kitchen counters & dining table
                drawRect(Color(0xFFCBD5E1), Offset(2f, 2f), Size(w * 0.6f, h * 0.22f))
                drawCircle(Color(0xFF94A3B8), 2f, Offset(w * 0.2f, h * 0.11f))
                drawCircle(Color(0xFF94A3B8), 2f, Offset(w * 0.4f, h * 0.11f))
                drawRect(Color(0xFFE2E8F0), Offset(w * 0.35f, h * 0.55f), Size(w * 0.35f, h * 0.25f))
            }
            preset.name.contains("Санузел") -> {
                // Bathtub & toilet
                drawRoundRect(Color(0xFFBAE6FD), Offset(w * 0.12f, h * 0.15f), Size(w * 0.38f, h * 0.7f), CornerRadius(4f, 4f))
                drawOval(Color(0xFFE2E8F0), Offset(w * 0.65f, h * 0.35f), Size(w * 0.22f, h * 0.35f))
            }
            else -> {
                // Standard room rectangle
                drawRect(Color(0xFFCBD5E1), Offset(w * 0.3f, h * 0.3f), Size(w * 0.4f, h * 0.4f), style = Stroke(1f))
            }
        }
    }
}

private data class RoomPreset(
    val name: String,
    val dims: String,
    val area: String,
    val w: Float,
    val h: Float,
    val isL: Boolean,
    val interiorCatalogIds: List<Pair<String, Point2D>> = emptyList()
)

/**
 * Furniture category panel with category selector and search.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun FurnitureCategoryPanel(
    editorState: CadEditorState,
    getCanvasCenterWorld: () -> Point2D
) {
    var selectedCat by remember { mutableStateOf(CatalogCategory.LIVING_ROOM) }
    var searchQuery by remember { mutableStateOf("") }

    val categories = listOf(
        CatalogCategory.LIVING_ROOM,
        CatalogCategory.BEDROOM,
        CatalogCategory.KITCHEN,
        CatalogCategory.BATHROOM,
        CatalogCategory.OFFICE
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // Search
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Поиск мебели...", fontSize = 11.sp, color = Color(0xFF94A3B8)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF2563EB),
                unfocusedBorderColor = Color(0xFFE2E8F0),
                focusedContainerColor = Color(0xFFF8FAFC),
                unfocusedContainerColor = Color(0xFFF8FAFC)
            ),
            shape = RoundedCornerShape(8.dp)
        )

        // Subcategory Pills (Segmented style) with mouse wheel horizontal scroll
        val pillScrollState = rememberScrollState()
        val coroutineScope = rememberCoroutineScope()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .horizontalScroll(pillScrollState),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categories.forEach { cat ->
                val isSel = cat == selectedCat
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSel) Color(0xFF0F172A) else Color(0xFFF1F5F9))
                        .clickable { selectedCat = cat }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        cat.titleRu,
                        fontSize = 11.sp,
                        fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSel) Color.White else Color(0xFF475569),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        val q = searchQuery.trim().lowercase()
        val items = remember(q, selectedCat) {
            CatalogItem.ALL_ITEMS.filter {
                it.category == selectedCat && (q.isBlank() || it.nameRu.lowercase().contains(q))
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 136.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            items(items) { item ->
                CatalogItemCard(item = item, onClick = {
                    val center = getCanvasCenterWorld()
                    editorState.addCatalogItemAt(item, center)
                })
            }
        }
    }
}

/**
 * Single catalog category panel (for Doors/Windows or HVAC).
 */
@Composable
private fun CatalogCategoryPanel(
    category: CatalogCategory,
    editorState: CadEditorState,
    getCanvasCenterWorld: () -> Point2D
) {
    var searchQuery by remember { mutableStateOf("") }
    val q = searchQuery.trim().lowercase()

    val items = remember(q, category) {
        CatalogItem.ALL_ITEMS.filter {
            it.category == category && (q.isBlank() || it.nameRu.lowercase().contains(q))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Поиск в разделе...", fontSize = 11.sp, color = Color(0xFF94A3B8)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF2563EB),
                unfocusedBorderColor = Color(0xFFE2E8F0),
                focusedContainerColor = Color(0xFFF8FAFC),
                unfocusedContainerColor = Color(0xFFF8FAFC)
            ),
            shape = RoundedCornerShape(8.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 136.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            items(items) { item ->
                CatalogItemCard(item = item, onClick = {
                    val center = getCanvasCenterWorld()
                    editorState.addCatalogItemAt(item, center)
                })
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
            // Crisp architectural blueprint symbol
            CatalogItemSchematic(item = item)

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

/**
 * Pure vector architectural schematic thumbnail for catalog items.
 * Replaces cartoonish emojis with precise CAD symbology.
 */
@Composable
private fun CatalogItemSchematic(item: CatalogItem) {
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
            Image(
                painter = painterResource(drawableRes),
                contentDescription = item.nameRu,
                modifier = Modifier.size(34.dp, 28.dp)
            )
        } else {
            Canvas(modifier = Modifier.size(32.dp, 24.dp)) {
                val stroke = 1.3f.dp.toPx()
                val tint = Color(0xFF334155)
                val w = size.width
                val h = size.height
                drawRect(tint, Offset(w * 0.15f, h * 0.15f), Size(w * 0.7f, h * 0.7f), style = Stroke(stroke))
            }
        }
    }
}

/**
 * CAD Drawing Tools panel inside the left drawer.
 */
@Composable
private fun CadToolsPanel(
    editorState: CadEditorState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Инструменты черчения:", fontSize = 11.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)

        ToolActionCard(
            title = "Указатель / Выбор",
            desc = "Клик для выбора, перемещение стен и мебели",
            hotkey = "V",
            isSelected = editorState.currentTool == CadTool.SELECT,
            onClick = { editorState.selectTool(CadTool.SELECT) }
        )

        ToolActionCard(
            title = "Чертить стены",
            desc = "Кликните для начала стены, замкните контур",
            hotkey = "W",
            isSelected = editorState.currentTool == CadTool.DRAW_WALL,
            onClick = { editorState.selectTool(CadTool.DRAW_WALL) }
        )

        ToolActionCard(
            title = "Разрезать стену",
            desc = "Кликните по любой стене для вставки узла",
            hotkey = "S",
            isSelected = editorState.currentTool == CadTool.SPLIT_WALL,
            onClick = { editorState.selectTool(CadTool.SPLIT_WALL) }
        )

        ToolActionCard(
            title = "Рулетка / Замер",
            desc = "Замер расстояния между двумя точками",
            hotkey = "M",
            isSelected = editorState.currentTool == CadTool.MEASURE,
            onClick = { editorState.selectTool(CadTool.MEASURE) }
        )

        HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

        Text("Толщина новой стены:", fontSize = 11.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(10f, 15f, 20f, 25f, 30f, 38f).forEach { t ->
                val isSel = editorState.defaultWallThicknessCm == t
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSel) Color(0xFF0F172A) else Color(0xFFF1F5F9))
                        .clickable { editorState.defaultWallThicknessCm = t }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${t.toInt()}см",
                        fontSize = 10.sp,
                        fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSel) Color.White else Color(0xFF1E293B)
                    )
                }
            }
        }

        var customThicknessText by remember(editorState.defaultWallThicknessCm) {
            mutableStateOf(editorState.defaultWallThicknessCm.toInt().toString())
        }

        OutlinedTextField(
            value = customThicknessText,
            onValueChange = { newVal ->
                if (newVal.all { it.isDigit() } && newVal.length <= 4) {
                    customThicknessText = newVal
                    newVal.toFloatOrNull()?.let { v ->
                        if (v in 1f..300f) {
                            editorState.defaultWallThicknessCm = v
                        }
                    }
                }
            },
            label = { Text("Точная толщина, см", fontSize = 10.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ToolActionCard(
    title: String,
    desc: String,
    hotkey: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC)
    val border = if (isSelected) Color(0xFF2563EB) else Color(0xFFE2E8F0)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
                Text(desc, fontSize = 10.sp, color = Color(0xFF64748B), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFE2E8F0))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(hotkey, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
            }
        }
    }
}
