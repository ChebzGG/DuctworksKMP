package com.cheezo.ductworks.ui.canvas

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cheezo.ductworks.model.*
import com.cheezo.ductworks.ui.CadEditorState
import com.cheezo.ductworks.ui.CadViewportState
import kotlin.math.*

/**
 * Renders professional top-down 2D architectural CAD symbols for furniture, doors, windows, and HVAC fixtures.
 * Matches Planner 5D / AutoCAD architectural standards.
 */
object ItemRenderer {

    val ItemFillColor = Color(0xFFF8FAFC)
    val ItemBorderColor = Color(0xFF334155)
    val ItemDetailColor = Color(0xFF64748B)
    val ItemSelectedBorder = Color(0xFF2563EB)
    val GlassColor = Color(0xFF38BDF8)
    val WoodColor = Color(0xFFF1F5F9)
    val FabricColor = Color(0xFFE2E8F0)
    val SanitaryColor = Color(0xFFFFFFFF)
    val SupplyColor = Color(0xFF0284C7)
    val ExhaustColor = Color(0xFFEA580C)

    fun drawAllItems(
        drawScope: DrawScope,
        editorState: CadEditorState,
        viewport: CadViewportState,
        textMeasurer: TextMeasurer,
        painters: Map<String, Painter> = emptyMap()
    ) {
        drawFurnitureAndFixtures(drawScope, editorState, viewport, painters)
        drawOpenings(drawScope, editorState, viewport, painters)
        drawSelectionOverlay(drawScope, editorState, viewport, textMeasurer)
    }

    fun isOpeningType(type: ItemType): Boolean = type.isOpening

    fun getPainterForItem(item: PlacedItem, painters: Map<String, Painter>): Painter? {
        if (painters.isEmpty()) return null
        val catalogItem = CatalogItem.ALL_ITEMS.firstOrNull { it.id == item.catalogItemId }
            ?: CatalogItem.ALL_ITEMS.firstOrNull { it.itemType == item.itemType }
        if (catalogItem != null && catalogItem.svgResName.isNotBlank()) {
            val p = painters[catalogItem.svgResName]
            if (p != null) return p
        }
        return painters["elem_" + item.itemType.name.lowercase()]
    }

    /**
     * Draws architectural openings (doors and windows) cutting through walls.
     */
    fun drawOpenings(
        drawScope: DrawScope,
        editorState: CadEditorState,
        viewport: CadViewportState,
        painters: Map<String, Painter> = emptyMap()
    ) {
        val items = editorState.document.items.values
        for (item in items) {
            if (item.wallId != null || isOpeningType(item.itemType)) {
                drawOpening(drawScope, item, editorState, viewport, painters)
            }
        }
    }

    /**
     * Draws furniture, sanitaries, and engineering fixtures placed on the floor.
     */
    fun drawFurnitureAndFixtures(
        drawScope: DrawScope,
        editorState: CadEditorState,
        viewport: CadViewportState,
        painters: Map<String, Painter> = emptyMap()
    ) {
        val items = editorState.document.items.values.sortedBy { it.zIndex }
        for (item in items) {
            if (item.wallId == null && !isOpeningType(item.itemType)) {
                drawFurnitureOrFixture(drawScope, item, editorState, viewport, painters)
            }
        }
    }

    /**
     * Draws selection border, corner handles, and rotation knob.
     */
    fun drawSelectionOverlay(
        drawScope: DrawScope,
        editorState: CadEditorState,
        viewport: CadViewportState,
        textMeasurer: TextMeasurer
    ) {
        val selectedId = editorState.selectedItemId ?: return
        val item = editorState.document.items[selectedId] ?: return
        drawSelectionOverlay(drawScope, item, viewport, textMeasurer)
    }

    /**
     * Renders architectural doors and windows cut into walls.
     */
    private fun drawOpening(
        drawScope: DrawScope,
        item: PlacedItem,
        editorState: CadEditorState,
        viewport: CadViewportState,
        painters: Map<String, Painter> = emptyMap()
    ) {
        with(drawScope) {
            val isSelected = item.id == editorState.selectedItemId
            val centerScreen = viewport.worldToScreen(item.position)
            val angle = item.rotationDegrees

            // Always cut full wall thickness cleanly
            val attachedWall = item.wallId?.let { editorState.document.walls[it] }
            val wallThicknessCm = attachedWall?.thicknessCm ?: item.depthCm
            val maskDepthCm = max(wallThicknessCm, item.depthCm) + 2f

            val widthScreen = item.widthCm * viewport.zoom
            val depthScreen = maskDepthCm * viewport.zoom

            // Save canvas transform
            drawContext.canvas.save()
            drawContext.canvas.translate(centerScreen.x, centerScreen.y)
            drawContext.canvas.rotate(angle)

            val halfW = widthScreen * 0.5f
            val halfD = depthScreen * 0.5f

            // Wall opening gap (white fill cleanly masking the wall)
            drawRect(
                color = Color.White,
                topLeft = Offset(-halfW, -halfD),
                size = Size(widthScreen, depthScreen),
                style = Fill
            )

            val strokeColor = if (isSelected) ItemSelectedBorder else ItemBorderColor

            when (item.itemType) {
                ItemType.DOOR_SINGLE -> {
                        // Left and right jambs
                        drawRect(Color(0xFF1E293B), Offset(-halfW, -halfD), Size(4f, depthScreen))
                    drawRect(Color(0xFF1E293B), Offset(halfW - 4f, -halfD), Size(4f, depthScreen))

                    // Door leaf at 90-degree swing
                    val swingRadius = widthScreen - 8f
                    val leafStart = Offset(-halfW + 4f, halfD)
                    val leafEnd = Offset(-halfW + 4f, halfD + swingRadius)

                    drawLine(
                        color = if (isSelected) ItemSelectedBorder else ItemBorderColor,
                        start = leafStart,
                        end = leafEnd,
                        strokeWidth = 2.5f
                    )

                    // Door quarter-circle swing arc (dashed)
                    val arcRect = Size(swingRadius * 2f, swingRadius * 2f)
                    val arcTopLeft = Offset(-halfW + 4f - swingRadius, halfD - swingRadius)
                    drawArc(
                        color = if (isSelected) ItemSelectedBorder else ItemDetailColor,
                        startAngle = 0f,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcRect,
                        style = Stroke(width = 1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f))
                    )
                }

                ItemType.DOOR_DOUBLE, ItemType.DOOR_TECH_DOUBLE -> {
                    val halfDoor = (widthScreen - 8f) * 0.5f
                    // Left and right jambs
                    drawRect(Color(0xFF1E293B), Offset(-halfW, -halfD), Size(4f, depthScreen))
                    drawRect(Color(0xFF1E293B), Offset(halfW - 4f, -halfD), Size(4f, depthScreen))

                    // Left leaf & swing
                    drawLine(if (isSelected) ItemSelectedBorder else ItemBorderColor, Offset(-halfW + 4f, halfD), Offset(-halfW + 4f, halfD + halfDoor), strokeWidth = 2.5f)
                    drawArc(ItemDetailColor, 0f, 90f, false, Offset(-halfW + 4f - halfDoor, halfD - halfDoor), Size(halfDoor * 2f, halfDoor * 2f), style = Stroke(1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f), 0f)))

                    // Right leaf & swing
                    drawLine(if (isSelected) ItemSelectedBorder else ItemBorderColor, Offset(halfW - 4f, halfD), Offset(halfW - 4f, halfD + halfDoor), strokeWidth = 2.5f)
                    drawArc(ItemDetailColor, 90f, 90f, false, Offset(halfW - 4f - halfDoor, halfD - halfDoor), Size(halfDoor * 2f, halfDoor * 2f), style = Stroke(1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f), 0f)))
                }

                ItemType.DOOR_SLIDING -> {
                    // Sliding panels track
                    drawLine(ItemBorderColor, Offset(-halfW, 0f), Offset(halfW, 0f), strokeWidth = 1.5f)
                    drawRect(FabricColor, Offset(-halfW, -3f), Size(widthScreen * 0.55f, 6f))
                    drawRect(ItemBorderColor, Offset(-halfW, -3f), Size(widthScreen * 0.55f, 6f), style = Stroke(1f))
                    drawRect(FabricColor, Offset(-halfW * 0.1f, -6f), Size(widthScreen * 0.55f, 6f))
                    drawRect(ItemBorderColor, Offset(-halfW * 0.1f, -6f), Size(widthScreen * 0.55f, 6f), style = Stroke(1f))
                }

                ItemType.DOOR_POCKET -> {
                    // Pocket door sliding into wall recess
                    drawRect(Color(0xFF1E293B), Offset(-halfW, -halfD), Size(4f, depthScreen))
                    drawRect(Color(0xFF1E293B), Offset(halfW - 4f, -halfD), Size(4f, depthScreen))
                    // Pocket recess box (dashed)
                    drawRect(
                        Color(0xFF94A3B8),
                        Offset(-halfW, -halfD * 0.5f),
                        Size(halfW, depthScreen * 0.5f),
                        style = Stroke(1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f))
                    )
                    // Sliding leaf
                    drawRect(Color(0xFF334155), Offset(-halfW * 0.3f, -2.5f), Size(halfW * 1.2f, 5f), style = Fill)
                }

                ItemType.DOOR_ENTRANCE, ItemType.DOOR_SERVER_RACK, ItemType.DOOR_FIREPROOF -> {
                    // Reinforced entrance/server/fireproof door with thicker jambs and heavy leaf
                    drawRect(Color(0xFF0F172A), Offset(-halfW, -halfD), Size(6f, depthScreen))
                    drawRect(Color(0xFF0F172A), Offset(halfW - 6f, -halfD), Size(6f, depthScreen))
                    // Threshold steel plate
                    drawLine(Color(0xFF475569), Offset(-halfW, halfD), Offset(halfW, halfD), strokeWidth = 3f)

                    val swingRadius = widthScreen - 12f
                    val leafStart = Offset(-halfW + 6f, halfD)
                    val leafEnd = Offset(-halfW + 6f, halfD + swingRadius)
                    drawLine(if (isSelected) ItemSelectedBorder else Color(0xFF0F172A), leafStart, leafEnd, strokeWidth = 3.5f)
                    drawArc(
                        color = if (isSelected) ItemSelectedBorder else ItemDetailColor,
                        startAngle = 0f,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = Offset(-halfW + 6f - swingRadius, halfD - swingRadius),
                        size = Size(swingRadius * 2f, swingRadius * 2f),
                        style = Stroke(1.4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f))
                    )
                }

                ItemType.WINDOW_SINGLE, ItemType.WINDOW_DOUBLE, ItemType.WINDOW_PANORAMIC, ItemType.WINDOW_CASHIER -> {
                    // Window frame jambs
                    drawRect(Color(0xFF1E293B), Offset(-halfW, -halfD), Size(6f, depthScreen))
                    drawRect(Color(0xFF1E293B), Offset(halfW - 6f, -halfD), Size(6f, depthScreen))

                    // Window sill line
                    drawLine(Color(0xFF0F172A), Offset(-halfW - 4f, halfD + 2f), Offset(halfW + 4f, halfD + 2f), strokeWidth = 2f)

                    // Dual glass pane lines in center
                    val glassColor = if (isSelected) ItemSelectedBorder else GlassColor
                    drawLine(glassColor, Offset(-halfW + 6f, -2f), Offset(halfW - 6f, -2f), strokeWidth = 2f)
                    drawLine(glassColor, Offset(-halfW + 6f, 2f), Offset(halfW - 6f, 2f), strokeWidth = 2f)

                    // Double window center mullion
                    if (item.itemType != ItemType.WINDOW_SINGLE) {
                        drawRect(Color(0xFF1E293B), Offset(-3f, -halfD), Size(6f, depthScreen))
                    }
                }

                ItemType.WINDOW_TRIPLE -> {
                    // Window frame jambs
                    drawRect(Color(0xFF1E293B), Offset(-halfW, -halfD), Size(6f, depthScreen))
                    drawRect(Color(0xFF1E293B), Offset(halfW - 6f, -halfD), Size(6f, depthScreen))
                    drawLine(Color(0xFF0F172A), Offset(-halfW - 4f, halfD + 2f), Offset(halfW + 4f, halfD + 2f), strokeWidth = 2f)

                    val glassColor = if (isSelected) ItemSelectedBorder else GlassColor
                    drawLine(glassColor, Offset(-halfW + 6f, -2f), Offset(halfW - 6f, -2f), strokeWidth = 2f)
                    drawLine(glassColor, Offset(-halfW + 6f, 2f), Offset(halfW - 6f, 2f), strokeWidth = 2f)

                    // 2 Mullions (3 panels)
                    val panelW = widthScreen / 3f
                    drawRect(Color(0xFF1E293B), Offset(-halfW + panelW - 3f, -halfD), Size(6f, depthScreen))
                    drawRect(Color(0xFF1E293B), Offset(-halfW + panelW * 2f - 3f, -halfD), Size(6f, depthScreen))
                }

                ItemType.WINDOW_CORNER -> {
                    // Corner glass window
                    drawRect(Color(0xFF1E293B), Offset(-halfW, -halfD), Size(6f, depthScreen))
                    drawRect(Color(0xFF1E293B), Offset(halfW - 6f, -halfD), Size(6f, depthScreen))
                    val glassColor = if (isSelected) ItemSelectedBorder else GlassColor
                    drawLine(glassColor, Offset(-halfW, 0f), Offset(0f, 0f), strokeWidth = 2.5f)
                    drawLine(glassColor, Offset(0f, 0f), Offset(0f, halfD), strokeWidth = 2.5f)
                    drawCircle(Color(0xFF1E293B), 4f, Offset(0f, 0f))
                }

                else -> {
                    // Generic opening / arch
                    drawLine(Color(0xFF64748B), Offset(-halfW, -halfD), Offset(halfW, -halfD), strokeWidth = 1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f))
                    drawLine(Color(0xFF64748B), Offset(-halfW, halfD), Offset(halfW, halfD), strokeWidth = 1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f))
                }
            }

            drawContext.canvas.restore()
        }
    }

    /**
     * Renders furniture and HVAC fixtures in top-down architectural blueprint standard.
     */
    private fun drawFurnitureOrFixture(
        drawScope: DrawScope,
        item: PlacedItem,
        editorState: CadEditorState,
        viewport: CadViewportState,
        painters: Map<String, Painter> = emptyMap()
    ) {
        with(drawScope) {
            val isSelected = item.id == editorState.selectedItemId
            val isHovered = item.id == editorState.hoveredItemId

            val centerScreen = viewport.worldToScreen(item.position)

            drawContext.canvas.save()
            drawContext.canvas.translate(centerScreen.x, centerScreen.y)
            drawContext.canvas.rotate(item.rotationDegrees)
            drawContext.canvas.scale(viewport.zoom, viewport.zoom)

            val widthScreen = item.widthCm
            val depthScreen = item.depthCm

            val halfW = widthScreen * 0.5f
            val halfD = depthScreen * 0.5f
            val baseRect = Offset(-halfW, -halfD)
            val baseSize = Size(widthScreen, depthScreen)

            val strokeColor = when {
                isSelected -> ItemSelectedBorder
                isHovered -> Color(0xFF3B82F6)
                else -> Color(0xFF1E293B)
            }
            val strokeW = (if (isSelected) 2.2f else 1.4f) / viewport.zoom
            val detailColor = if (isSelected) ItemSelectedBorder.copy(alpha = 0.8f) else Color(0xFF64748B)
            val fineColor = if (isSelected) ItemSelectedBorder.copy(alpha = 0.6f) else Color(0xFF94A3B8)

            val z = 1f
            fun CornerRadius(x: Float, y: Float): androidx.compose.ui.geometry.CornerRadius =
                androidx.compose.ui.geometry.CornerRadius(x, y)

            when (item.itemType) {
                // --- Living Room ---
                ItemType.SOFA_STRAIGHT -> {
                    drawRoundRect(FabricColor, baseRect, baseSize, CornerRadius(6f, 6f))
                    drawRoundRect(strokeColor, baseRect, baseSize, CornerRadius(6f, 6f), style = Stroke(strokeW))

                    // Ergonomic Backrest at REAR (+halfD, against wall) - strictly matches 3D model
                    val backH = depthScreen * 0.28f
                    val backY = halfD - backH - 4f
                    drawRoundRect(Color(0xFFCBD5E1), Offset(-halfW + 4f, backY), Size(widthScreen - 8f, backH), CornerRadius(4f, 4f))
                    drawRoundRect(detailColor, Offset(-halfW + 4f, backY), Size(widthScreen - 8f, backH), CornerRadius(4f, 4f), style = Stroke(1f))

                    // Left & right armrests along full depth
                    val armW = widthScreen * 0.12f
                    drawRoundRect(Color(0xFFCBD5E1), Offset(-halfW + 3f, -halfD + 4f), Size(armW, depthScreen - 8f), CornerRadius(4f, 4f))
                    drawRoundRect(detailColor, Offset(-halfW + 3f, -halfD + 4f), Size(armW, depthScreen - 8f), CornerRadius(4f, 4f), style = Stroke(1f))
                    drawRoundRect(Color(0xFFCBD5E1), Offset(halfW - armW - 3f, -halfD + 4f), Size(armW, depthScreen - 8f), CornerRadius(4f, 4f))
                    drawRoundRect(detailColor, Offset(halfW - armW - 3f, -halfD + 4f), Size(armW, depthScreen - 8f), CornerRadius(4f, 4f), style = Stroke(1f))

                    // Plush seat cushions in FRONT (-halfD, facing into room/coffee table)
                    val seatW = widthScreen - armW * 2f - 6f
                    val cushionCount = 3
                    val cW = seatW / cushionCount
                    val seatY = -halfD + 4f
                    val seatH = depthScreen - backH - 8f
                    for (i in 0 until cushionCount) {
                        val cx = -halfW + armW + 3f + cW * i
                        drawRoundRect(FabricColor, Offset(cx + 2f, seatY), Size(cW - 4f, seatH), CornerRadius(3f, 3f))
                        drawRoundRect(detailColor, Offset(cx + 2f, seatY), Size(cW - 4f, seatH), CornerRadius(3f, 3f), style = Stroke(1f))
                    }

                    // Designer accent throw pillows against backrest
                    val pW = min(armW * 1.5f, 16f)
                    val pH = pW * 0.65f
                    drawRoundRect(Color(0xFF93C5FD), Offset(-halfW + armW + 5f, backY - pH * 0.5f), Size(pW, pH), CornerRadius(2.5f, 2.5f))
                    drawRoundRect(Color(0xFFFDBA74), Offset(halfW - armW - pW - 5f, backY - pH * 0.5f), Size(pW, pH), CornerRadius(2.5f, 2.5f))
                }

                ItemType.SOFA_CORNER -> {
                    val cutW = widthScreen * 0.45f
                    val cutH = depthScreen * 0.45f
                    val cutLeft = halfW - cutW
                    val cutBottom = -halfD + cutH

                    // True L-shaped geometric outline with zero white background
                    val sofaLPath = Path().apply {
                        moveTo(-halfW, -halfD)
                        lineTo(cutLeft, -halfD)
                        lineTo(cutLeft, cutBottom)
                        lineTo(halfW, cutBottom)
                        lineTo(halfW, halfD)
                        lineTo(-halfW, halfD)
                        close()
                    }
                    drawPath(sofaLPath, FabricColor, style = Fill)
                    drawPath(sofaLPath, strokeColor, style = Stroke(strokeW))

                    // L-shaped continuous backrest along REAR (+halfD) and LEFT (-halfW)
                    val backT = min(widthScreen, depthScreen) * 0.22f
                    drawRoundRect(Color(0xFFCBD5E1), Offset(-halfW + 4f, halfD - backT - 4f), Size(widthScreen - 8f, backT), CornerRadius(3f, 3f))
                    drawRoundRect(Color(0xFFCBD5E1), Offset(-halfW + 4f, -halfD + 4f), Size(backT, depthScreen - 8f), CornerRadius(3f, 3f))
                    drawRoundRect(detailColor, Offset(-halfW + 4f, halfD - backT - 4f), Size(widthScreen - 8f, backT), CornerRadius(3f, 3f), style = Stroke(1f))
                    drawRoundRect(detailColor, Offset(-halfW + 4f, -halfD + 4f), Size(backT, depthScreen - 8f), CornerRadius(3f, 3f), style = Stroke(1f))

                    // Armrests at outer ends
                    drawRoundRect(Color(0xFFCBD5E1), Offset(halfW - backT - 3f, halfD - backT * 1.4f - 3f), Size(backT, backT * 1.4f), CornerRadius(3f, 3f))
                    drawRoundRect(detailColor, Offset(halfW - backT - 3f, halfD - backT * 1.4f - 3f), Size(backT, backT * 1.4f), CornerRadius(3f, 3f), style = Stroke(1f))
                    drawRoundRect(Color(0xFFCBD5E1), Offset(-halfW + 4f, -halfD + 4f), Size(backT * 1.4f, backT), CornerRadius(3f, 3f))
                    drawRoundRect(detailColor, Offset(-halfW + 4f, -halfD + 4f), Size(backT * 1.4f, backT), CornerRadius(3f, 3f), style = Stroke(1f))

                    // Accent decorative throw pillows
                    val pW = min(backT * 1.4f, 18f)
                    val pH = pW * 0.65f
                    drawRoundRect(Color(0xFF93C5FD), Offset(-halfW + backT + 5f, halfD - backT - pH - 3f), Size(pW, pH), CornerRadius(2.5f, 2.5f))
                    drawRoundRect(Color(0xFFFDBA74), Offset(halfW - backT - pW - 5f, halfD - backT - pH - 3f), Size(pW, pH), CornerRadius(2.5f, 2.5f))
                }

                ItemType.SOFA_U_SHAPED -> {
                    val cutW = widthScreen * 0.42f
                    val cutH = depthScreen * 0.55f
                    val cutLeft = -cutW * 0.5f
                    val cutRight = cutW * 0.5f
                    val cutBottom = -halfD + cutH

                    // True U-shaped geometric outline with zero white background
                    val sofaUPath = Path().apply {
                        moveTo(-halfW, -halfD)
                        lineTo(cutLeft, -halfD)
                        lineTo(cutLeft, cutBottom)
                        lineTo(cutRight, cutBottom)
                        lineTo(cutRight, -halfD)
                        lineTo(halfW, -halfD)
                        lineTo(halfW, halfD)
                        lineTo(-halfW, halfD)
                        close()
                    }
                    drawPath(sofaUPath, FabricColor, style = Fill)
                    drawPath(sofaUPath, strokeColor, style = Stroke(strokeW))

                    val bT = min(widthScreen, depthScreen) * 0.2f
                    drawRect(Color(0xFFCBD5E1), Offset(-halfW + 3f, halfD - bT - 3f), Size(widthScreen - 6f, bT))
                    drawRect(Color(0xFFCBD5E1), Offset(-halfW + 3f, -halfD + 3f), Size(bT, depthScreen - 6f))
                    drawRect(Color(0xFFCBD5E1), Offset(halfW - bT - 3f, -halfD + 3f), Size(bT, depthScreen - 6f))
                    drawRect(detailColor, Offset(-halfW + 3f, halfD - bT - 3f), Size(widthScreen - 6f, bT), style = Stroke(1f))
                    drawRect(detailColor, Offset(-halfW + 3f, -halfD + 3f), Size(bT, depthScreen - 6f), style = Stroke(1f))
                    drawRect(detailColor, Offset(halfW - bT - 3f, -halfD + 3f), Size(bT, depthScreen - 6f), style = Stroke(1f))

                    // Accent corner pillows
                    val pW = min(bT * 1.3f, 18f)
                    val pH = pW * 0.65f
                    drawRoundRect(Color(0xFF93C5FD), Offset(-halfW + bT + 4f, halfD - bT - pH - 3f), Size(pW, pH), CornerRadius(2.5f, 2.5f))
                    drawRoundRect(Color(0xFFFDBA74), Offset(halfW - bT - pW - 4f, halfD - bT - pH - 3f), Size(pW, pH), CornerRadius(2.5f, 2.5f))
                }

                ItemType.ARMCHAIR -> {
                    drawRoundRect(FabricColor, baseRect, baseSize, CornerRadius(8f, 8f))
                    drawRoundRect(strokeColor, baseRect, baseSize, CornerRadius(8f, 8f), style = Stroke(strokeW))

                    // Backrest at REAR (+halfD)
                    val backH = depthScreen * 0.26f
                    val backY = halfD - backH - 4f
                    drawRoundRect(Color(0xFFCBD5E1), Offset(-halfW + 4f, backY), Size(widthScreen - 8f, backH), CornerRadius(4f, 4f))
                    drawRoundRect(detailColor, Offset(-halfW + 4f, backY), Size(widthScreen - 8f, backH), CornerRadius(4f, 4f), style = Stroke(1f))

                    // Armrests on sides
                    val armW = widthScreen * 0.18f
                    drawRoundRect(Color(0xFFCBD5E1), Offset(-halfW + 3f, -halfD + 4f), Size(armW, depthScreen - 8f), CornerRadius(4f, 4f))
                    drawRoundRect(detailColor, Offset(-halfW + 3f, -halfD + 4f), Size(armW, depthScreen - 8f), CornerRadius(4f, 4f), style = Stroke(1f))
                    drawRoundRect(Color(0xFFCBD5E1), Offset(halfW - armW - 3f, -halfD + 4f), Size(armW, depthScreen - 8f), CornerRadius(4f, 4f))
                    drawRoundRect(detailColor, Offset(halfW - armW - 3f, -halfD + 4f), Size(armW, depthScreen - 8f), CornerRadius(4f, 4f), style = Stroke(1f))

                    // Central seat cushion in FRONT (-halfD)
                    val cW = widthScreen - armW * 2f - 10f
                    val cH = depthScreen - backH - 10f
                    drawRoundRect(FabricColor, Offset(-cW * 0.5f, -halfD + 5f), Size(cW, cH), CornerRadius(4f, 4f))
                    drawRoundRect(detailColor, Offset(-cW * 0.5f, -halfD + 5f), Size(cW, cH), CornerRadius(4f, 4f), style = Stroke(1f))
                }

                ItemType.COFFEE_TABLE -> {
                    drawRoundRect(WoodColor, baseRect, baseSize, CornerRadius(4f, 4f))
                    drawRoundRect(strokeColor, baseRect, baseSize, CornerRadius(4f, 4f), style = Stroke(strokeW))
                    drawRoundRect(detailColor, Offset(-halfW + 5f, -halfD + 5f), Size(widthScreen - 10f, depthScreen - 10f), CornerRadius(2f, 2f), style = Stroke(0.8f))
                    val legS = 6f
                    drawRect(strokeColor, Offset(-halfW + 3f, -halfD + 3f), Size(legS, legS))
                    drawRect(strokeColor, Offset(halfW - legS - 3f, -halfD + 3f), Size(legS, legS))
                    drawRect(strokeColor, Offset(-halfW + 3f, halfD - legS - 3f), Size(legS, legS))
                    drawRect(strokeColor, Offset(halfW - legS - 3f, halfD - legS - 3f), Size(legS, legS))
                }

                ItemType.COFFEE_TABLE_ROUND -> {
                    val r = widthScreen * 0.48f
                    drawCircle(WoodColor, r, Offset.Zero)
                    drawCircle(strokeColor, r, Offset.Zero, style = Stroke(strokeW))
                    drawCircle(detailColor, r * 0.78f, Offset.Zero, style = Stroke(0.8f))
                    val legDist = r * 0.55f
                    drawCircle(strokeColor, 3f, Offset(-legDist, -legDist))
                    drawCircle(strokeColor, 3f, Offset(legDist, -legDist))
                    drawCircle(strokeColor, 3f, Offset(-legDist, legDist))
                    drawCircle(strokeColor, 3f, Offset(legDist, legDist))
                }

                ItemType.TV_STAND -> {
                    drawRoundRect(WoodColor, baseRect, baseSize, CornerRadius(3f, 3f))
                    drawRoundRect(strokeColor, baseRect, baseSize, CornerRadius(3f, 3f), style = Stroke(strokeW))
                    // Back wall line
                    drawLine(strokeColor, Offset(-halfW + 4f, halfD - 2f), Offset(halfW - 4f, halfD - 2f), strokeWidth = 1.5f)
                    val segW = widthScreen / 3f
                    drawLine(detailColor, Offset(-halfW + segW, -halfD), Offset(-halfW + segW, halfD), strokeWidth = 1f)
                    drawLine(detailColor, Offset(-halfW + segW * 2f, -halfD), Offset(-halfW + segW * 2f, halfD), strokeWidth = 1f)
                    // TV screen slightly towards rear (+halfD), facing front (-halfD)
                    val tvW = widthScreen * 0.82f
                    drawRoundRect(Color(0xFF0F172A), Offset(-tvW * 0.5f, halfD * 0.15f - 3f), Size(tvW, 6f), CornerRadius(1.5f, 1.5f))
                    drawRoundRect(Color(0xFF475569), Offset(-tvW * 0.15f, halfD * 0.15f - 5f), Size(tvW * 0.3f, 10f), CornerRadius(2f, 2f))
                    // Front drawer handles facing room (-halfD)
                    drawRoundRect(detailColor, Offset(-halfW + segW * 0.5f - 10f, -halfD + 3f), Size(20f, 3f), CornerRadius(1.5f, 1.5f))
                    drawRoundRect(detailColor, Offset(-10f, -halfD + 3f), Size(20f, 3f), CornerRadius(1.5f, 1.5f))
                    drawRoundRect(detailColor, Offset(halfW - segW * 0.5f - 10f, -halfD + 3f), Size(20f, 3f), CornerRadius(1.5f, 1.5f))
                }

                ItemType.BOOKSHELF -> {
                    drawRect(WoodColor, baseRect, baseSize)
                    drawRect(strokeColor, baseRect, baseSize, style = Stroke(strokeW))
                    // Solid back panel against wall (+halfD)
                    drawLine(strokeColor, Offset(-halfW, halfD - 2f), Offset(halfW, halfD - 2f), strokeWidth = 2.5f)
                    val shelfCount = 4
                    for (i in 1 until shelfCount) {
                        val sx = -halfW + (widthScreen / shelfCount) * i
                        drawLine(strokeColor, Offset(sx, -halfD), Offset(sx, halfD - 2f), strokeWidth = 1.2f)
                        for (b in 1..3) {
                            val by = -halfD + (depthScreen / 4f) * b
                            drawLine(detailColor, Offset(sx - 3f, by), Offset(sx + 3f, by), strokeWidth = 0.8f)
                        }
                    }
                }

                ItemType.FLOOR_LAMP -> {
                    val r = widthScreen * 0.45f
                    drawCircle(Color(0xFFFEF3C7), r, Offset.Zero)
                    drawCircle(strokeColor, r, Offset.Zero, style = Stroke(strokeW))
                    drawCircle(strokeColor, r * 0.28f, Offset.Zero)
                    drawLine(detailColor, Offset.Zero, Offset(0f, -halfD), strokeWidth = 1.2f)
                    drawLine(detailColor, Offset.Zero, Offset(-halfW * 0.86f, halfD * 0.5f), strokeWidth = 1.2f)
                    drawLine(detailColor, Offset.Zero, Offset(halfW * 0.86f, halfD * 0.5f), strokeWidth = 1.2f)
                }

                ItemType.CARPET_RECT -> {
                    drawRoundRect(Color(0xFFF1F5F9), baseRect, baseSize, CornerRadius(6f, 6f))
                    drawRoundRect(Color(0xFF94A3B8), baseRect, baseSize, CornerRadius(6f, 6f), style = Stroke(1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)))
                    val inW = widthScreen * 0.72f
                    val inD = depthScreen * 0.72f
                    drawRect(Color(0xFFCBD5E1), Offset(-inW * 0.5f, -inD * 0.5f), Size(inW, inD), style = Stroke(1f))
                    val fringeCount = 6
                    val stepY = depthScreen / (fringeCount + 1)
                    for (i in 1..fringeCount) {
                        val fy = -halfD + stepY * i
                        drawLine(Color(0xFF94A3B8), Offset(-halfW, fy), Offset(-halfW - 3f, fy), strokeWidth = 1f)
                        drawLine(Color(0xFF94A3B8), Offset(halfW, fy), Offset(halfW + 3f, fy), strokeWidth = 1f)
                    }
                }

                // --- Bedroom ---
                ItemType.BED_DOUBLE -> {
                    drawRoundRect(FabricColor, baseRect, baseSize, CornerRadius(6f, 6f))
                    drawRoundRect(strokeColor, baseRect, baseSize, CornerRadius(6f, 6f), style = Stroke(strokeW))

                    // Headboard at REAR (+halfD, against wall)
                    val headboardH = depthScreen * 0.12f
                    val headboardY = halfD - headboardH
                    drawRect(Color(0xFF94A3B8), Offset(-halfW, headboardY), Size(widthScreen, headboardH))
                    drawRect(strokeColor, Offset(-halfW, headboardY), Size(widthScreen, headboardH), style = Stroke(1.2f))

                    // Two contoured pillows in front of headboard
                    val pillowW = widthScreen * 0.38f
                    val pillowH = depthScreen * 0.22f
                    val pillowY = headboardY - pillowH - 6f
                    drawRoundRect(Color(0xFFF8FAFC), Offset(-halfW + 8f, pillowY), Size(pillowW, pillowH), CornerRadius(4f, 4f))
                    drawRoundRect(detailColor, Offset(-halfW + 8f, pillowY), Size(pillowW, pillowH), CornerRadius(4f, 4f), style = Stroke(1f))
                    drawRoundRect(Color(0xFFF8FAFC), Offset(halfW - pillowW - 8f, pillowY), Size(pillowW, pillowH), CornerRadius(4f, 4f))
                    drawRoundRect(detailColor, Offset(halfW - pillowW - 8f, pillowY), Size(pillowW, pillowH), CornerRadius(4f, 4f), style = Stroke(1f))

                    // Duvet fold line towards foot of bed (-halfD)
                    val foldY = -halfD + depthScreen * 0.48f
                    drawLine(detailColor, Offset(-halfW + 4f, foldY), Offset(halfW - 4f, foldY), strokeWidth = 1.2f)
                }

                ItemType.BED_SINGLE -> {
                    drawRoundRect(FabricColor, baseRect, baseSize, CornerRadius(6f, 6f))
                    drawRoundRect(strokeColor, baseRect, baseSize, CornerRadius(6f, 6f), style = Stroke(strokeW))

                    // Headboard at REAR (+halfD)
                    val headboardH = depthScreen * 0.12f
                    val headboardY = halfD - headboardH
                    drawRect(Color(0xFF94A3B8), Offset(-halfW, headboardY), Size(widthScreen, headboardH))
                    drawRect(strokeColor, Offset(-halfW, headboardY), Size(widthScreen, headboardH), style = Stroke(1.2f))

                    // One centered pillow
                    val pW = widthScreen * 0.68f
                    val pH = depthScreen * 0.22f
                    val pillowY = headboardY - pH - 6f
                    drawRoundRect(Color(0xFFF8FAFC), Offset(-pW * 0.5f, pillowY), Size(pW, pH), CornerRadius(4f, 4f))
                    drawRoundRect(detailColor, Offset(-pW * 0.5f, pillowY), Size(pW, pH), CornerRadius(4f, 4f), style = Stroke(1f))

                    // Duvet fold line towards foot of bed (-halfD)
                    val foldY = -halfD + depthScreen * 0.48f
                    drawLine(detailColor, Offset(-halfW + 4f, foldY), Offset(halfW - 4f, foldY), strokeWidth = 1.2f)
                }

                ItemType.BED_BUNK -> {
                    drawRoundRect(FabricColor, baseRect, baseSize, CornerRadius(4f, 4f))
                    drawRoundRect(strokeColor, baseRect, baseSize, CornerRadius(4f, 4f), style = Stroke(strokeW))

                    // Headboard at REAR (+halfD) and footboard at FRONT (-halfD)
                    drawRect(Color(0xFF94A3B8), Offset(-halfW, halfD - 8f), Size(widthScreen, 8f))
                    drawRect(Color(0xFF94A3B8), Offset(-halfW, -halfD), Size(widthScreen, 8f))

                    // Ladder rungs on right edge
                    val ladderW = 10f
                    drawRect(Color(0xFF475569), Offset(halfW - ladderW, -halfD * 0.4f), Size(ladderW, depthScreen * 0.8f))
                    for (r in 0..4) {
                        val ry = -halfD * 0.4f + (depthScreen * 0.8f / 4f) * r
                        drawLine(Color(0xFFF8FAFC), Offset(halfW - ladderW, ry), Offset(halfW, ry), strokeWidth = 1.5f)
                    }

                    // Pillow at REAR (+halfD)
                    val pillowH = depthScreen * 0.22f
                    val pillowY = halfD - 8f - pillowH - 4f
                    drawRoundRect(Color(0xFFF8FAFC), Offset(-halfW + 6f, pillowY), Size(widthScreen - ladderW - 12f, pillowH), CornerRadius(4f, 4f))
                    drawRoundRect(detailColor, Offset(-halfW + 6f, pillowY), Size(widthScreen - ladderW - 12f, pillowH), CornerRadius(4f, 4f), style = Stroke(1f))
                }

                ItemType.WARDROBE -> {
                    drawRect(WoodColor, baseRect, baseSize)
                    drawRect(strokeColor, baseRect, baseSize, style = Stroke(strokeW))
                    // Solid back panel at +halfD (wall)
                    drawLine(strokeColor, Offset(-halfW, halfD - 2f), Offset(halfW, halfD - 2f), strokeWidth = 2.5f)
                    // Front sliding door track at -halfD (room)
                    drawLine(detailColor, Offset(-halfW, -halfD + 4f), Offset(halfW, -halfD + 4f), strokeWidth = 1.2f)
                    val dW = widthScreen / 3f
                    drawLine(strokeColor, Offset(-halfW + dW, -halfD), Offset(-halfW + dW, halfD - 2f), strokeWidth = 1.2f)
                    drawLine(strokeColor, Offset(-halfW + dW * 2f, -halfD), Offset(-halfW + dW * 2f, halfD - 2f), strokeWidth = 1.2f)
                    // Hanger rail line with hangers
                    drawLine(detailColor, Offset(-halfW + 8f, 0f), Offset(halfW - 8f, 0f), strokeWidth = 1.2f)
                    val hCount = 6
                    val hStep = (widthScreen - 20f) / hCount
                    for (i in 0 until hCount) {
                        val hx = -halfW + 10f + hStep * i
                        drawLine(fineColor, Offset(hx - 3f, -4f), Offset(hx + 3f, 4f), strokeWidth = 1f)
                    }
                    // Door handles facing room (-halfD)
                    drawRoundRect(detailColor, Offset(-halfW + dW - 6f, -halfD + 2f), Size(4f, 8f), CornerRadius(1.5f, 1.5f))
                    drawRoundRect(detailColor, Offset(-halfW + dW * 2f + 2f, -halfD + 2f), Size(4f, 8f), CornerRadius(1.5f, 1.5f))
                }

                ItemType.WARDROBE_CORNER -> {
                    val cutW = widthScreen * 0.45f
                    val cutH = depthScreen * 0.45f
                    val cutLeft = halfW - cutW
                    val cutBottom = -halfD + cutH

                    // True L-shaped geometric outline with zero white background
                    val wardrobeLPath = Path().apply {
                        moveTo(-halfW, -halfD)
                        lineTo(cutLeft, -halfD)
                        lineTo(cutLeft, cutBottom)
                        lineTo(halfW, cutBottom)
                        lineTo(halfW, halfD)
                        lineTo(-halfW, halfD)
                        close()
                    }
                    drawPath(wardrobeLPath, WoodColor, style = Fill)
                    drawPath(wardrobeLPath, strokeColor, style = Stroke(strokeW))

                    // Solid back walls at wall edges (+halfD and -halfW)
                    drawLine(strokeColor, Offset(-halfW, halfD - 2f), Offset(halfW, halfD - 2f), strokeWidth = 2.2f)
                    drawLine(strokeColor, Offset(-halfW + 2f, -halfD), Offset(-halfW + 2f, halfD), strokeWidth = 2.2f)

                    // Hanger rail lines
                    val rearMidY = halfD - (depthScreen - cutH) * 0.5f
                    drawLine(detailColor, Offset(cutLeft + 6f, rearMidY), Offset(halfW - 6f, rearMidY), strokeWidth = 1.2f)
                    val leftMidX = -halfW + (widthScreen - cutW) * 0.5f
                    drawLine(detailColor, Offset(leftMidX, -halfD + 6f), Offset(leftMidX, cutBottom - 6f), strokeWidth = 1.2f)

                    // Door front handles
                    drawRoundRect(detailColor, Offset(cutLeft - 4f, cutBottom * 0.5f - 4f), Size(3f, 8f), CornerRadius(1f, 1f))
                    drawRoundRect(detailColor, Offset(cutLeft * 0.5f - 4f, cutBottom - 4f), Size(8f, 3f), CornerRadius(1f, 1f))
                }

                ItemType.NIGHTSTAND -> {
                    drawRoundRect(WoodColor, baseRect, baseSize, CornerRadius(4f, 4f))
                    drawRoundRect(strokeColor, baseRect, baseSize, CornerRadius(4f, 4f), style = Stroke(strokeW))
                    // Back panel against wall (+halfD) with lamp
                    drawCircle(Color(0xFFFEF08A), 4.5f, Offset(0f, halfD * 0.45f))
                    drawCircle(Color(0xFFF59E0B), 2f, Offset(0f, halfD * 0.45f))
                    // Drawer line and front handle facing room (-halfD)
                    drawLine(detailColor, Offset(-halfW + 4f, 0f), Offset(halfW - 4f, 0f), strokeWidth = 1f)
                    drawRoundRect(detailColor, Offset(-halfW * 0.25f, -halfD * 0.5f - 2f), Size(halfW * 0.5f, 4f), CornerRadius(2f, 2f))
                }

                ItemType.DRESSER -> {
                    drawRoundRect(WoodColor, baseRect, baseSize, CornerRadius(3f, 3f))
                    drawRoundRect(strokeColor, baseRect, baseSize, CornerRadius(3f, 3f), style = Stroke(strokeW))
                    // Solid back panel against wall (+halfD)
                    drawLine(strokeColor, Offset(-halfW, halfD - 2f), Offset(halfW, halfD - 2f), strokeWidth = 2f)
                    val dH = depthScreen / 3f
                    for (i in 1..2) {
                        val ly = -halfD + dH * i
                        drawLine(detailColor, Offset(-halfW, ly), Offset(halfW, ly), strokeWidth = 1f)
                        drawRoundRect(detailColor, Offset(-halfW * 0.45f, ly - dH * 0.5f - 2f), Size(widthScreen * 0.25f, 4f), CornerRadius(2f, 2f))
                        drawRoundRect(detailColor, Offset(halfW * 0.2f, ly - dH * 0.5f - 2f), Size(widthScreen * 0.25f, 4f), CornerRadius(2f, 2f))
                    }
                    drawRoundRect(detailColor, Offset(-halfW * 0.45f, halfD - dH * 0.5f - 2f), Size(widthScreen * 0.25f, 4f), CornerRadius(2f, 2f))
                    drawRoundRect(detailColor, Offset(halfW * 0.2f, halfD - dH * 0.5f - 2f), Size(widthScreen * 0.25f, 4f), CornerRadius(2f, 2f))
                }

                // --- Kitchen & Dining ---
                ItemType.KITCHEN_COUNTER -> {
                    drawRect(Color(0xFFF8FAFC), baseRect, baseSize)
                    drawRect(strokeColor, baseRect, baseSize, style = Stroke(strokeW))
                    // Backsplash line against wall (+halfD)
                    drawLine(strokeColor, Offset(-halfW, halfD - 2f), Offset(halfW, halfD - 2f), strokeWidth = 2.5f)
                    // Double sink in left half
                    val sinkW = widthScreen * 0.38f
                    val sinkD = depthScreen * 0.65f
                    val sinkRect = Offset(-halfW + 10f, -sinkD * 0.5f + 2f)
                    drawRoundRect(Color(0xFFE0F2FE), sinkRect, Size(sinkW, sinkD), CornerRadius(4f, 4f))
                    drawRoundRect(detailColor, sinkRect, Size(sinkW, sinkD), CornerRadius(4f, 4f), style = Stroke(1f))
                    // Center divider
                    drawLine(detailColor, Offset(-halfW + 10f + sinkW * 0.5f, sinkRect.y), Offset(-halfW + 10f + sinkW * 0.5f, sinkRect.y + sinkD), strokeWidth = 1f)
                    // Drains
                    drawCircle(detailColor, 3f, Offset(-halfW + 10f + sinkW * 0.25f, 0f))
                    drawCircle(detailColor, 3f, Offset(-halfW + 10f + sinkW * 0.75f, 0f))
                    // Faucet mixer at REAR (+halfD, against wall)
                    drawCircle(strokeColor, 4.5f, Offset(-halfW + 10f + sinkW * 0.5f, halfD - 8f))
                    drawCircle(Color(0xFF0F172A), 2.5f, Offset(-halfW + 10f + sinkW * 0.5f, halfD - 8f))
                }

                ItemType.KITCHEN_ISLAND -> {
                    drawRoundRect(WoodColor, baseRect, baseSize, CornerRadius(4f, 4f))
                    drawRoundRect(strokeColor, baseRect, baseSize, CornerRadius(4f, 4f), style = Stroke(strokeW))
                    // Prep sink
                    val prepSinkW = widthScreen * 0.22f
                    drawRoundRect(Color(0xFFE0F2FE), Offset(-prepSinkW * 0.5f, -halfD * 0.55f), Size(prepSinkW, depthScreen * 0.36f), CornerRadius(3f, 3f))
                    drawRoundRect(detailColor, Offset(-prepSinkW * 0.5f, -halfD * 0.55f), Size(prepSinkW, depthScreen * 0.36f), CornerRadius(3f, 3f), style = Stroke(1f))
                    drawCircle(strokeColor, 3f, Offset(0f, -halfD * 0.55f - 3f))
                    // Overhang dashed line
                    drawLine(fineColor, Offset(-halfW + 6f, halfD - 10f), Offset(halfW - 6f, halfD - 10f), strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f))
                }

                ItemType.DINING_TABLE_4 -> {
                    drawRoundRect(WoodColor, baseRect, baseSize, CornerRadius(4f, 4f))
                    drawRoundRect(strokeColor, baseRect, baseSize, CornerRadius(4f, 4f), style = Stroke(strokeW))
                    val chairW = (widthScreen - 16f) / 2f * 0.75f
                    val chairD = 10f
                    for (i in 0 until 2) {
                        val cx = -halfW + 8f + ((widthScreen - 16f) / 2f) * (i + 0.5f)
                        drawRoundRect(FabricColor, Offset(cx - chairW * 0.5f, -halfD - chairD + 2f), Size(chairW, chairD), CornerRadius(3f, 3f))
                        drawRoundRect(strokeColor, Offset(cx - chairW * 0.5f, -halfD - chairD + 2f), Size(chairW, chairD), CornerRadius(3f, 3f), style = Stroke(1f))
                        drawRoundRect(FabricColor, Offset(cx - chairW * 0.5f, halfD - 2f), Size(chairW, chairD), CornerRadius(3f, 3f))
                        drawRoundRect(strokeColor, Offset(cx - chairW * 0.5f, halfD - 2f), Size(chairW, chairD), CornerRadius(3f, 3f), style = Stroke(1f))
                    }
                }

                ItemType.DINING_TABLE_6 -> {
                    drawRoundRect(WoodColor, baseRect, baseSize, CornerRadius(4f, 4f))
                    drawRoundRect(strokeColor, baseRect, baseSize, CornerRadius(4f, 4f), style = Stroke(strokeW))
                    val chairW = (widthScreen - 20f) / 3f * 0.75f
                    val chairD = 10f
                    for (i in 0 until 3) {
                        val cx = -halfW + 10f + ((widthScreen - 20f) / 3f) * (i + 0.5f)
                        drawRoundRect(FabricColor, Offset(cx - chairW * 0.5f, -halfD - chairD + 2f), Size(chairW, chairD), CornerRadius(3f, 3f))
                        drawRoundRect(strokeColor, Offset(cx - chairW * 0.5f, -halfD - chairD + 2f), Size(chairW, chairD), CornerRadius(3f, 3f), style = Stroke(1f))
                        drawRoundRect(FabricColor, Offset(cx - chairW * 0.5f, halfD - 2f), Size(chairW, chairD), CornerRadius(3f, 3f))
                        drawRoundRect(strokeColor, Offset(cx - chairW * 0.5f, halfD - 2f), Size(chairW, chairD), CornerRadius(3f, 3f), style = Stroke(1f))
                    }
                }

                ItemType.DINING_TABLE_ROUND -> {
                    val r = widthScreen * 0.48f
                    drawCircle(WoodColor, r, Offset.Zero)
                    drawCircle(strokeColor, r, Offset.Zero, style = Stroke(strokeW))
                    val chairR = 10f
                    val chairAngles = listOf(0.0, PI / 2.0, PI, 3.0 * PI / 2.0)
                    for (ang in chairAngles) {
                        val cx = (cos(ang) * (r + chairR - 4f)).toFloat()
                        val cy = (sin(ang) * (r + chairR - 4f)).toFloat()
                        drawCircle(FabricColor, chairR, Offset(cx, cy))
                        drawCircle(strokeColor, chairR, Offset(cx, cy), style = Stroke(1f))
                    }
                }

                ItemType.REFRIGERATOR, ItemType.REFRIGERATOR_SIDE_BY_SIDE -> {
                    drawRoundRect(Color(0xFFF8FAFC), baseRect, baseSize, CornerRadius(4f, 4f))
                    drawRoundRect(strokeColor, baseRect, baseSize, CornerRadius(4f, 4f), style = Stroke(strokeW))
                    // Back cooling fins against wall (+halfD)
                    drawLine(fineColor, Offset(-halfW + 6f, halfD - 3f), Offset(halfW - 6f, halfD - 3f), strokeWidth = 2f)
                    if (item.itemType == ItemType.REFRIGERATOR_SIDE_BY_SIDE) {
                        drawLine(strokeColor, Offset(0f, -halfD), Offset(0f, halfD - 3f), strokeWidth = 1.5f)
                        // Front door handles facing room (-halfD)
                        drawRoundRect(Color(0xFFCBD5E1), Offset(-halfW * 0.65f, -halfD + 8f), Size(12f, 16f), CornerRadius(2f, 2f))
                        drawLine(strokeColor, Offset(-4f, -halfD + 4f), Offset(-4f, -halfD + depthScreen * 0.6f), strokeWidth = 2.5f)
                        drawLine(strokeColor, Offset(4f, -halfD + 4f), Offset(4f, -halfD + depthScreen * 0.6f), strokeWidth = 2.5f)
                    } else {
                        // Single door split with front handles facing room (-halfD)
                        val splitY = -halfD + depthScreen * 0.35f
                        drawLine(strokeColor, Offset(-halfW, splitY), Offset(halfW, splitY), strokeWidth = 1.5f)
                        drawLine(strokeColor, Offset(halfW - 12f, -halfD + 4f), Offset(halfW - 12f, splitY - 4f), strokeWidth = 2.5f)
                        drawLine(strokeColor, Offset(halfW - 12f, splitY + 4f), Offset(halfW - 12f, halfD - 8f), strokeWidth = 2.5f)
                    }
                }

                ItemType.COOKING_STOVE -> {
                    drawRoundRect(Color(0xFF0F172A), baseRect, baseSize, CornerRadius(4f, 4f))
                    drawRoundRect(strokeColor, baseRect, baseSize, CornerRadius(4f, 4f), style = Stroke(strokeW))
                    // Back splash guard against wall (+halfD)
                    drawRoundRect(Color(0xFF334155), Offset(-halfW + 4f, halfD - 4f), Size(widthScreen - 8f, 3f), CornerRadius(1.5f, 1.5f))
                    // Front control panel with knobs facing cook (-halfD)
                    drawRoundRect(Color(0xFFCBD5E1), Offset(-halfW + 8f, -halfD + 3f), Size(widthScreen - 16f, 5f), CornerRadius(1.5f, 1.5f))
                    for (k in 0..3) {
                        val kx = -halfW + 16f + ((widthScreen - 32f) / 3f) * k
                        drawCircle(Color(0xFF0F172A), 2f, Offset(kx, -halfD + 5.5f))
                    }
                    val rL = min(widthScreen, depthScreen) * 0.17f
                    val rS = min(widthScreen, depthScreen) * 0.13f
                    val dx = halfW * 0.48f
                    val dy = halfD * 0.38f
                    drawCircle(Color(0xFF475569), rS, Offset(-dx, -dy + 3f), style = Stroke(1.5f))
                    drawCircle(Color(0xFFEF4444), rS * 0.5f, Offset(-dx, -dy + 3f), style = Stroke(1f))
                    drawCircle(Color(0xFF475569), rL, Offset(dx, -dy + 3f), style = Stroke(1.5f))
                    drawCircle(Color(0xFFEF4444), rL * 0.5f, Offset(dx, -dy + 3f), style = Stroke(1f))
                    drawCircle(Color(0xFF475569), rL, Offset(-dx, dy - 2f), style = Stroke(1.5f))
                    drawCircle(Color(0xFFEF4444), rL * 0.5f, Offset(-dx, dy - 2f), style = Stroke(1f))
                    drawCircle(Color(0xFF475569), rS, Offset(dx, dy - 2f), style = Stroke(1.5f))
                    drawCircle(Color(0xFFEF4444), rS * 0.5f, Offset(dx, dy - 2f), style = Stroke(1f))
                }

                // --- Bathroom Fixtures ---
                ItemType.BATHTUB -> {
                    drawRoundRect(SanitaryColor, baseRect, baseSize, CornerRadius(6f, 6f))
                    drawRoundRect(strokeColor, baseRect, baseSize, CornerRadius(6f, 6f), style = Stroke(strokeW))
                    val basinInset = 8f
                    drawRoundRect(Color(0xFFE0F2FE), Offset(-halfW + basinInset, -halfD + basinInset), Size(widthScreen - basinInset * 2f, depthScreen - basinInset * 2f), CornerRadius(14f, 14f))
                    drawRoundRect(detailColor, Offset(-halfW + basinInset, -halfD + basinInset), Size(widthScreen - basinInset * 2f, depthScreen - basinInset * 2f), CornerRadius(14f, 14f), style = Stroke(1f))
                    drawCircle(Color(0xFF0F172A), 3.5f, Offset(-halfW + 24f, 0f))
                    drawCircle(detailColor, 5.5f, Offset(-halfW + 24f, 0f), style = Stroke(1f))
                    drawRect(Color(0xFF0F172A), Offset(halfW - 12f, -3f), Size(6f, 6f))
                }

                ItemType.BATHTUB_FREESTANDING -> {
                    drawOval(SanitaryColor, baseRect, baseSize)
                    drawOval(strokeColor, baseRect, baseSize, style = Stroke(strokeW))
                    val inset = 8f
                    drawOval(Color(0xFFE0F2FE), Offset(-halfW + inset, -halfD + inset), Size(widthScreen - inset * 2f, depthScreen - inset * 2f))
                    drawOval(detailColor, Offset(-halfW + inset, -halfD + inset), Size(widthScreen - inset * 2f, depthScreen - inset * 2f), style = Stroke(1f))
                    drawCircle(Color(0xFF0F172A), 3.5f, Offset.Zero)
                    drawCircle(detailColor, 5.5f, Offset.Zero, style = Stroke(1f))
                }

                ItemType.SHOWER_CABIN -> {
                    drawRoundRect(SanitaryColor, baseRect, baseSize, CornerRadius(6f, 6f))
                    drawRoundRect(strokeColor, baseRect, baseSize, CornerRadius(6f, 6f), style = Stroke(strokeW))
                    // Corner drain trap
                    drawCircle(Color(0xFF94A3B8), 6f, Offset(-halfW + 14f, halfD - 14f), style = Stroke(1.2f))
                    drawCircle(Color(0xFF0F172A), 2.5f, Offset(-halfW + 14f, halfD - 14f))
                    // Glass enclosure panels
                    drawLine(GlassColor, Offset(-halfW + 4f, -halfD + 4f), Offset(halfW - 4f, -halfD + 4f), strokeWidth = 2.5f)
                    drawLine(GlassColor, Offset(halfW - 4f, -halfD + 4f), Offset(halfW - 4f, halfD - 4f), strokeWidth = 2.5f)
                    // Shower mixer & head at rear wall (+halfD)
                    drawCircle(Color(0xFF0F172A), 4f, Offset(0f, halfD - 8f))
                    drawLine(detailColor, Offset(0f, halfD - 8f), Offset(0f, halfD - 16f), strokeWidth = 2f)
                }

                ItemType.TOILET -> {
                    // Tank at REAR (+halfD, against wall) - strictly matches 3D model
                    val tankH = depthScreen * 0.30f
                    val tankY = halfD - tankH
                    drawRoundRect(SanitaryColor, Offset(-halfW, tankY), Size(widthScreen, tankH), CornerRadius(4f, 4f))
                    drawRoundRect(strokeColor, Offset(-halfW, tankY), Size(widthScreen, tankH), CornerRadius(4f, 4f), style = Stroke(strokeW))
                    // Dual-flush button on tank
                    drawRoundRect(detailColor, Offset(-8f, tankY + tankH * 0.35f), Size(16f, 6f), CornerRadius(3f, 3f), style = Stroke(1.2f))
                    drawLine(detailColor, Offset(0f, tankY + tankH * 0.35f), Offset(0f, tankY + tankH * 0.35f + 6f), strokeWidth = 1f)

                    // Toilet bowl extending into FRONT (-halfD, room)
                    val bowlW = widthScreen * 0.85f
                    val bowlH = depthScreen * 0.72f
                    val bowlTop = tankY - bowlH * 0.85f
                    drawOval(SanitaryColor, Offset(-bowlW * 0.5f, bowlTop), Size(bowlW, bowlH))
                    drawOval(strokeColor, Offset(-bowlW * 0.5f, bowlTop), Size(bowlW, bowlH), style = Stroke(strokeW))
                    // Inner bowl water pool
                    drawOval(Color(0xFFE0F2FE), Offset(-bowlW * 0.32f, bowlTop + bowlH * 0.22f), Size(bowlW * 0.64f, bowlH * 0.55f))
                    drawOval(detailColor, Offset(-bowlW * 0.32f, bowlTop + bowlH * 0.22f), Size(bowlW * 0.64f, bowlH * 0.55f), style = Stroke(1f))
                }

                ItemType.BIDET -> {
                    drawOval(SanitaryColor, baseRect, baseSize)
                    drawOval(strokeColor, baseRect, baseSize, style = Stroke(strokeW))
                    drawOval(Color(0xFFE0F2FE), Offset(-halfW * 0.7f, -halfD * 0.7f), Size(widthScreen * 0.7f, depthScreen * 0.7f))
                    drawOval(detailColor, Offset(-halfW * 0.7f, -halfD * 0.7f), Size(widthScreen * 0.7f, depthScreen * 0.7f), style = Stroke(1f))
                    // Tap / mixer at REAR (+halfD, against wall)
                    drawCircle(strokeColor, 4.5f, Offset(0f, halfD - 8f))
                    drawCircle(detailColor, 2f, Offset(0f, halfD - 8f))
                }

                ItemType.WASHBASIN -> {
                    drawRoundRect(SanitaryColor, baseRect, baseSize, CornerRadius(4f, 4f))
                    drawRoundRect(strokeColor, baseRect, baseSize, CornerRadius(4f, 4f), style = Stroke(strokeW))
                    val bW = widthScreen * 0.75f
                    val bD = depthScreen * 0.65f
                    drawRoundRect(Color(0xFFE0F2FE), Offset(-bW * 0.5f, -bD * 0.5f - 2f), Size(bW, bD), CornerRadius(10f, 10f))
                    drawRoundRect(detailColor, Offset(-bW * 0.5f, -bD * 0.5f - 2f), Size(bW, bD), CornerRadius(10f, 10f), style = Stroke(1f))
                    // Faucet mixer at REAR (+halfD, against wall)
                    drawCircle(Color(0xFF0F172A), 4.5f, Offset(0f, halfD - 7f))
                    drawCircle(Color(0xFF64748B), 2f, Offset(0f, halfD - 7f))
                    // Drain centered
                    drawCircle(Color(0xFF0F172A), 3.5f, Offset(0f, -2f))
                }

                ItemType.WASHBASIN_DOUBLE -> {
                    drawRoundRect(SanitaryColor, baseRect, baseSize, CornerRadius(4f, 4f))
                    drawRoundRect(strokeColor, baseRect, baseSize, CornerRadius(4f, 4f), style = Stroke(strokeW))
                    val bW = widthScreen * 0.38f
                    val bD = depthScreen * 0.65f
                    val x1 = -halfW + 8f
                    drawRoundRect(Color(0xFFE0F2FE), Offset(x1, -bD * 0.5f - 2f), Size(bW, bD), CornerRadius(10f, 10f))
                    drawRoundRect(detailColor, Offset(x1, -bD * 0.5f - 2f), Size(bW, bD), CornerRadius(10f, 10f), style = Stroke(1f))
                    // Left faucet mixer at REAR (+halfD, wall)
                    drawCircle(Color(0xFF0F172A), 4.5f, Offset(x1 + bW * 0.5f, halfD - 7f))
                    drawCircle(Color(0xFF0F172A), 3f, Offset(x1 + bW * 0.5f, -2f))
                    val x2 = halfW - bW - 8f
                    drawRoundRect(Color(0xFFE0F2FE), Offset(x2, -bD * 0.5f - 2f), Size(bW, bD), CornerRadius(10f, 10f))
                    drawRoundRect(detailColor, Offset(x2, -bD * 0.5f - 2f), Size(bW, bD), CornerRadius(10f, 10f), style = Stroke(1f))
                    // Right faucet mixer at REAR (+halfD, wall)
                    drawCircle(Color(0xFF0F172A), 4.5f, Offset(x2 + bW * 0.5f, halfD - 7f))
                    drawCircle(Color(0xFF0F172A), 3f, Offset(x2 + bW * 0.5f, -2f))
                }

                ItemType.WASHING_MACHINE -> {
                    drawRoundRect(SanitaryColor, baseRect, baseSize, CornerRadius(4f, 4f))
                    drawRoundRect(strokeColor, baseRect, baseSize, CornerRadius(4f, 4f), style = Stroke(strokeW))
                    // Rear hose connection strip (+halfD)
                    drawLine(detailColor, Offset(-halfW + 4f, halfD - 3f), Offset(halfW - 4f, halfD - 3f), strokeWidth = 2f)
                    // Front control panel (-halfD)
                    val cpH = depthScreen * 0.22f
                    drawRect(Color(0xFFF1F5F9), Offset(-halfW, -halfD), Size(widthScreen, cpH))
                    drawLine(detailColor, Offset(-halfW, -halfD + cpH), Offset(halfW, -halfD + cpH), strokeWidth = 1f)
                    drawRect(detailColor, Offset(-halfW + 6f, -halfD + 4f), Size(widthScreen * 0.28f, cpH - 8f), style = Stroke(1f))
                    drawCircle(strokeColor, 3.5f, Offset(halfW - 12f, -halfD + cpH * 0.5f))
                    val drumR = min(widthScreen, depthScreen) * 0.32f
                    val drumCenter = Offset(0f, cpH * 0.4f)
                    drawCircle(Color(0xFFE2E8F0), drumR, drumCenter)
                    drawCircle(strokeColor, drumR, drumCenter, style = Stroke(1.5f))
                    drawCircle(GlassColor, drumR * 0.65f, drumCenter, style = Stroke(1f))
                }

                // --- Office ---
                ItemType.DESK_OFFICE -> {
                    drawRoundRect(WoodColor, baseRect, baseSize, CornerRadius(4f, 4f))
                    drawRoundRect(strokeColor, baseRect, baseSize, CornerRadius(4f, 4f), style = Stroke(strokeW))
                    // Back modesty panel along wall (+halfD)
                    drawLine(strokeColor, Offset(-halfW, halfD - 2f), Offset(halfW, halfD - 2f), strokeWidth = 2.5f)
                    // Cable grommet at REAR (+halfD)
                    drawCircle(detailColor, 4f, Offset(-halfW + 16f, halfD - 10f), style = Stroke(1f))
                    drawCircle(strokeColor, 1.5f, Offset(-halfW + 16f, halfD - 10f))
                    // Monitor stand base at rear
                    drawRoundRect(Color(0xFF475569), Offset(-16f, halfD * 0.35f), Size(32f, 6f), CornerRadius(1.5f, 1.5f))
                    // Drawer pedestal on right
                    val pedW = widthScreen * 0.28f
                    drawLine(strokeColor, Offset(halfW - pedW, -halfD), Offset(halfW - pedW, halfD - 2f), strokeWidth = 1.2f)
                    // Drawer pull handles facing user (-halfD)
                    drawLine(detailColor, Offset(halfW - pedW * 0.7f, -halfD + 8f), Offset(halfW - pedW * 0.3f, -halfD + 8f), strokeWidth = 2f)
                    // Ergonomic desk pad for user keyboard/laptop (-halfD)
                    drawRoundRect(Color(0xFFE2E8F0), Offset(-halfW + 12f, -halfD + 12f), Size(widthScreen - pedW - 24f, depthScreen * 0.45f), CornerRadius(2f, 2f))
                    drawRoundRect(detailColor, Offset(-halfW + 12f, -halfD + 12f), Size(widthScreen - pedW - 24f, depthScreen * 0.45f), CornerRadius(2f, 2f), style = Stroke(1f))
                }

                ItemType.DESK_CORNER -> {
                    val cutW = widthScreen * 0.45f
                    val cutH = depthScreen * 0.45f
                    val cutLeft = halfW - cutW
                    val cutBottom = -halfD + cutH

                    val deskPath = Path().apply {
                        moveTo(-halfW, -halfD)
                        lineTo(cutLeft, -halfD)
                        lineTo(cutLeft, cutBottom)
                        lineTo(halfW, cutBottom)
                        lineTo(halfW, halfD)
                        lineTo(-halfW, halfD)
                        close()
                    }
                    drawPath(deskPath, WoodColor, style = Fill)
                    drawPath(deskPath, strokeColor, style = Stroke(strokeW))

                    // Back corner cable grommet (+halfD)
                    drawCircle(detailColor, 4f, Offset(-halfW + 18f, halfD - 18f), style = Stroke(1f))
                    // Drawer pedestal indication on right wing
                    drawRoundRect(Color(0xFFE2E8F0), Offset(halfW - cutW * 0.7f, cutBottom + 6f), Size(cutW * 0.6f, depthScreen - cutH - 12f), CornerRadius(2f, 2f))
                    drawRoundRect(detailColor, Offset(halfW - cutW * 0.7f, cutBottom + 6f), Size(cutW * 0.6f, depthScreen - cutH - 12f), CornerRadius(2f, 2f), style = Stroke(1f))
                }

                ItemType.OFFICE_CHAIR -> {
                    val radius = min(widthScreen, depthScreen) * 0.38f
                    // 5 caster spokes radiating from center
                    for (i in 0 until 5) {
                        val ang = (i * 72.0 * PI / 180.0).toFloat()
                        val spX = cos(ang) * (halfW * 0.88f)
                        val spY = sin(ang) * (halfD * 0.88f)
                        drawLine(Color(0xFF64748B), Offset.Zero, Offset(spX, spY), strokeWidth = 1.5f)
                        drawCircle(Color(0xFF334155), 2.8f, Offset(spX, spY))
                    }
                    drawCircle(Color(0xFF475569), 4f, Offset.Zero)

                    // Contoured round seat cushion
                    drawCircle(FabricColor, radius, Offset(0f, 2f))
                    drawCircle(strokeColor, radius, Offset(0f, 2f), style = Stroke(strokeW))

                    // Ergonomic backrest post
                    drawLine(Color(0xFF475569), Offset(0f, -radius * 0.5f), Offset(0f, -halfD * 0.82f), strokeWidth = 2.5f)

                    // Curved backrest contour at TOP (-halfD)
                    val backW = widthScreen * 0.65f
                    val backH = depthScreen * 0.16f
                    val backY = -halfD + depthScreen * 0.06f
                    drawRoundRect(Color(0xFF0F172A), Offset(-backW * 0.5f, backY), Size(backW, backH), CornerRadius(4f, 4f))
                    drawRoundRect(strokeColor, Offset(-backW * 0.5f, backY), Size(backW, backH), CornerRadius(4f, 4f), style = Stroke(strokeW))
                    drawRoundRect(Color(0xFF334155), Offset(-backW * 0.44f, backY + 2f), Size(backW * 0.88f, backH - 4f), CornerRadius(3f, 3f))

                    // Left and right armrests
                    val armW = widthScreen * 0.08f
                    val armH = depthScreen * 0.28f
                    drawRoundRect(Color(0xFF334155), Offset(-halfW * 0.88f, -armH * 0.5f + 2f), Size(armW, armH), CornerRadius(2.5f, 2.5f))
                    drawRoundRect(strokeColor, Offset(-halfW * 0.88f, -armH * 0.5f + 2f), Size(armW, armH), CornerRadius(2.5f, 2.5f), style = Stroke(1f))
                    drawRoundRect(Color(0xFF334155), Offset(halfW * 0.88f - armW, -armH * 0.5f + 2f), Size(armW, armH), CornerRadius(2.5f, 2.5f))
                    drawRoundRect(strokeColor, Offset(halfW * 0.88f - armW, -armH * 0.5f + 2f), Size(armW, armH), CornerRadius(2.5f, 2.5f), style = Stroke(1f))
                }

                ItemType.FILING_CABINET -> {
                    drawRoundRect(WoodColor, baseRect, baseSize, CornerRadius(3f, 3f))
                    drawRoundRect(strokeColor, baseRect, baseSize, CornerRadius(3f, 3f), style = Stroke(strokeW))
                    val stepH = depthScreen / 3f
                    for (i in 0 until 3) {
                        val py = -halfD + stepH * (i + 0.5f)
                        drawLine(detailColor, Offset(-halfW, -halfD + stepH * i), Offset(halfW, -halfD + stepH * i), strokeWidth = 1f)
                        drawRoundRect(detailColor, Offset(-widthScreen * 0.25f, py - 2f), Size(widthScreen * 0.5f, 4f), CornerRadius(2f, 2f))
                    }
                }

                // --- Professional IT, Datacenter & Office Equipment ---
                ItemType.SERVER_RACK_42U, ItemType.SERVER_CABINET_DOUBLE -> {
                    // 19" Rack enclosure with perforated door and cooling vents
                    drawRoundRect(Color(0xFF0F172A), baseRect, baseSize, CornerRadius(2f, 2f))
                    drawRoundRect(strokeColor, baseRect, baseSize, CornerRadius(2f, 2f), style = Stroke(strokeW))
                    // Front door perforated grill indication (-halfD)
                    drawRect(Color(0xFF334155), Offset(-halfW + 4f, -halfD + 2f), Size(widthScreen - 8f, 5f))
                    // 19" mounting rails (pair of vertical lines on each side)
                    val railInset = widthScreen * 0.16f
                    drawLine(Color(0xFF64748B), Offset(-halfW + railInset, -halfD + 8f), Offset(-halfW + railInset, halfD - 8f), strokeWidth = 1.5f)
                    drawLine(Color(0xFF64748B), Offset(halfW - railInset, -halfD + 8f), Offset(halfW - railInset, halfD - 8f), strokeWidth = 1.5f)
                    // Fan ventilation slots at rear (+halfD)
                    drawCircle(Color(0xFF475569), 4f * z, Offset(-halfW * 0.35f, halfD - 8f), style = Stroke(1f))
                    drawCircle(Color(0xFF475569), 4f * z, Offset(halfW * 0.35f, halfD - 8f), style = Stroke(1f))
                    // Server status LED (Green & Blue)
                    drawCircle(Color(0xFF22C55E), 1.8f * z, Offset(-halfW + 7f, -halfD + 4.5f))
                    drawCircle(Color(0xFF3B82F6), 1.8f * z, Offset(-halfW + 12f, -halfD + 4.5f))
                }

                ItemType.PC_WORKSTATION -> {
                    // Desk base
                    drawRoundRect(WoodColor, baseRect, baseSize, CornerRadius(4f, 4f))
                    drawRoundRect(strokeColor, baseRect, baseSize, CornerRadius(4f, 4f), style = Stroke(strokeW))
                    // Dual 27" monitors facing user (-halfD)
                    val monW = widthScreen * 0.40f
                    val monD = 5f * z
                    // Left monitor slightly angled
                    drawRoundRect(Color(0xFF1E293B), Offset(-halfW + 8f, halfD * 0.2f), Size(monW, monD), CornerRadius(1.5f, 1.5f))
                    // Right monitor
                    drawRoundRect(Color(0xFF1E293B), Offset(halfW - monW - 8f, halfD * 0.2f), Size(monW, monD), CornerRadius(1.5f, 1.5f))
                    // Keyboard & mouse pad area
                    drawRoundRect(Color(0xFFCBD5E1), Offset(-halfW * 0.45f, -halfD + 8f), Size(widthScreen * 0.5f, depthScreen * 0.38f), CornerRadius(2f, 2f))
                    drawRoundRect(detailColor, Offset(-halfW * 0.45f, -halfD + 8f), Size(widthScreen * 0.5f, depthScreen * 0.38f), CornerRadius(2f, 2f), style = Stroke(1f))
                    // IT Tower under desk
                    drawRect(Color(0xFF0F172A), Offset(halfW - 14f * z, -halfD + 6f), Size(10f * z, depthScreen * 0.6f))
                }

                ItemType.DISPATCH_CONSOLE -> {
                    // Ergonomic dispatch console
                    drawRoundRect(Color(0xFF1E293B), baseRect, baseSize, CornerRadius(4f, 4f))
                    drawRoundRect(strokeColor, baseRect, baseSize, CornerRadius(4f, 4f), style = Stroke(strokeW))
                    // 3 curved dispatch screens on riser
                    val screenW = widthScreen * 0.28f
                    val screenD = 4.5f * z
                    drawRoundRect(Color(0xFF0F172A), Offset(-screenW * 1.5f + 4f, halfD * 0.3f), Size(screenW, screenD), CornerRadius(1.5f, 1.5f))
                    drawRoundRect(Color(0xFF0F172A), Offset(-screenW * 0.5f, halfD * 0.32f), Size(screenW, screenD), CornerRadius(1.5f, 1.5f))
                    drawRoundRect(Color(0xFF0F172A), Offset(screenW * 0.5f - 4f, halfD * 0.3f), Size(screenW, screenD), CornerRadius(1.5f, 1.5f))
                    // Operator work surface with dual control panels
                    drawRoundRect(Color(0xFF334155), Offset(-halfW + 12f, -halfD + 8f), Size(widthScreen - 24f, depthScreen * 0.4f), CornerRadius(2f, 2f))
                }

                ItemType.CONFERENCE_TABLE -> {
                    // Large boardroom table
                    drawRoundRect(Color(0xFFE2E8F0), baseRect, baseSize, CornerRadius(8f, 8f))
                    drawRoundRect(strokeColor, baseRect, baseSize, CornerRadius(8f, 8f), style = Stroke(strokeW))
                    // Center cable trough
                    val troughW = widthScreen * 0.45f
                    val troughD = depthScreen * 0.20f
                    drawRoundRect(Color(0xFFCBD5E1), Offset(-troughW * 0.5f, -troughD * 0.5f), Size(troughW, troughD), CornerRadius(2f, 2f))
                    drawRoundRect(detailColor, Offset(-troughW * 0.5f, -troughD * 0.5f), Size(troughW, troughD), CornerRadius(2f, 2f), style = Stroke(1f))
                    // 6 to 8 perimeter conference chairs
                    val chairR = 12f * z
                    for (i in 0..2) {
                        val cx = -halfW * 0.6f + (halfW * 1.2f / 2f) * i
                        drawCircle(FabricColor, chairR, Offset(cx, -halfD + 4f))
                        drawCircle(strokeColor, chairR, Offset(cx, -halfD + 4f), style = Stroke(1f))
                        drawCircle(FabricColor, chairR, Offset(cx, halfD - 4f))
                        drawCircle(strokeColor, chairR, Offset(cx, halfD - 4f), style = Stroke(1f))
                    }
                }

                ItemType.METAL_RACK_STORAGE -> {
                    // Industrial metal shelving
                    drawRect(Color(0xFFF1F5F9), baseRect, baseSize)
                    drawRect(strokeColor, baseRect, baseSize, style = Stroke(strokeW))
                    // 4 corner angle steel posts
                    val postSize = 6f * z
                    drawRect(Color(0xFF334155), Offset(-halfW, -halfD), Size(postSize, postSize))
                    drawRect(Color(0xFF334155), Offset(halfW - postSize, -halfD), Size(postSize, postSize))
                    drawRect(Color(0xFF334155), Offset(-halfW, halfD - postSize), Size(postSize, postSize))
                    drawRect(Color(0xFF334155), Offset(halfW - postSize, halfD - postSize), Size(postSize, postSize))
                    // Shelf divider lines
                    drawLine(detailColor, Offset(-halfW, -halfD * 0.33f), Offset(halfW, -halfD * 0.33f), strokeWidth = 1f)
                    drawLine(detailColor, Offset(-halfW, halfD * 0.33f), Offset(halfW, halfD * 0.33f), strokeWidth = 1f)
                    // Diagonal brace indication
                    drawLine(fineColor, Offset(-halfW, -halfD), Offset(halfW, halfD), strokeWidth = 0.8f)
                }

                ItemType.RECEPTION_DESK -> {
                    // Angular reception counter
                    drawRoundRect(Color(0xFFF8FAFC), baseRect, baseSize, CornerRadius(4f, 4f))
                    drawRoundRect(strokeColor, baseRect, baseSize, CornerRadius(4f, 4f), style = Stroke(strokeW))
                    // High outer greeting ledge (-halfD)
                    drawRect(Color(0xFF0F172A), Offset(-halfW, -halfD), Size(widthScreen, 8f * z))
                    // Side greeting wing
                    drawRect(Color(0xFF0F172A), Offset(halfW - 8f * z, -halfD), Size(8f * z, depthScreen))
                    // Lower work desktop
                    drawRoundRect(Color(0xFFE2E8F0), Offset(-halfW + 6f, -halfD + 12f * z), Size(widthScreen - 18f * z, depthScreen - 16f * z), CornerRadius(2f, 2f))
                }

                ItemType.UPS_INDUSTRIAL, ItemType.DATACENTER_COOLING -> {
                    // Heavy industrial cabinet
                    drawRoundRect(Color(0xFF1E293B), baseRect, baseSize, CornerRadius(3f, 3f))
                    drawRoundRect(strokeColor, baseRect, baseSize, CornerRadius(3f, 3f), style = Stroke(strokeW))
                    // Front louvers
                    val louverCount = 4
                    for (i in 1..louverCount) {
                        val ly = -halfD + (depthScreen / (louverCount + 1f)) * i
                        drawLine(Color(0xFF475569), Offset(-halfW + 6f, ly), Offset(halfW - 6f, ly), strokeWidth = 1.5f)
                    }
                    // Warning icon / electrical lightning bolt badge
                    drawCircle(Color(0xFFF59E0B), 5f * z, Offset(0f, -halfD + 10f * z))
                }



                else -> {
                    val painter = getPainterForItem(item, painters)
                    if (painter != null) {
                        drawContext.canvas.translate(-halfW, -halfD)
                        with(painter) {
                            draw(Size(widthScreen, depthScreen))
                        }
                        drawContext.canvas.translate(halfW, halfD)
                    } else {
                        // Generic cabinet / fixture
                        drawRoundRect(WoodColor, baseRect, baseSize, CornerRadius(4f, 4f))
                        drawRoundRect(strokeColor, baseRect, baseSize, CornerRadius(4f, 4f), style = Stroke(strokeW))
                    }
                }
            }

            drawContext.canvas.restore()
        }
    }

    /**
     * Renders selection bounding box, corner resize grips, rotation handle, and quick action HUD.
     */
    private fun drawSelectionOverlay(
        drawScope: DrawScope,
        item: PlacedItem,
        viewport: CadViewportState,
        textMeasurer: TextMeasurer
    ) {
        with(drawScope) {
            val centerScreen = viewport.worldToScreen(item.position)
            val angle = item.rotationDegrees

            val widthScreen = item.widthCm * viewport.zoom
            val depthScreen = item.depthCm * viewport.zoom

            drawContext.canvas.save()
            drawContext.canvas.translate(centerScreen.x, centerScreen.y)
            drawContext.canvas.rotate(angle)

            val halfW = widthScreen * 0.5f + 4f
            val halfD = depthScreen * 0.5f + 4f
            val boundRect = Offset(-halfW, -halfD)
            val boundSize = Size(halfW * 2f, halfD * 2f)

            // Outer selection box
            drawRoundRect(
                color = ItemSelectedBorder,
                topLeft = boundRect,
                size = boundSize,
                cornerRadius = CornerRadius(4f, 4f),
                style = Stroke(width = 1.5f)
            )

            // 4 Corner resize handles
            val gripSize = 8f
            val corners = listOf(
                Offset(-halfW, -halfD),
                Offset(halfW, -halfD),
                Offset(halfW, halfD),
                Offset(-halfW, halfD)
            )
            for (c in corners) {
                drawRect(Color.White, Offset(c.x - gripSize * 0.5f, c.y - gripSize * 0.5f), Size(gripSize, gripSize), style = Fill)
                drawRect(ItemSelectedBorder, Offset(c.x - gripSize * 0.5f, c.y - gripSize * 0.5f), Size(gripSize, gripSize), style = Stroke(1.5f))
            }

            // Rotation stem & knob at top
            val stemLen = 24f
            val knobRadius = 7f
            val knobCenter = Offset(0f, -halfD - stemLen)
            drawLine(ItemSelectedBorder, Offset(0f, -halfD), knobCenter, strokeWidth = 1.5f)
            drawCircle(Color.White, knobRadius, knobCenter, style = Fill)
            drawCircle(ItemSelectedBorder, knobRadius, knobCenter, style = Stroke(2f))

            drawContext.canvas.restore()

            // Floating Quick HUD above the object (unrotated, easy to read)
            val hudY = centerScreen.y - (halfD + stemLen + 20f)
            val hudText = "${item.name} (${item.widthCm.toInt()}×${item.depthCm.toInt()}см)"
            val measured = textMeasurer.measure(
                hudText,
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            )

            val hudW = measured.size.width + 16f
            val hudH = measured.size.height + 8f
            val hudTopLeft = Offset(centerScreen.x - hudW * 0.5f, hudY - hudH * 0.5f)

            drawRoundRect(
                color = Color(0xEE0F172A),
                topLeft = hudTopLeft,
                size = Size(hudW, hudH),
                cornerRadius = CornerRadius(6f, 6f),
                style = Fill
            )
            drawText(measured, topLeft = Offset(hudTopLeft.x + 8f, hudTopLeft.y + 4f))
        }
    }
}
