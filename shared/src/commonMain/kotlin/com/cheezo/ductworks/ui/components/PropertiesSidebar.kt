package com.cheezo.ductworks.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cheezo.ductworks.model.*
import com.cheezo.ductworks.ui.CadEditorState
import com.cheezo.ductworks.ui.canvas.ItemRenderer
import kotlin.math.round
import kotlin.math.roundToInt

@Composable
fun CadPropertiesSidebar(
    editorState: CadEditorState,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFFFFFFFF),
        contentColor = Color(0xFF0F172A),
        shadowElevation = 2.dp,
        modifier = modifier
            .width(280.dp)
            .fillMaxHeight()
            .border(width = 1.dp, color = Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(14.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Sidebar Header with Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Свойства",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFF1F5F9))
                        .clickable { editorState.isPropertiesSidebarOpen = false },
                    contentAlignment = Alignment.Center
                ) {
                    CadIcons.Close(tint = Color(0xFF64748B))
                }
            }

            HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)
            val selectedItemId = editorState.selectedItemId
            val selectedWallId = editorState.selectedWallId
            val selectedRoomId = editorState.selectedRoomId
            val selectedVertexId = editorState.selectedVertexId

            when {
                selectedItemId != null -> {
                    ItemProperties(
                        itemId = selectedItemId,
                        editorState = editorState
                    )
                }
                selectedWallId != null -> {
                    WallProperties(
                        wallId = selectedWallId,
                        editorState = editorState
                    )
                }
                selectedRoomId != null -> {
                    RoomProperties(
                        roomId = selectedRoomId,
                        editorState = editorState
                    )
                }
                selectedVertexId != null -> {
                    VertexProperties(
                        vertexId = selectedVertexId,
                        editorState = editorState
                    )
                }
                else -> {
                    ProjectOverviewProperties(editorState = editorState)
                }
            }
        }
    }
}

@Composable
private fun ItemProperties(
    itemId: String,
    editorState: CadEditorState
) {
    val item = editorState.document.items[itemId] ?: return

    SidebarSectionHeader("Свойства объекта")

    // Name & Category Badge
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF1F5F9))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            Text(
                "Категория: ${item.itemType.name.replace('_', ' ').lowercase()}",
                fontSize = 11.sp,
                color = Color(0xFF64748B)
            )
        }
    }

    // Dimensions: Width, Depth, Height
    Text("Габариты (Ш × Г × В, см):", fontSize = 12.sp, color = Color(0xFF475569), fontWeight = FontWeight.SemiBold)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        var wText by remember(item.id, item.widthCm) { mutableStateOf(item.widthCm.toInt().toString()) }
        OutlinedTextField(
            value = wText,
            onValueChange = { newVal ->
                if (newVal.all { it.isDigit() } && newVal.length <= 4) {
                    wText = newVal
                    newVal.toFloatOrNull()?.let { v ->
                        if (v in 5f..2000f) editorState.updateSelectedItemDimensions(widthCm = v)
                    }
                }
            },
            label = { Text("Ширина", fontSize = 10.sp) },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )

        var dText by remember(item.id, item.depthCm) { mutableStateOf(item.depthCm.toInt().toString()) }
        OutlinedTextField(
            value = dText,
            onValueChange = { newVal ->
                if (newVal.all { it.isDigit() } && newVal.length <= 4) {
                    dText = newVal
                    newVal.toFloatOrNull()?.let { v ->
                        if (v in 5f..2000f) editorState.updateSelectedItemDimensions(depthCm = v)
                    }
                }
            },
            label = { Text("Глубина", fontSize = 10.sp) },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )

        var hText by remember(item.id, item.heightCm) { mutableStateOf(item.heightCm.toInt().toString()) }
        OutlinedTextField(
            value = hText,
            onValueChange = { newVal ->
                if (newVal.all { it.isDigit() } && newVal.length <= 4) {
                    hText = newVal
                    newVal.toFloatOrNull()?.let { v ->
                        if (v in 5f..2000f) editorState.updateSelectedItemDimensions(heightCm = v)
                    }
                }
            },
            label = { Text("Высота", fontSize = 10.sp) },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
    }

    val isOpening = ItemRenderer.isOpeningType(item.itemType)
    if (isOpening) {
        Text("Высота от пола (см):", fontSize = 12.sp, color = Color(0xFF475569), fontWeight = FontWeight.SemiBold)
        var elevText by remember(item.id, item.elevationCm) { mutableStateOf(item.elevationCm.toInt().toString()) }
        OutlinedTextField(
            value = elevText,
            onValueChange = { newVal ->
                if (newVal.all { it.isDigit() } && newVal.length <= 4) {
                    elevText = newVal
                    newVal.toFloatOrNull()?.let { v ->
                        if (v in 0f..1000f) editorState.updateSelectedItemDimensions(elevationCm = v)
                    }
                }
            },
            label = { Text("Подоконник / от пола", fontSize = 11.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }

    // Duplicate & Delete actions
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = { editorState.duplicateSelectedItem() },
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF8FAFC)),
        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth().height(36.dp)
    ) {
        Text("📋 Дублировать объект", color = Color(0xFF0F172A), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }

    Button(
        onClick = { editorState.deleteSelectedItem() },
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth().height(36.dp)
    ) {
        Text("✕ Удалить объект", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WallProperties(
    wallId: String,
    editorState: CadEditorState
) {
    val wall = editorState.document.walls[wallId] ?: return
    val line = wall.toLineSegment(editorState.document.vertices)

    SidebarSectionHeader("Свойства стены")

    if (line != null) {
        StatCard(
            label = "Длина стены",
            value = editorState.formatLength(line.length),
            subtext = "Угол к горизонту: ${line.angleDegrees.roundToInt()}°"
        )
    }

    Text("Тип стены:", fontSize = 12.sp, color = Color(0xFF475569), fontWeight = FontWeight.SemiBold)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        WallType.values().forEach { type ->
            val isCurrent = wall.wallType == type
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isCurrent) Color(0xFF2563EB) else Color(0xFFF8FAFC))
                    .border(1.dp, if (isCurrent) Color(0xFF1D4ED8) else Color(0xFFCBD5E1), RoundedCornerShape(6.dp))
                    .clickable { editorState.updateWallType(wallId, type) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    when (type) {
                        WallType.EXTERIOR -> "Несущая"
                        WallType.INTERIOR -> "Межкомн."
                        WallType.PARTITION -> "Перегор."
                    },
                    fontSize = 11.sp,
                    color = if (isCurrent) Color.White else Color(0xFF1E293B),
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }

    Text("Толщина стены (см):", fontSize = 12.sp, color = Color(0xFF475569), fontWeight = FontWeight.SemiBold)
    var thicknessText by remember(wall.thicknessCm) {
        mutableStateOf(wall.thicknessCm.toInt().toString())
    }
    OutlinedTextField(
        value = thicknessText,
        onValueChange = { newVal ->
            if (newVal.all { it.isDigit() } && newVal.length <= 4) {
                thicknessText = newVal
                newVal.toFloatOrNull()?.let { v ->
                    if (v in 1f..300f) {
                        editorState.updateWallThickness(wallId, v)
                    }
                }
            }
        },
        label = { Text("Толщина, см", fontSize = 11.sp) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Text("Высота стены (см):", fontSize = 12.sp, color = Color(0xFF475569), fontWeight = FontWeight.SemiBold)
    var heightText by remember(wall.heightCm) {
        mutableStateOf(wall.heightCm.toInt().toString())
    }
    OutlinedTextField(
        value = heightText,
        onValueChange = { newVal ->
            if (newVal.all { it.isDigit() } && newVal.length <= 5) {
                heightText = newVal
                newVal.toFloatOrNull()?.let { v ->
                    if (v in 50f..1500f) {
                        editorState.updateWallHeight(wallId, v)
                    }
                }
            }
        },
        label = { Text("Высота, см", fontSize = 11.sp) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(Modifier.height(8.dp))
    Button(
        onClick = { editorState.deleteSelected() },
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Удалить стену", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RoomProperties(
    roomId: String,
    editorState: CadEditorState
) {
    val room = editorState.document.rooms.firstOrNull { it.id == roomId } ?: return
    val vertices = editorState.document.vertices

    SidebarSectionHeader("Свойства помещения")

    var nameInput by remember(room.id, room.name) { mutableStateOf(room.name) }

    OutlinedTextField(
        value = nameInput,
        onValueChange = {
            nameInput = it
            editorState.updateRoomProperties(room.id, name = it, type = room.roomType, ceilingHeightCm = room.ceilingHeightCm)
        },
        label = { Text("Название помещения", color = Color(0xFF64748B)) },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color(0xFF0F172A),
            unfocusedTextColor = Color(0xFF0F172A),
            focusedBorderColor = Color(0xFF2563EB),
            unfocusedBorderColor = Color(0xFFCBD5E1)
        ),
        modifier = Modifier.fillMaxWidth()
    )

    Text("Тип помещения:", fontSize = 12.sp, color = Color(0xFF475569), fontWeight = FontWeight.SemiBold)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val types = listOf(
            RoomType.LIVING_ROOM,
            RoomType.BEDROOM,
            RoomType.KITCHEN,
            RoomType.BATHROOM,
            RoomType.CORRIDOR,
            RoomType.OFFICE
        )
        types.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { type ->
                    val isCurrent = room.roomType == type
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isCurrent) Color(0xFF2563EB) else Color(0xFFF8FAFC))
                            .border(1.dp, if (isCurrent) Color(0xFF1D4ED8) else Color(0xFFCBD5E1), RoundedCornerShape(6.dp))
                            .clickable {
                                editorState.updateRoomProperties(room.id, name = room.name, type = type, ceilingHeightCm = room.ceilingHeightCm)
                            }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            type.titleRu,
                            fontSize = 11.sp,
                            color = if (isCurrent) Color.White else Color(0xFF1E293B),
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    val areaM2 = room.calculateAreaM2(vertices)
    val perimeterM = room.calculatePerimeterM(vertices)
    val volumeM3 = room.calculateVolumeM3(vertices)
    val requiredAirflow = room.calculateRequiredAirflowM3h(vertices)

    StatCard(
        label = "Площадь пола",
        value = editorState.formatArea(areaM2),
        subtext = "Периметр: ${(round(perimeterM * 10f) / 10f)} м"
    )

    // Phase 2 Ventilation Architecture Card
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF0F9FF))
            .border(1.dp, Color(0xFFBAE6FD), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("💨", fontSize = 14.sp)
                Text("Вентиляция (Этап 2)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0369A1))
            }
            Text("Объем помещения: ${editorState.formatVolume(volumeM3)}", fontSize = 11.sp, color = Color(0xFF0C4A6E))
            Text("Кратность обмена: ${room.roomType.recommendedAirExchangeRatePerHour} 1/ч", fontSize = 11.sp, color = Color(0xFF0369A1))
            Text("Расчетный приток воздуха: $requiredAirflow м³/ч", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0284C7))
        }
    }
}

@Composable
private fun VertexProperties(
    vertexId: String,
    editorState: CadEditorState
) {
    val vertex = editorState.document.vertices[vertexId] ?: return
    val connectedWalls = editorState.document.getConnectedWalls(vertexId)

    SidebarSectionHeader("Свойства узла / угла")

    StatCard(
        label = "Координаты на плане",
        value = "X: ${(round(vertex.position.x / 10f) / 10f)} м",
        subtext = "Y: ${(round(vertex.position.y / 10f) / 10f)} м"
    )

    StatCard(
        label = "Стен соединено в узле",
        value = "${connectedWalls.size} шт.",
        subtext = if (connectedWalls.size >= 2) "Замкнутый жесткий угол" else "Свободный торец стены"
    )

    Spacer(Modifier.height(8.dp))
    Button(
        onClick = { editorState.deleteSelected() },
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Удалить узел", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ProjectOverviewProperties(
    editorState: CadEditorState
) {
    val doc = editorState.document

    SidebarSectionHeader("Обзор плана здания")

    // Compact 2x2 Grid for key architecture metrics
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        CompactStatTile("Площадь", editorState.formatArea(doc.totalFloorAreaM2), "${doc.rooms.size} комн.", Modifier.weight(1f))
        CompactStatTile("Метраж стен", "${(round(doc.totalWallsLengthM * 10f) / 10f)} м", "${doc.walls.size} стен", Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        CompactStatTile("Узлы плана", "${doc.vertices.size} шт.", "Привязка 90°", Modifier.weight(1f))
        CompactStatTile("Высота стен", "${editorState.defaultWallHeightCm.toInt()} см", "Потолок", Modifier.weight(1f))
    }

    Text("Высота стен проекта:", fontSize = 11.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
    val projectHeightPresets = listOf(250f, 270f, 280f, 300f, 320f)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        projectHeightPresets.forEach { h ->
            val isCurrent = editorState.defaultWallHeightCm == h
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isCurrent) Color(0xFF2563EB) else Color(0xFFF1F5F9))
                    .clickable { editorState.setAllWallsHeight(h) }
                    .padding(vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${h.toInt()}",
                    fontSize = 11.sp,
                    color = if (isCurrent) Color.White else Color(0xFF334155),
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }

    Button(
        onClick = { editorState.addRectangleRoomAt(Point2D.Zero) },
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("+ Добавить комнату (4×3 м)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }

}

@Composable
private fun CompactStatTile(
    label: String,
    value: String,
    subtext: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF8FAFC))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, fontSize = 10.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            Text(subtext, fontSize = 9.sp, color = Color(0xFF94A3B8))
        }
    }
}

@Composable
private fun SidebarSectionHeader(title: String) {
    Text(
        title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF0F172A)
    )
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    subtext: String? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF8FAFC))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, fontSize = 10.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A))
            if (subtext != null) {
                Text(subtext, fontSize = 10.sp, color = Color(0xFF94A3B8))
            }
        }
    }
}
