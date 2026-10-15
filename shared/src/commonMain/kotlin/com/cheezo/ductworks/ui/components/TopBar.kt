package com.cheezo.ductworks.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cheezo.ductworks.engine.cad.GostDimensionTerminator
import com.cheezo.ductworks.engine.threed.WallHeightMode3D
import com.cheezo.ductworks.ui.CadEditorState
import com.cheezo.ductworks.ui.CadViewportState
import com.cheezo.ductworks.ui.MeasurementUnit
import com.cheezo.ductworks.ui.ViewMode
import kotlin.math.roundToInt

/**
 * Professional Architectural TopBar (Figma / Linear / AutoCAD Standard).
 * Clean typography, crisp vector icons, and cohesive slate palette.
 * Free of emojis and AI-prototype artifacts.
 */
@Composable
fun CadTopBar(
    editorState: CadEditorState,
    viewport: CadViewportState,
    onFitToScreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFFFFFFFF),
        contentColor = Color(0xFF0F172A),
        shadowElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .border(width = 1.dp, color = Color(0xFFE2E8F0))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp)
        ) {
            // --- Left Group: Logo, Title & Document Quick Actions ---
            Row(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Minimalist CAD Monogram Icon
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F172A)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "D",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                // Title & Subtitle with Status Dot
                Column(verticalArrangement = Arrangement.Center) {
                    Text(
                        "Ductworks CAD",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = Color(0xFF0F172A),
                        lineHeight = 15.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF10B981))
                        )
                        Text(
                            editorState.document.name,
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                            lineHeight = 13.sp
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                var showClearDialog by remember { mutableStateOf(false) }

                // Document Actions (Ghost buttons with vector icons)
                GhostActionButton(
                    icon = { CadIcons.Trash(tint = Color(0xFF64748B)) },
                    label = "Очистить",
                    onClick = { showClearDialog = true }
                )

                if (showClearDialog) {
                    AlertDialog(
                        onDismissRequest = { showClearDialog = false },
                        title = { Text("Очистить проект?", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                        text = { Text("Вы действительно хотите удалить все стены, комнаты и объекты с плана? Это действие нельзя отменить.", fontSize = 13.sp) },
                        confirmButton = {
                            Button(
                                onClick = {
                                    editorState.resetPlan()
                                    showClearDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Очистить", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            OutlinedButton(
                                onClick = { showClearDialog = false },
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Отмена", color = Color(0xFF475569))
                            }
                        },
                        containerColor = Color.White,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                VerticalDivider(
                    color = Color(0xFFE2E8F0),
                    modifier = Modifier.height(22.dp).padding(horizontal = 4.dp)
                )

                // Figma-style Segmented View Switcher (2D Plan vs 3D Model) - Fixed on Left
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF1F5F9))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                        .padding(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val is2D = editorState.viewMode == ViewMode.MODE_2D

                    SegmentedTabPill(
                        icon = { CadIcons.Plan2D(tint = if (is2D) Color(0xFF0F172A) else Color(0xFF64748B)) },
                        label = "2D План",
                        isSelected = is2D,
                        onClick = { editorState.viewMode = ViewMode.MODE_2D }
                    )
                    SegmentedTabPill(
                        icon = { CadIcons.Model3D(tint = if (!is2D) Color(0xFF0F172A) else Color(0xFF64748B)) },
                        label = "3D Модель",
                        isSelected = !is2D,
                        onClick = { editorState.viewMode = ViewMode.MODE_3D }
                    )
                }
            }

            // --- Right Group: Settings Button / 3D Heights / Undo / Zoom / Inspector ---
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (editorState.viewMode == ViewMode.MODE_2D) {
                    var isSettingsOpen by remember { mutableStateOf(false) }

                    Box {
                        GhostActionButton(
                            icon = { CadIcons.Settings(tint = if (isSettingsOpen) Color(0xFF2563EB) else Color(0xFF475569)) },
                            label = "Настройки",
                            onClick = { isSettingsOpen = !isSettingsOpen }
                        )

                        DropdownMenu(
                            expanded = isSettingsOpen,
                            onDismissRequest = { isSettingsOpen = false },
                            modifier = Modifier
                                .width(280.dp)
                                .background(Color.White)
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Text("Отображение чертежа", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))

                            SettingsCheckItem(icon = { CadIcons.Grid() }, label = "Сетка", isChecked = editorState.isGridVisible) {
                                editorState.isGridVisible = !editorState.isGridVisible
                            }
                            SettingsCheckItem(icon = { CadIcons.Magnet() }, label = "Привязка", isChecked = editorState.isGridSnapEnabled) {
                                editorState.isGridSnapEnabled = !editorState.isGridSnapEnabled
                            }
                            SettingsCheckItem(icon = { CadIcons.Ortho() }, label = "Орто", isChecked = editorState.isOrthoEnabled) {
                                editorState.isOrthoEnabled = !editorState.isOrthoEnabled
                            }
                            SettingsCheckItem(icon = { CadIcons.Dimensions() }, label = "Размеры", isChecked = editorState.showDimensions) {
                                editorState.showDimensions = !editorState.showDimensions
                            }
                            SettingsCheckItem(icon = { CadIcons.Area() }, label = "Экспликация", isChecked = editorState.showRoomAreas) {
                                editorState.showRoomAreas = !editorState.showRoomAreas
                            }
                            SettingsCheckItem(icon = { CadIcons.RoomsTab() }, label = "Текстуры пола", isChecked = editorState.showFloorTextures) {
                                editorState.showFloorTextures = !editorState.showFloorTextures
                            }
                            SettingsCheckItem(icon = { CadIcons.ToolsTab() }, label = "Штриховка", isChecked = editorState.showWallHatching) {
                                editorState.showWallHatching = !editorState.showWallHatching
                            }

                            HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp, modifier = Modifier.padding(vertical = 6.dp))

                            Text("Единицы измерения", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(MeasurementUnit.MILLIMETERS to "мм", MeasurementUnit.CENTIMETERS to "см", MeasurementUnit.METERS to "м").forEach { (u, label) ->
                                    val isSel = editorState.measurementUnit == u
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSel) Color(0xFF0F172A) else Color(0xFFF1F5F9))
                                            .clickable { editorState.measurementUnit = u }
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(label, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium, color = if (isSel) Color.White else Color(0xFF334155))
                                    }
                                }
                            }

                            HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp, modifier = Modifier.padding(vertical = 6.dp))

                            Text("Ограничители размеров", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(GostDimensionTerminator.ARROWS_GOST_20 to "Стрелки", GostDimensionTerminator.ARCHITECTURAL_TICKS_45 to "Засечки").forEach { (term, label) ->
                                    val isSel = editorState.dimensionTerminator == term
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSel) Color(0xFF0F172A) else Color(0xFFF1F5F9))
                                            .clickable { editorState.dimensionTerminator = term }
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(label, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium, color = if (isSel) Color.White else Color(0xFF334155))
                                    }
                                }
                            }
                        }
                    }
                }

                // Undo / Redo
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF8FAFC))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                        .padding(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SquareIconButton(
                        enabled = editorState.engine.canUndo,
                        onClick = { editorState.undo() }
                    ) {
                        CadIcons.Undo(tint = if (editorState.engine.canUndo) Color(0xFF334155) else Color(0xFFCBD5E1))
                    }
                    SquareIconButton(
                        enabled = editorState.engine.canRedo,
                        onClick = { editorState.redo() }
                    ) {
                        CadIcons.Redo(tint = if (editorState.engine.canRedo) Color(0xFF334155) else Color(0xFFCBD5E1))
                    }
                }

                // Zoom Controller
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF8FAFC))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                        .padding(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    SquareIconButton(onClick = {
                        viewport.zoomAround(androidx.compose.ui.geometry.Offset(500f, 500f), 0.82f)
                    }) {
                        CadIcons.Minus(tint = Color(0xFF475569))
                    }
                    Text(
                        "${(viewport.zoom * 100).roundToInt()}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF334155),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    SquareIconButton(onClick = {
                        viewport.zoomAround(androidx.compose.ui.geometry.Offset(500f, 500f), 1.22f)
                    }) {
                        CadIcons.Plus(tint = Color(0xFF475569))
                    }
                    SquareIconButton(onClick = onFitToScreen) {
                        CadIcons.FitScreen(tint = Color(0xFF475569))
                    }
                }

                // Inspector Sidebar Toggle
                val isInspOpen = editorState.isPropertiesSidebarOpen
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isInspOpen) Color(0xFFF1F5F9) else Color(0xFFFFFFFF))
                        .border(
                            1.dp,
                            if (isInspOpen) Color(0xFFCBD5E1) else Color(0xFFE2E8F0),
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { editorState.isPropertiesSidebarOpen = !editorState.isPropertiesSidebarOpen }
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CadIcons.Properties(tint = if (isInspOpen) Color(0xFF0F172A) else Color(0xFF64748B))
                        Text(
                            "Свойства",
                            fontSize = 11.sp,
                            fontWeight = if (isInspOpen) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isInspOpen) Color(0xFF0F172A) else Color(0xFF475569)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentedTabPill(
    icon: @Composable () -> Unit,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) Color(0xFFFFFFFF) else Color.Transparent)
            .let {
                if (isSelected) it.shadow(1.dp, RoundedCornerShape(6.dp)).border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(6.dp)) else it
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            icon()
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isSelected) Color(0xFF0F172A) else Color(0xFF64748B)
            )
        }
    }
}

@Composable
private fun GhostActionButton(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFF8FAFC))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            icon()
            Text(
                label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF334155)
            )
        }
    }
}

@Composable
private fun VectorToolToggle(
    icon: @Composable () -> Unit,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(if (isActive) Color(0xFFEFF6FF) else Color.Transparent)
            .let {
                if (isActive) it.border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(5.dp)) else it
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            icon()
            Text(
                label,
                fontSize = 11.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isActive) Color(0xFF1D4ED8) else Color(0xFF64748B)
            )
        }
    }
}

@Composable
private fun SettingsCheckItem(
    icon: @Composable () -> Unit,
    label: String,
    isChecked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (isChecked) Color(0xFFF8FAFC) else Color.Transparent)
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            icon()
            Text(label, fontSize = 12.sp, color = if (isChecked) Color(0xFF0F172A) else Color(0xFF64748B), fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal)
        }
        Switch(
            checked = isChecked,
            onCheckedChange = { onToggle() },
            modifier = Modifier.height(20.dp),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF2563EB),
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFCBD5E1)
            )
        )
    }
}

@Composable
private fun SquareIconButton(
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (enabled) Color.Transparent else Color(0xFFF1F5F9))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
