package com.cheezo.ductworks.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cheezo.ductworks.ui.CadEditorState
import com.cheezo.ductworks.ui.CadTool

@Composable
fun CadToolSidebar(
    editorState: CadEditorState,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFFFFFFFF),
        contentColor = Color(0xFF0F172A),
        shadowElevation = 2.dp,
        modifier = modifier
            .width(76.dp)
            .fillMaxHeight()
            .border(width = 1.dp, color = Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CadToolItem(
                symbol = "⬈",
                title = "Выбор",
                hotkey = "V",
                isSelected = editorState.currentTool == CadTool.SELECT,
                onClick = { editorState.selectTool(CadTool.SELECT) }
            )

            CadToolItem(
                symbol = "━",
                title = "Стена",
                hotkey = "W",
                isSelected = editorState.currentTool == CadTool.DRAW_WALL,
                onClick = { editorState.selectTool(CadTool.DRAW_WALL) }
            )

            CadToolItem(
                symbol = "◻",
                title = "Комната",
                hotkey = "R",
                isSelected = editorState.currentTool == CadTool.RECTANGLE_ROOM,
                onClick = { editorState.selectTool(CadTool.RECTANGLE_ROOM) }
            )

            CadToolItem(
                symbol = "◲",
                title = "L-контур",
                hotkey = "L",
                isSelected = editorState.currentTool == CadTool.L_SHAPED_ROOM,
                onClick = { editorState.selectTool(CadTool.L_SHAPED_ROOM) }
            )

            CadToolItem(
                symbol = "✂",
                title = "Разрез",
                hotkey = "S",
                isSelected = editorState.currentTool == CadTool.SPLIT_WALL,
                onClick = { editorState.selectTool(CadTool.SPLIT_WALL) }
            )

            CadToolItem(
                symbol = "⇲",
                title = "Замер",
                hotkey = "M",
                isSelected = editorState.currentTool == CadTool.MEASURE,
                onClick = { editorState.selectTool(CadTool.MEASURE) }
            )

            Spacer(Modifier.weight(1f))

            CadToolItem(
                symbol = "✕",
                title = "Удалить",
                hotkey = "Del",
                isSelected = editorState.currentTool == CadTool.DELETE,
                danger = true,
                onClick = {
                    if (editorState.selectedWallId != null || editorState.selectedVertexId != null) {
                        editorState.deleteSelected()
                    } else {
                        editorState.selectTool(CadTool.DELETE)
                    }
                }
            )
        }
    }
}

@Composable
private fun CadToolItem(
    symbol: String,
    title: String,
    hotkey: String,
    isSelected: Boolean,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val activeBg = if (danger) Color(0xFFDC2626) else Color(0xFF2563EB)
    val inactiveBg = Color(0xFFF8FAFC)
    val borderCol = if (isSelected) (if (danger) Color(0xFFB91C1C) else Color(0xFF1D4ED8)) else Color(0xFFE2E8F0)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) activeBg else inactiveBg)
            .border(1.dp, borderCol, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                symbol,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isSelected) Color.White else (if (danger) Color(0xFFDC2626) else Color(0xFF1E293B))
            )
            Text(
                title,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else Color(0xFF334155)
            )
            Text(
                "[$hotkey]",
                fontSize = 9.sp,
                color = if (isSelected) Color(0xFFBFDBFE) else Color(0xFF94A3B8)
            )
        }
    }
}
