package com.cheezo.ductworks.ui.canvas

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cheezo.ductworks.engine.WallPolygonBuilder
import com.cheezo.ductworks.engine.cad.FloorPatternEngine
import com.cheezo.ductworks.engine.cad.GostDimensionEngine
import com.cheezo.ductworks.engine.cad.InteriorPlanningEngine
import com.cheezo.ductworks.engine.cad.WallHatchEngine
import com.cheezo.ductworks.model.*
import com.cheezo.ductworks.ui.CadEditorState
import com.cheezo.ductworks.ui.CadTool
import com.cheezo.ductworks.ui.CadViewportState
import kotlin.math.*

/**
 * Professional architectural CAD renderer strictly adhering to ГОСТ 2.307-2011, ГОСТ 21.501-2018,
 * and high-end interior planning workflows (Planner 5D / ArchiCAD).
 *
 * Integrates:
 * - Clean engineering white background with metric coordinate grid
 * - Procedural architectural floor materials and patterns (FloorPatternEngine)
 * - Mitered wall polygon meshes with ГОСТ material cross-sections (WallHatchEngine)
 * - Two-level dimension chains with 45° serifs and parallel text (GostDimensionEngine)
 * - Clear room interior dimensions (чистые размеры комнат)
 * - Dynamic furniture-to-wall distance snapping guides
 * - Ergonomic clearance zones (Neufert standard)
 * - ГОСТ 21.501 Room explanation stamps
 * - Smart alignment guides & rubber-band previews
 */
object CadRenderer {

    // Engineering White Theme Palette
    val CanvasBgColor = Color(0xFFFFFFFF)
    val GridMinorColor = Color(0xFFF1F5F9)
    val GridMajorColor = Color(0xFFE2E8F0)
    val AxisColor = Color(0xFF94A3B8)
    val GridTextColor = Color(0xFF94A3B8)

    // Architectural Wall Colors
    val WallExteriorFill = Color(0xFF1E293B)
    val WallInteriorFill = Color(0xFF334155)
    val WallPartitionFill = Color(0xFF475569)
    val WallSelectedFill = Color(0xFF2563EB)
    val WallBorderColor = Color(0xFF0F172A)
    val WallSelectedBorder = Color(0xFF60A5FA)

    // CAD Grip / Vertex Colors
    val GripFillColor = Color(0xFFFFFFFF)
    val GripBorderColor = Color(0xFF1E293B)
    val GripSelectedFill = Color(0xFF2563EB)
    val GripSelectedBorder = Color(0xFFFFFFFF)
    val GripSnapColor = Color(0xFF10B981)

    // Overlays
    val GuideLineColor = Color(0xFF0284C7)
    val PreviewWallColor = Color(0x772563EB)
    val PreviewWallOutline = Color(0xFF2563EB)

    fun drawCadGrid(
        drawScope: DrawScope,
        viewport: CadViewportState,
        editorState: CadEditorState,
        textMeasurer: TextMeasurer
    ) {
        with(drawScope) {
            val width = size.width
            val height = size.height

            drawContext.canvas.save()
            drawContext.canvas.clipRect(0f, 0f, width, height)
            try {
                val rulerX = if (editorState.leftNavWidthPx > 0) {
                    editorState.leftNavWidthPx.toFloat()
                } else {
                    (if (editorState.isLeftDrawerOpen) 378f else 58f) * density
                }

                val topLeftWorld = viewport.screenToWorld(Offset(rulerX, 0f))
                val bottomRightWorld = viewport.screenToWorld(Offset(width, height))
                val zoom = viewport.zoom

                val minorStep = editorState.gridStepCm
                val majorStep = 100f // 1 meter

                val startMajorX = floor(topLeftWorld.x / majorStep) * majorStep
                val endMajorX = ceil(bottomRightWorld.x / majorStep) * majorStep
                val startMajorY = floor(topLeftWorld.y / majorStep) * majorStep
                val endMajorY = ceil(bottomRightWorld.y / majorStep) * majorStep

                // Minor grid lines (e.g. 20 cm) if zoomed in enough
                if (zoom >= 0.25f) {
                    val startMinorX = floor(topLeftWorld.x / minorStep) * minorStep
                    val endMinorX = ceil(bottomRightWorld.x / minorStep) * minorStep
                    val startMinorY = floor(topLeftWorld.y / minorStep) * minorStep
                    val endMinorY = ceil(bottomRightWorld.y / minorStep) * minorStep

                    var curX = startMinorX
                    while (curX <= endMinorX) {
                        val sx = viewport.worldToScreen(Point2D(curX, 0f)).x
                        if (sx >= rulerX) {
                            drawLine(GridMinorColor, Offset(sx, 0f), Offset(sx, height), strokeWidth = 1f)
                        }
                        curX += minorStep
                    }

                    var curY = startMinorY
                    while (curY <= endMinorY) {
                        val sy = viewport.worldToScreen(Point2D(0f, curY)).y
                        if (sy in 0f..height) {
                            drawLine(GridMinorColor, Offset(rulerX, sy), Offset(width, sy), strokeWidth = 1f)
                        }
                        curY += minorStep
                    }
                }

                // Major grid lines (1 meter)
                var curX = startMajorX
                while (curX <= endMajorX) {
                    val sx = viewport.worldToScreen(Point2D(curX, 0f)).x
                    if (sx >= rulerX) {
                        drawLine(GridMajorColor, Offset(sx, 0f), Offset(sx, height), strokeWidth = 1.2f)
                    }
                    curX += majorStep
                }

                var curY = startMajorY
                while (curY <= endMajorY) {
                    val sy = viewport.worldToScreen(Point2D(0f, curY)).y
                    if (sy in 0f..height) {
                        drawLine(GridMajorColor, Offset(rulerX, sy), Offset(width, sy), strokeWidth = 1.2f)
                    }
                    curY += majorStep
                }

                // Origin (0, 0) crosshair
                val originScreen = viewport.worldToScreen(Point2D.Zero)
                if (originScreen.x in rulerX..width) {
                    drawLine(AxisColor, Offset(originScreen.x, 0f), Offset(originScreen.x, height), strokeWidth = 1.5f)
                }
                if (originScreen.y in 0f..height) {
                    drawLine(AxisColor, Offset(rulerX, originScreen.y), Offset(width, originScreen.y), strokeWidth = 1.5f)
                }
            } finally {
                drawContext.canvas.restore()
            }
        }
    }

    /**
     * Renders professional CAD metric grid rulers (vertical and horizontal) along the visible workspace edges.
     * Guaranteed to never be overlapped or clipped by the navigation sidebar.
     * Features:
     * - Fixed vertical ruler baseline along rulerX (right edge of left sidebar)
     * - Fixed horizontal ruler baseline along top edge of canvas
     * - Major ticks (8px) and minor ticks (4px)
     * - Adaptive metric coordinate badges (0м, 1м, 2м...) with high-legibility pills
     * - Dynamic interactive cursor position indicators on both rulers
     */
    fun drawCadRulers(
        drawScope: DrawScope,
        viewport: CadViewportState,
        editorState: CadEditorState,
        textMeasurer: TextMeasurer
    ) {
        with(drawScope) {
            val width = size.width
            val height = size.height

            drawContext.canvas.save()
            drawContext.canvas.clipRect(0f, 0f, width, height)
            try {
                val rulerX = if (editorState.leftNavWidthPx > 0) {
                    editorState.leftNavWidthPx.toFloat()
                } else {
                    (if (editorState.isLeftDrawerOpen) 378f else 58f) * density
                }

                val rightBound = if (editorState.rightSidebarWidthPx > 0 && editorState.isPropertiesSidebarOpen) {
                    (width - editorState.rightSidebarWidthPx.toFloat() - 6f).coerceAtLeast(rulerX + 100f)
                } else {
                    width - 24f
                }

                val zoom = viewport.zoom
                val minorStep = editorState.gridStepCm
                val majorStep = 100f // 1 meter

                val topLeftWorld = viewport.screenToWorld(Offset(rulerX, 0f))
                val bottomRightWorld = viewport.screenToWorld(Offset(rightBound, height))

                val startMajorX = floor(topLeftWorld.x / majorStep) * majorStep
                val endMajorX = ceil(bottomRightWorld.x / majorStep) * majorStep
                val startMajorY = floor(topLeftWorld.y / majorStep) * majorStep
                val endMajorY = ceil(bottomRightWorld.y / majorStep) * majorStep

                // Dynamic LOD: meter label interval based on zoom to prevent label crowding
                val meterPixelSpan = 100f * zoom
                val labelIntervalMeters = when {
                    meterPixelSpan >= 80f -> 1   // Zoomed in (>=80px per meter): every 1m
                    meterPixelSpan >= 40f -> 2   // Moderately zoomed out (40-80px): every 2m
                    meterPixelSpan >= 20f -> 5   // Zoomed out (20-40px): every 5m
                    meterPixelSpan >= 10f -> 10  // Far zoomed out (10-20px): every 10m
                    meterPixelSpan >= 4f  -> 20  // Very far (4-10px): every 20m
                    else -> 50                   // Tiny scale (<4px per meter): every 50m
                }

                val rulerTrackColor = Color(0xFFCBD5E1) // slate-300
                val rulerMajorTickColor = Color(0xFF64748B) // slate-500
                val rulerMinorTickColor = Color(0xFF94A3B8) // slate-400
                val labelTextColor = Color(0xFF334155) // slate-700

                // 1. Draw ruler baseline tracks
                // Vertical ruler line at rulerX
                drawLine(
                    color = rulerTrackColor,
                    start = Offset(rulerX, 0f),
                    end = Offset(rulerX, height),
                    strokeWidth = 1.5f
                )
                // Top horizontal ruler line
                drawLine(
                    color = rulerTrackColor,
                    start = Offset(rulerX, 0f),
                    end = Offset(rightBound, 0f),
                    strokeWidth = 1.5f
                )

                // 2. Vertical Ruler: Minor ticks
                if (minorStep * zoom >= 6f) {
                    val startMinorY = floor(topLeftWorld.y / minorStep) * minorStep
                    val endMinorY = ceil(bottomRightWorld.y / minorStep) * minorStep
                    var curY = startMinorY
                    while (curY <= endMinorY) {
                        val sy = viewport.worldToScreen(Point2D(0f, curY)).y
                        if (sy in 0f..height) {
                            val isMajor = ((curY / 100f).roundToLong() * 100f - curY).absoluteValue < 0.5f
                            if (!isMajor) {
                                drawLine(
                                    color = rulerMinorTickColor,
                                    start = Offset(rulerX, sy),
                                    end = Offset(rulerX + 4f, sy),
                                    strokeWidth = 1f
                                )
                            }
                        }
                        curY += minorStep
                    }
                }

                // 3. Vertical Ruler: Major ticks & coordinate labels (clean text without background box)
                var curY = startMajorY
                while (curY <= endMajorY) {
                    val sy = viewport.worldToScreen(Point2D(0f, curY)).y
                    if (sy in 0f..height) {
                        val isOrigin = curY.absoluteValue < 0.5f
                        val tickColor = if (isOrigin) Color(0xFF2563EB) else rulerMajorTickColor
                        drawLine(
                            color = tickColor,
                            start = Offset(rulerX, sy),
                            end = Offset(rulerX + 8f, sy),
                            strokeWidth = if (isOrigin) 2f else 1.5f
                        )

                        val meters = (curY / 100f).roundToInt()
                        if (meters % labelIntervalMeters == 0 && sy in 14f..(height - 14f)) {
                            val label = "${meters}м"
                            val measured = textMeasurer.measure(
                                label,
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    color = if (isOrigin) Color(0xFF1D4ED8) else labelTextColor,
                                    fontWeight = if (isOrigin) FontWeight.Bold else FontWeight.Medium
                                )
                            )
                            val textH = measured.size.height.toFloat()
                            val badgeX = rulerX + 11f
                            val badgeY = sy - textH / 2f

                            drawText(measured, topLeft = Offset(badgeX, badgeY))
                        }
                    }
                    curY += majorStep
                }

                // 4. Horizontal Ruler: Minor ticks
                if (minorStep * zoom >= 6f) {
                    val startMinorX = floor(topLeftWorld.x / minorStep) * minorStep
                    val endMinorX = ceil(bottomRightWorld.x / minorStep) * minorStep
                    var curX = startMinorX
                    while (curX <= endMinorX) {
                        val sx = viewport.worldToScreen(Point2D(curX, 0f)).x
                        if (sx in (rulerX + 1f)..rightBound) {
                            val isMajor = ((curX / 100f).roundToLong() * 100f - curX).absoluteValue < 0.5f
                            if (!isMajor) {
                                drawLine(
                                    color = rulerMinorTickColor,
                                    start = Offset(sx, 0f),
                                    end = Offset(sx, 4f),
                                    strokeWidth = 1f
                                )
                            }
                        }
                        curX += minorStep
                    }
                }

                // 5. Horizontal Ruler: Major ticks & coordinate labels (clean text without background box)
                var curX = startMajorX
                while (curX <= endMajorX) {
                    val sx = viewport.worldToScreen(Point2D(curX, 0f)).x
                    if (sx in (rulerX + 1f)..rightBound) {
                        val isOrigin = curX.absoluteValue < 0.5f
                        val tickColor = if (isOrigin) Color(0xFF2563EB) else rulerMajorTickColor
                        drawLine(
                            color = tickColor,
                            start = Offset(sx, 0f),
                            end = Offset(sx, 8f),
                            strokeWidth = if (isOrigin) 2f else 1.5f
                        )

                        val meters = (curX / 100f).roundToInt()
                        if (meters % labelIntervalMeters == 0 && sx in (rulerX + 40f)..(rightBound - 12f)) {
                            val label = "${meters}м"
                            val measured = textMeasurer.measure(
                                label,
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    color = if (isOrigin) Color(0xFF1D4ED8) else labelTextColor,
                                    fontWeight = if (isOrigin) FontWeight.Bold else FontWeight.Medium
                                )
                            )
                            val badgeX = sx + 4f
                            val badgeY = 3f

                            drawText(measured, topLeft = Offset(badgeX, badgeY))
                        }
                    }
                    curX += majorStep
                }

                // 6. Interactive Cursor Position Indicators on Rulers
                editorState.currentCursorWorldPoint?.let { curWorld ->
                    if (curWorld.x.isFinite() && curWorld.y.isFinite()) {
                        val curScreen = viewport.worldToScreen(curWorld)

                        // Vertical ruler cursor indicator
                        if (curScreen.y in 0f..height) {
                            val pathY = Path().apply {
                                moveTo(rulerX, curScreen.y - 4f)
                                lineTo(rulerX + 6f, curScreen.y)
                                lineTo(rulerX, curScreen.y + 4f)
                                close()
                            }
                            drawPath(pathY, color = Color(0xFF2563EB))
                        }

                        // Horizontal ruler cursor indicator
                        if (curScreen.x in rulerX..rightBound) {
                            val pathX = Path().apply {
                                moveTo(curScreen.x - 4f, 0f)
                                lineTo(curScreen.x, 6f)
                                lineTo(curScreen.x + 4f, 0f)
                                close()
                            }
                            drawPath(pathX, color = Color(0xFF2563EB))
                        }
                    }
                }
            } finally {
                drawContext.canvas.restore()
            }
        }
    }

    /**
     * Draws procedural architectural room floor materials (parquet, tile, marble, etc.)
     */
    fun drawRoomFloors(
        drawScope: DrawScope,
        editorState: CadEditorState,
        viewport: CadViewportState
    ) {
        with(drawScope) {
            val doc = editorState.document
            val vertices = doc.vertices

            for (room in doc.rooms) {
                val poly = room.getPolygonPoints(vertices)
                if (poly.size < 3) continue

                val isSelected = room.id == editorState.selectedRoomId
                val material = editorState.roomFloorMaterials[room.id]
                    ?: FloorPatternEngine.getMaterialForRoomType(room.roomType)

                if (editorState.showFloorTextures) {
                    FloorPatternEngine.drawRoomFloor(
                        drawScope = this,
                        room = room,
                        polygonWorld = poly,
                        material = material,
                        isSelected = isSelected,
                        viewport = viewport
                    )
                } else {
                    // Plain solid architectural tint fallback
                    val screenPoints = poly.map { viewport.worldToScreen(it) }
                    val path = Path().apply {
                        moveTo(screenPoints[0].x, screenPoints[0].y)
                        for (i in 1 until screenPoints.size) lineTo(screenPoints[i].x, screenPoints[i].y)
                        close()
                    }
                    val roomBaseColor = Color(material.baseColorHex)
                    drawPath(path, if (isSelected) Color(0xFFDBEAFE) else roomBaseColor, style = Fill)
                    drawPath(path, if (isSelected) Color(0xFF2563EB) else Color(0xFFCBD5E1), style = Stroke(width = if (isSelected) 2f else 1f))
                }
            }
        }
    }

    /**
     * Draws ergonomic opening and clearance zones around furniture.
     */
    fun drawErgonomicsClearances(
        drawScope: DrawScope,
        editorState: CadEditorState,
        viewport: CadViewportState
    ) {
        if (!editorState.showErgonomicsClearance) return

        with(drawScope) {
            // Draw for selected item or active items
            val selectedId = editorState.selectedItemId
            val itemsToDraw = if (selectedId != null) {
                listOfNotNull(editorState.document.items[selectedId])
            } else {
                editorState.document.items.values
            }

            for (item in itemsToDraw) {
                val zone = InteriorPlanningEngine.calculateClearanceZone(item)
                if (zone != null) {
                    InteriorPlanningEngine.drawClearanceZone(this, zone, viewport)
                }
            }
        }
    }

    /**
     * Draws architectural walls with true mitered joins and ГОСТ 2.306 hatching.
     */
    fun drawWalls(
        drawScope: DrawScope,
        editorState: CadEditorState,
        viewport: CadViewportState,
        textMeasurer: TextMeasurer
    ) {
        with(drawScope) {
            val doc = editorState.document
            val vertices = doc.vertices
            val walls = doc.walls

            // 1. Compute true architectural wall polygon meshes with miter joins
            val meshes = WallPolygonBuilder.buildWallMeshes(vertices, walls)

            // 2. Draw wall polygons with hatching
            for (mesh in meshes) {
                val wall = walls[mesh.wallId] ?: continue
                val isSelected = wall.id == editorState.selectedWallId
                val isHovered = wall.id == editorState.hoveredWallId

                val hatch = if (editorState.showWallHatching) {
                    WallHatchEngine.getHatchForWallType(wall.wallType)
                } else {
                    WallHatchEngine.WallMaterialHatch.SOLID_ARCHITECTURAL
                }

                // Hover & selection outer glow (longitudinal faces and free caps)
                if (isSelected || isHovered) {
                    val screenPoly = mesh.polygon.map { viewport.worldToScreen(it) }
                    if (screenPoly.size >= 4) {
                        val glowColor = Color(0x443B82F6)
                        val glowW = 6f
                        drawLine(glowColor, screenPoly[0], screenPoly[1], strokeWidth = glowW)
                        drawLine(glowColor, screenPoly[2], screenPoly[3], strokeWidth = glowW)
                        if (mesh.drawEndCap) drawLine(glowColor, screenPoly[1], screenPoly[2], strokeWidth = glowW)
                        if (mesh.drawStartCap) drawLine(glowColor, screenPoly[3], screenPoly[0], strokeWidth = glowW)
                    }
                }

                WallHatchEngine.drawWallHatch(
                    drawScope = this,
                    wall = wall,
                    polygonWorld = mesh.polygon,
                    hatchType = hatch,
                    isSelected = isSelected,
                    isHovered = isHovered,
                    viewport = viewport,
                    drawStartCap = mesh.drawStartCap,
                    drawEndCap = mesh.drawEndCap
                )

                // Midpoint drag handle for selected wall (Planner 5D style)
                if (isSelected) {
                    val line = wall.toLineSegment(vertices)
                    if (line != null) {
                        val midScreen = viewport.worldToScreen(line.midpoint)
                        val handleRadius = 8f
                        drawCircle(Color(0x22000000), handleRadius + 2f, Offset(midScreen.x, midScreen.y + 1f))
                        drawCircle(Color.White, handleRadius, midScreen)
                        drawCircle(WallSelectedFill, handleRadius, midScreen, style = Stroke(width = 2.5f))
                        drawCircle(WallSelectedFill, 3f, midScreen)
                    }
                }
            }

            // 3. Render Two-Level ГОСТ Wall Dimension Chains
            if (editorState.showDimensions) {
                val allChains = GostDimensionEngine.calculateWallDimensionChains(
                    walls = walls.values,
                    vertices = vertices,
                    items = doc.items.values,
                    rooms = doc.rooms,
                    viewport = viewport
                )

                val filteredChains = if (editorState.showOpeningDimensionChains) {
                    allChains
                } else {
                    allChains.filter { it.level == 2 || it.segments.size == 1 }
                }

                GostDimensionEngine.drawWallDimensionChains(
                    drawScope = this,
                    chains = filteredChains,
                    editorState = editorState,
                    viewport = viewport,
                    textMeasurer = textMeasurer
                )
            }

            // 4. Render Clear Interior Room Dimensions (Planner 5D Inset Dimensions)
            if (editorState.showClearRoomDimensions && editorState.showDimensions) {
                val clearDims = GostDimensionEngine.calculateRoomClearDimensions(
                    rooms = doc.rooms,
                    vertices = vertices,
                    walls = walls,
                    items = doc.items.values
                )
                GostDimensionEngine.drawRoomClearDimensions(
                    drawScope = this,
                    clearDims = clearDims,
                    editorState = editorState,
                    viewport = viewport,
                    textMeasurer = textMeasurer
                )
            }

            // 5. Render Dynamic Furniture Distance Guides (Planner 5D Live Wall Snaps)
            if (editorState.showFurnitureDistanceGuides) {
                val selectedItemId = editorState.selectedItemId
                if (selectedItemId != null) {
                    val item = doc.items[selectedItemId]
                    if (item != null && item.wallId == null) {
                        val guides = GostDimensionEngine.calculateFurnitureDistanceGuides(
                            item = item,
                            walls = walls.values,
                            vertices = vertices
                        )
                        GostDimensionEngine.drawFurnitureDistanceGuides(
                            drawScope = this,
                            guides = guides,
                            editorState = editorState,
                            viewport = viewport,
                            textMeasurer = textMeasurer
                        )
                    }
                }
            }
        }
    }

    /**
     * Draws ГОСТ 21.501 Room Explanations with circled sequence numbers, names, and underlined areas.
     */
    fun drawRoomBadges(
        drawScope: DrawScope,
        editorState: CadEditorState,
        viewport: CadViewportState,
        textMeasurer: TextMeasurer
    ) {
        InteriorPlanningEngine.drawGostRoomExplanations(
            drawScope = drawScope,
            rooms = editorState.document.rooms,
            vertices = editorState.document.vertices,
            editorState = editorState,
            viewport = viewport,
            textMeasurer = textMeasurer
        )
    }

    fun drawVertices(
        drawScope: DrawScope,
        editorState: CadEditorState,
        viewport: CadViewportState
    ) {
        with(drawScope) {
            val doc = editorState.document
            val isWallDrawing = editorState.currentTool == CadTool.DRAW_WALL ||
                    editorState.currentTool == CadTool.SPLIT_WALL ||
                    editorState.drawingStartPoint != null

            val relevantIds = buildSet {
                editorState.selectedVertexId?.let { add(it) }
                editorState.hoveredVertexId?.let { add(it) }
                editorState.activeSnap?.snappedVertexId?.let { add(it) }
                editorState.selectedWallId?.let { wallId ->
                    doc.walls[wallId]?.let {
                        add(it.startVertexId)
                        add(it.endVertexId)
                    }
                }
            }

            if (!isWallDrawing && relevantIds.isEmpty()) return

            val verticesToDraw = if (isWallDrawing) {
                doc.vertices.values
            } else {
                doc.vertices.values.filter { it.id in relevantIds }
            }

            for (v in verticesToDraw) {
                val isSelected = v.id == editorState.selectedVertexId
                val isHovered = v.id == editorState.hoveredVertexId
                val screenPos = viewport.worldToScreen(v.position)

                val radius = if (isSelected) 6.5f else if (isHovered) 5.5f else 4.5f

                drawCircle(Color(0x33000000), radius + 2f, Offset(screenPos.x, screenPos.y + 1f))
                drawCircle(
                    color = if (isSelected) GripSelectedBorder else if (isHovered) GripSnapColor else GripBorderColor,
                    radius = radius + 1.5f,
                    center = screenPos,
                    style = Stroke(width = 1.5f)
                )
                drawCircle(
                    color = if (isSelected) GripSelectedFill else GripFillColor,
                    radius = radius,
                    center = screenPos
                )
            }
        }
    }

    fun drawOverlaysAndSnapping(
        drawScope: DrawScope,
        editorState: CadEditorState,
        viewport: CadViewportState,
        textMeasurer: TextMeasurer
    ) {
        with(drawScope) {
            // Smart alignment guidelines
            editorState.activeSnap?.guideLines?.forEach { guide ->
                val sScreen = viewport.worldToScreen(guide.start)
                val eScreen = viewport.worldToScreen(guide.end)
                drawLine(
                    color = GuideLineColor,
                    start = sScreen,
                    end = eScreen,
                    strokeWidth = 1.2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                )
            }

            // Smart item-to-item alignment guidelines
            editorState.activeItemAlignGuides.forEach { guide ->
                val sScreen = viewport.worldToScreen(guide.start)
                val eScreen = viewport.worldToScreen(guide.end)
                drawLine(
                    color = Color(0xFF0284C7),
                    start = sScreen,
                    end = eScreen,
                    strokeWidth = 1.4f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                )
            }

            // Magnetic snap marker
            editorState.activeSnap?.let { snap ->
                if (snap.type != SnapType.NONE) {
                    val sPos = viewport.worldToScreen(snap.point)
                    drawCircle(GripSnapColor, 9f, sPos, style = Stroke(width = 2f))
                    drawLine(GripSnapColor, Offset(sPos.x - 5f, sPos.y), Offset(sPos.x + 5f, sPos.y), strokeWidth = 1.5f)
                    drawLine(GripSnapColor, Offset(sPos.x, sPos.y - 5f), Offset(sPos.x, sPos.y + 5f), strokeWidth = 1.5f)
                }
            }

            // Rubber-band wall drawing preview
            val startPt = editorState.drawingStartPoint
            val currentPt = editorState.activeSnap?.point ?: editorState.currentCursorWorldPoint

            if (editorState.currentTool == CadTool.DRAW_WALL && startPt != null && currentPt != null) {
                val sScreen = viewport.worldToScreen(startPt)
                val eScreen = viewport.worldToScreen(currentPt)
                val thickScreen = max(editorState.defaultWallThicknessCm * viewport.zoom, 4f)

                drawLine(PreviewWallColor, sScreen, eScreen, strokeWidth = thickScreen, cap = StrokeCap.Butt)
                drawLine(
                    color = PreviewWallOutline,
                    start = sScreen,
                    end = eScreen,
                    strokeWidth = 1.8f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                )

                val lengthCm = startPt.distanceTo(currentPt)
                val lengthText = GostDimensionEngine.formatGostLength(lengthCm, editorState.measurementUnit)
                val seg = LineSegment(startPt, currentPt)
                val angleDeg = seg.angleDegrees.roundToInt()
                val badgeText = "$lengthText  •  $angleDeg°"

                val measured = textMeasurer.measure(
                    badgeText,
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                )

                val badgeOffset = Offset(eScreen.x + 16f, eScreen.y - 28f)
                val badgeW = measured.size.width + 16f
                val badgeH = measured.size.height + 8f

                drawRoundRect(
                    color = Color(0xEE0F172A),
                    topLeft = badgeOffset,
                    size = Size(badgeW, badgeH),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                drawText(measured, topLeft = Offset(badgeOffset.x + 8f, badgeOffset.y + 4f))

                if (angleDeg % 90 == 0) {
                    val rightAngleBadge = textMeasurer.measure("⊾ 90°", style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981)))
                    drawRoundRect(
                        color = Color(0xEE064E3B),
                        topLeft = Offset(sScreen.x + 12f, sScreen.y + 12f),
                        size = Size(rightAngleBadge.size.width + 10f, rightAngleBadge.size.height + 6f),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                    drawText(rightAngleBadge, topLeft = Offset(sScreen.x + 17f, sScreen.y + 15f))
                }
            }

            // Tape measure preview
            val mStart = editorState.measureStartPoint
            val mEnd = editorState.measureEndPoint ?: editorState.currentCursorWorldPoint
            if (editorState.currentTool == CadTool.MEASURE && mStart != null && mEnd != null) {
                val sScreen = viewport.worldToScreen(mStart)
                val eScreen = viewport.worldToScreen(mEnd)

                drawLine(
                    color = Color(0xFFEA580C),
                    start = sScreen,
                    end = eScreen,
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                )
                drawCircle(Color(0xFFEA580C), 5f, sScreen)
                drawCircle(Color(0xFFEA580C), 5f, eScreen)

                val dist = mStart.distanceTo(mEnd)
                val mText = "Дистанция: ${GostDimensionEngine.formatGostLength(dist, editorState.measurementUnit)}"
                val measured = textMeasurer.measure(
                    mText,
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                )
                val badgeOffset = Offset(eScreen.x + 14f, eScreen.y - 24f)
                drawRoundRect(
                    color = Color(0xEE9A3412),
                    topLeft = badgeOffset,
                    size = Size(measured.size.width + 14f, measured.size.height + 8f),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                drawText(measured, topLeft = Offset(badgeOffset.x + 7f, badgeOffset.y + 4f))
            }
        }
    }
}
