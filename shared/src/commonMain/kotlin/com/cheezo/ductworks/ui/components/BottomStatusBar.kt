package com.cheezo.ductworks.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cheezo.ductworks.engine.threed.WallHeightMode3D
import com.cheezo.ductworks.ui.CadEditorState
import com.cheezo.ductworks.ui.CadViewportState
import com.cheezo.ductworks.ui.ViewMode
import com.cheezo.ductworks.ui.threed.CameraPreset3D
import com.cheezo.ductworks.ui.threed.KorenderCameraState
import kotlin.math.roundToInt

@Composable
fun CadBottomStatusBar(
    editorState: CadEditorState,
    viewport: CadViewportState,
    camera3DState: KorenderCameraState? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFFF8FAFC),
        contentColor = Color(0xFF0F172A),
        shadowElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = Color(0xFFE2E8F0))
    ) {
        if (editorState.viewMode == ViewMode.MODE_2D) {
            // ==================== 2D STATUS BAR ====================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Current Tool & Hint
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFF1F5F9))
                            .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(4.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            editorState.currentTool.titleRu,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F172A)
                        )
                    }

                    Text(
                        editorState.statusHint,
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }


                // Right: Coordinates & Zoom
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val pt = editorState.currentCursorWorldPoint
                    val coordText = if (pt != null) {
                        val xM = (pt.x / 100f * 100).roundToInt() / 100f
                        val yM = (pt.y / 100f * 100).roundToInt() / 100f
                        "X: $xM м  |  Y: $yM м"
                    } else {
                        "X: 0.00 м  |  Y: 0.00 м"
                    }

                    Text(
                        coordText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF475569)
                    )

                    Text(
                        "Масштаб: ${(viewport.zoom * 100).roundToInt()}%",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
        } else {
            // ==================== 3D STATUS & CONTROL BAR ====================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 5.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: 3D Camera & Geometry Controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 1. Геометрия (режимы высоты стен)
                    var geometryMenuExpanded by remember { mutableStateOf(false) }
                    val currentModeLabel = when (editorState.wallHeightMode3D) {
                        WallHeightMode3D.CUTAWAY_120CM -> "Срез 1.1 м"
                        WallHeightMode3D.FULL_HEIGHT -> "Полная"
                        WallHeightMode3D.XRAY_TRANSPARENT -> "Рентген"
                    }

                    Box {
                        BottomBarButton(
                            icon = { CadIcons.Model3D(tint = Color(0xFF334155)) },
                            label = "Геометрия: $currentModeLabel ▾",
                            onClick = { geometryMenuExpanded = true }
                        )

                        DropdownMenu(
                            expanded = geometryMenuExpanded,
                            onDismissRequest = { geometryMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Срез 1.1 м (стандарт)",
                                        fontSize = 12.sp,
                                        fontWeight = if (editorState.wallHeightMode3D == WallHeightMode3D.CUTAWAY_120CM) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    editorState.wallHeightMode3D = WallHeightMode3D.CUTAWAY_120CM
                                    geometryMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Полные стены 2.8 м",
                                        fontSize = 12.sp,
                                        fontWeight = if (editorState.wallHeightMode3D == WallHeightMode3D.FULL_HEIGHT) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    editorState.wallHeightMode3D = WallHeightMode3D.FULL_HEIGHT
                                    geometryMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Рентген (прозрачные стены)",
                                        fontSize = 12.sp,
                                        fontWeight = if (editorState.wallHeightMode3D == WallHeightMode3D.XRAY_TRANSPARENT) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    editorState.wallHeightMode3D = WallHeightMode3D.XRAY_TRANSPARENT
                                    geometryMenuExpanded = false
                                }
                            )
                        }
                    }

                    // 2. Изометрия (3D ракурс)
                    BottomBarButton(
                        icon = { CadIcons.IsometricView(tint = Color(0xFF334155)) },
                        label = "Изометрия",
                        onClick = {
                            camera3DState?.setPreset(CameraPreset3D.ISOMETRIC)
                        }
                    )

                    // 3. Сверху
                    BottomBarButton(
                        icon = { CadIcons.TopView(tint = Color(0xFF334155)) },
                        label = "Сверху",
                        onClick = {
                            camera3DState?.setPreset(CameraPreset3D.TOP_DOWN)
                        }
                    )

                    // 4. Спереди
                    BottomBarButton(
                        icon = { CadIcons.FrontView(tint = Color(0xFF334155)) },
                        label = "Спереди",
                        onClick = {
                            camera3DState?.setPreset(CameraPreset3D.FRONT)
                        }
                    )

                    // 5. Сброс вида
                    BottomBarButton(
                        icon = { CadIcons.ResetTarget(tint = Color(0xFF334155)) },
                        label = "Сброс вида",
                        onClick = {
                            val bounds = editorState.document.getBoundingBox()
                            val cx = bounds.center.x / 100f
                            val cz = bounds.center.y / 100f
                            val span = (maxOf(bounds.width, bounds.height, 450f) / 100f)
                            camera3DState?.frameBuilding(cx, cz, span)
                        }
                    )
                }

                // Middle: Navigation Hint
                Text(
                    "СКМ: Масштаб (колесо) и перемещение (тянуть)  •  ЛКМ: Вращение камеры  •  ПКМ: Свойства",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                // Right: 3D Zoom Controller
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFF1F5F9))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(6.dp))
                    .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val zoomPercent = camera3DState?.let {
                        (1100f / it.distance).roundToInt().coerceIn(20, 400)
                    } ?: 100

                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { camera3DState?.zoom(1.18f) },
                        contentAlignment = Alignment.Center
                    ) {
                        CadIcons.Minus(tint = Color(0xFF475569))
                    }
                    Text(
                        "$zoomPercent%",
                        fontSize = 11.sp,
                        color = Color(0xFF334155),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { camera3DState?.zoom(0.85f) },
                        contentAlignment = Alignment.Center
                    ) {
                        CadIcons.Plus(tint = Color(0xFF475569))
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomBarButton(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFF1F5F9))
            .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
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
private fun CadStatusChip(
    text: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (isActive) Color(0xFF0284C7) else Color(0xFFF1F5F9))
            .border(1.dp, if (isActive) Color(0xFF0369A1) else Color(0xFFCBD5E1), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            color = if (isActive) Color.White else Color(0xFF64748B)
        )
    }
}


