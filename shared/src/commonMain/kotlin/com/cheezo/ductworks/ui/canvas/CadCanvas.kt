package com.cheezo.ductworks.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import kotlin.math.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cheezo.ductworks.model.*
import com.cheezo.ductworks.ui.CadEditorState
import com.cheezo.ductworks.ui.components.CadIcons
import com.cheezo.ductworks.ui.CadTool
import com.cheezo.ductworks.ui.CadViewportState
import com.cheezo.ductworks.ui.DragSession

@Composable
fun CadCanvas(
    editorState: CadEditorState,
    viewport: CadViewportState,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val itemPainters = com.cheezo.ductworks.ui.components.CadResourceResolver.rememberItemPainters()

    var isRmbPanningState by remember { mutableStateOf(false) }
    var isLmbPanningState by remember { mutableStateOf(false) }

    val canvasCursor = remember(
        isRmbPanningState,
        isLmbPanningState,
        editorState.currentTool,
        editorState.activeDragSession,
        editorState.hoveredItemId,
        editorState.hoveredVertexId,
        editorState.hoveredWallId
    ) {
        when {
            isRmbPanningState || isLmbPanningState -> PointerIcon.Hand
            editorState.activeDragSession !is DragSession.None -> PointerIcon.Hand
            editorState.currentTool == CadTool.DRAW_WALL || editorState.currentTool == CadTool.MEASURE -> PointerIcon.Crosshair
            editorState.currentTool == CadTool.SPLIT_WALL -> PointerIcon.Crosshair
            editorState.hoveredItemId != null || editorState.hoveredVertexId != null || editorState.hoveredWallId != null -> PointerIcon.Hand
            else -> PointerIcon.Default
        }
    }

    var contextMenuState by remember { mutableStateOf<CanvasContextMenuState?>(null) }
    var contextMenuRect by remember { mutableStateOf<Rect?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerHoverIcon(canvasCursor)
            // Unified rock-solid CAD pointer event loop (Key is Unit so gesture coroutine is NEVER cancelled mid-drag)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    var downScreenPos = Offset.Zero
                    var downWorldPos = Point2D.Zero
                    var isDragActive = false
                    var isCanvasPanning = false

                    var wasSecondaryPressed = false
                    var rmbDownScreenPos = Offset.Zero
                    var rmbHitObject: Any? = null
                    var isRmbPanning = false

                    var hitRotationKnobAtDown: PlacedItem? = null
                    var hitVertexAtDown: WallVertex? = null
                    var hitItemAtDown: PlacedItem? = null
                    var hitWallAtDown: Pair<WallSegment, Point2D>? = null
                    var hitRoomAtDown: Room? = null

                    while (true) {
                        val event = awaitPointerEvent()

                        // 1. СКМ (Middle Mouse Button / Tertiary) pan navigation
                        if (event.buttons.isTertiaryPressed) {
                            val change = event.changes.firstOrNull()
                            if (change != null && change.pressed) {
                                val panDelta = change.position - change.previousPosition
                                viewport.panBy(panDelta)
                                change.consume()
                            }
                            continue
                        }

                        // 2. ПКМ (Right-click / Secondary): Pan layout on empty space or open Context Menu on items
                        val isSecondaryDown = event.buttons.isSecondaryPressed
                        if (isSecondaryDown) {
                            val primary = event.changes.firstOrNull()
                            if (primary != null) {
                                if (!wasSecondaryPressed) {
                                    // Press down secondary button
                                    rmbDownScreenPos = primary.position
                                    if (editorState.drawingStartPoint != null) {
                                        editorState.cancelCurrentOperation()
                                        isRmbPanning = false
                                        isRmbPanningState = false
                                        rmbHitObject = null
                                    } else {
                                        val worldPt = viewport.screenToWorld(rmbDownScreenPos)
                                        val hitRadius = (20f / viewport.zoom).coerceIn(10f, 45f)
                                        val hitV = editorState.engine.findNearestVertex(worldPt, hitRadius)
                                        val hitI = if (hitV == null) editorState.engine.findNearestItem(worldPt) else null
                                        val hitW = if (hitV == null && hitI == null) editorState.engine.findNearestWall(worldPt, hitRadius) else null

                                        if (hitV == null && hitI == null && hitW == null) {
                                            // Empty space: start panning layout
                                            isRmbPanning = true
                                            isRmbPanningState = true
                                            rmbHitObject = null
                                            contextMenuState = null
                                        } else {
                                            isRmbPanning = false
                                            isRmbPanningState = false
                                            rmbHitObject = hitI ?: hitW ?: hitV
                                        }
                                    }
                                } else if (isRmbPanning) {
                                    // Dragging RMB on empty space: pan the plan
                                    val panDelta = primary.position - primary.previousPosition
                                    if (panDelta != Offset.Zero) {
                                        viewport.panBy(panDelta)
                                    }
                                }
                                primary.consume()
                            }
                            wasSecondaryPressed = true
                            continue
                        } else if (wasSecondaryPressed) {
                            // Secondary button released
                            val primary = event.changes.firstOrNull()
                            if (isRmbPanning) {
                                isRmbPanning = false
                                isRmbPanningState = false
                            } else if (rmbHitObject != null && primary != null) {
                                val dist = (primary.position - rmbDownScreenPos).getDistance()
                                if (dist <= 6f) {
                                    when (val target = rmbHitObject) {
                                        is PlacedItem -> {
                                            editorState.selectItem(target.id)
                                            contextMenuState = CanvasContextMenuState(rmbDownScreenPos, ContextTarget.Item(target))
                                        }
                                        is Pair<*, *> -> {
                                            @Suppress("UNCHECKED_CAST")
                                            val wPair = target as Pair<WallSegment, Point2D>
                                            editorState.selectWall(wPair.first.id)
                                            contextMenuState = CanvasContextMenuState(rmbDownScreenPos, ContextTarget.Wall(wPair.first, wPair.second))
                                        }
                                    }
                                }
                            }
                            rmbHitObject = null
                            wasSecondaryPressed = false
                        }

                        // 3. СКМ Mouse wheel zoom around cursor
                        for (change in event.changes) {
                            val scrollDelta = change.scrollDelta
                            if (scrollDelta.y != 0f) {
                                val zoomFactor = if (scrollDelta.y < 0) 1.15f else 0.87f
                                viewport.zoomAround(change.position, zoomFactor)
                                change.consume()
                            }
                        }

                        val primary = event.changes.firstOrNull() ?: continue
                        val screenPos = primary.position
                        val worldPos = viewport.screenToWorld(screenPos)
                        editorState.currentCursorWorldPoint = worldPos

                        val isDown = primary.pressed
                        val wasDown = primary.previousPressed

                        // --- MOUSE DOWN (PRESS) ---
                        if (!wasDown && isDown) {
                            if (contextMenuState != null && contextMenuRect?.contains(screenPos) == true) {
                                // Pointer down inside context menu: let menu buttons handle click
                                continue
                            }
                            contextMenuState = null
                            contextMenuRect = null
                            downScreenPos = screenPos
                            downWorldPos = worldPos
                            isDragActive = false
                            isCanvasPanning = false

                            val hitRadius = (20f / viewport.zoom).coerceIn(10f, 45f)

                            // Check if clicked on rotation knob of currently selected item
                            val selItem = editorState.selectedItemId?.let { editorState.document.items[it] }
                            val knobScreen = selItem?.let { getItemRotationKnobScreen(it, viewport) }
                            hitRotationKnobAtDown = if (knobScreen != null && (screenPos - knobScreen).getDistance() <= 18f) selItem else null

                            hitVertexAtDown = if (hitRotationKnobAtDown == null) {
                                editorState.engine.findNearestVertex(worldPos, hitRadius)
                            } else null
                            hitItemAtDown = if (hitRotationKnobAtDown == null && hitVertexAtDown == null) {
                                editorState.engine.findNearestItem(worldPos)
                            } else null
                            hitWallAtDown = if (hitRotationKnobAtDown == null && hitVertexAtDown == null && hitItemAtDown == null) {
                                editorState.engine.findNearestWall(worldPos, hitRadius)
                            } else null
                            hitRoomAtDown = if (hitRotationKnobAtDown == null && hitVertexAtDown == null && hitItemAtDown == null && hitWallAtDown == null) {
                                editorState.engine.findRoomAt(worldPos)
                            } else null
                        }

                        // --- MOUSE DRAG / MOVE WHILE PRESSED (LMB: Drag components only) ---
                        else if (wasDown && isDown) {
                            val moveDist = (screenPos - downScreenPos).getDistance()

                            // Threshold to distinguish click vs drag (4 screen pixels)
                            if (!isDragActive && moveDist >= 4f) {
                                isDragActive = true
                                if (editorState.currentTool == CadTool.SELECT) {
                                    when {
                                        hitRotationKnobAtDown != null -> {
                                            editorState.startRotateItem(hitRotationKnobAtDown.id, hitRotationKnobAtDown.position)
                                        }
                                        hitVertexAtDown != null -> {
                                            editorState.startDragVertex(hitVertexAtDown.id, downWorldPos)
                                        }
                                        hitItemAtDown != null -> {
                                            editorState.startDragItem(hitItemAtDown.id, downWorldPos)
                                        }
                                        hitWallAtDown != null -> {
                                            editorState.startDragWall(hitWallAtDown.first.id, downWorldPos)
                                        }
                                        hitRoomAtDown != null && editorState.selectedRoomId == hitRoomAtDown.id -> {
                                            editorState.startDragRoom(hitRoomAtDown.id, downWorldPos)
                                        }
                                        else -> {
                                            isCanvasPanning = true
                                            isLmbPanningState = true
                                        }
                                    }
                                } else if (editorState.drawingStartPoint == null) {
                                    isCanvasPanning = true
                                    isLmbPanningState = true
                                } else {
                                    isCanvasPanning = false
                                }
                            }

                            if (isDragActive) {
                                primary.consume()
                                when (val session = editorState.activeDragSession) {
                                    is DragSession.RotateItem -> {
                                        val centerScreen = viewport.worldToScreen(session.centerWorld)
                                        val dx = screenPos.x - centerScreen.x
                                        val dy = screenPos.y - centerScreen.y
                                        val rad = atan2(dy.toDouble(), dx.toDouble()).toFloat()
                                        var deg = Math.toDegrees(rad.toDouble()).toFloat() + 90f
                                        if (deg < 0f) deg += 360f
                                        if (deg >= 360f) deg -= 360f

                                        val item = editorState.document.items[session.itemId]
                                        if (item != null && item.itemType.isOpening) {
                                            val wall = (item.wallId?.let { editorState.document.walls[it] })
                                                ?: editorState.engine.findNearestWall(item.position, item.widthCm)?.first
                                            if (wall != null) {
                                                val vStart = editorState.document.vertices[wall.startVertexId]?.position
                                                val vEnd = editorState.document.vertices[wall.endVertexId]?.position
                                                if (vStart != null && vEnd != null) {
                                                    val wallAngle = (Math.toDegrees(atan2((vEnd.y - vStart.y).toDouble(), (vEnd.x - vStart.x).toDouble())).toFloat() + 360f) % 360f
                                                    val a1 = wallAngle
                                                    val a2 = (wallAngle + 180f) % 360f

                                                    fun diffAngle(a: Float, b: Float): Float = ((a - b + 540f) % 360f) - 180f
                                                    val d1 = abs(diffAngle(deg, a1))
                                                    val d2 = abs(diffAngle(deg, a2))
                                                    deg = if (d1 <= d2) a1 else a2
                                                }
                                            }
                                        } else if (editorState.isOrthoEnabled) {
                                            deg = (round(deg / 15f) * 15f) % 360f
                                        }
                                        editorState.updateRotateItem(deg)
                                    }
                                    is DragSession.Vertex -> {
                                        val otherVertices = editorState.document.vertices.values.filter { it.id != session.vertexId }
                                        val snap = CadSnapper.resolveSnap(
                                            rawPoint = worldPos,
                                            originPoint = session.startPointer,
                                            vertices = otherVertices,
                                            walls = editorState.document.walls.values,
                                            vertexMap = editorState.document.vertices,
                                            gridStepCm = editorState.gridStepCm,
                                            isGridSnapEnabled = editorState.isGridSnapEnabled,
                                            isOrthoEnabled = editorState.isOrthoEnabled
                                        )
                                        editorState.activeSnap = snap
                                        editorState.updateDragVertex(snap.point)
                                    }
                                    is DragSession.Item -> {
                                        editorState.updateDragItem(worldPos)
                                    }
                                    is DragSession.Wall -> {
                                        val wall = editorState.document.walls[session.wallId]
                                        val vStart = wall?.let { editorState.document.vertices[it.startVertexId]?.position }
                                        val vEnd = wall?.let { editorState.document.vertices[it.endVertexId]?.position }
                                        val otherVertices = editorState.document.vertices.values.filter {
                                            wall == null || (it.id != wall.startVertexId && it.id != wall.endVertexId)
                                        }
                                        val targetPt = if (vStart != null && vEnd != null) {
                                            val wallVec = vEnd - vStart
                                            val isWallHoriz = abs(wallVec.y) <= abs(wallVec.x)
                                            if (editorState.isOrthoEnabled) {
                                                if (isWallHoriz) {
                                                    val rawDy = worldPos.y - session.startPointer.y
                                                    var dy = rawDy
                                                    var guide: GuideLine? = null
                                                    if (editorState.isGridSnapEnabled) {
                                                        for (v in otherVertices) {
                                                             if (abs(vStart.y + rawDy - v.position.y) < 15f) {
                                                                 dy = v.position.y - vStart.y
                                                                 guide = GuideLine(Point2D(v.position.x - 200f, v.position.y), Point2D(v.position.x + 200f, v.position.y), true)
                                                                 break
                                                             }
                                                             if (abs(vEnd.y + rawDy - v.position.y) < 15f) {
                                                                 dy = v.position.y - vEnd.y
                                                                 guide = GuideLine(Point2D(v.position.x - 200f, v.position.y), Point2D(v.position.x + 200f, v.position.y), true)
                                                                 break
                                                             }
                                                        }
                                                        if (guide == null) {
                                                            dy = CadSnapper.snapToGrid(rawDy, editorState.gridStepCm)
                                                        }
                                                    }
                                                    val pt = Point2D(session.startPointer.x, session.startPointer.y + dy)
                                                    editorState.activeSnap = SnapResult(pt, if (guide != null) SnapType.ALIGNMENT_GUIDE else SnapType.ORTHO_AXIS, guideLines = listOfNotNull(guide))
                                                    pt
                                                } else {
                                                    val rawDx = worldPos.x - session.startPointer.x
                                                    var dx = rawDx
                                                    var guide: GuideLine? = null
                                                    if (editorState.isGridSnapEnabled) {
                                                        for (v in otherVertices) {
                                                             if (abs(vStart.x + rawDx - v.position.x) < 15f) {
                                                                 dx = v.position.x - vStart.x
                                                                 guide = GuideLine(Point2D(v.position.x, v.position.y - 200f), Point2D(v.position.x, v.position.y + 200f), false)
                                                                 break
                                                             }
                                                             if (abs(vEnd.x + rawDx - v.position.x) < 15f) {
                                                                 dx = v.position.x - vEnd.x
                                                                 guide = GuideLine(Point2D(v.position.x, v.position.y - 200f), Point2D(v.position.x, v.position.y + 200f), false)
                                                                 break
                                                             }
                                                        }
                                                        if (guide == null) {
                                                            dx = CadSnapper.snapToGrid(rawDx, editorState.gridStepCm)
                                                        }
                                                    }
                                                    val pt = Point2D(session.startPointer.x + dx, session.startPointer.y)
                                                    editorState.activeSnap = SnapResult(pt, if (guide != null) SnapType.ALIGNMENT_GUIDE else SnapType.ORTHO_AXIS, guideLines = listOfNotNull(guide))
                                                    pt
                                                }
                                            } else {
                                                // Ortho is OFF: free 2D wall dragging
                                                val rawDx = worldPos.x - session.startPointer.x
                                                val rawDy = worldPos.y - session.startPointer.y
                                                val dx = if (editorState.isGridSnapEnabled) CadSnapper.snapToGrid(rawDx, editorState.gridStepCm) else rawDx
                                                val dy = if (editorState.isGridSnapEnabled) CadSnapper.snapToGrid(rawDy, editorState.gridStepCm) else rawDy
                                                val pt = Point2D(session.startPointer.x + dx, session.startPointer.y + dy)
                                                editorState.activeSnap = SnapResult(pt, if (editorState.isGridSnapEnabled) SnapType.GRID else SnapType.NONE)
                                                pt
                                            }
                                        } else {
                                            worldPos
                                        }
                                        editorState.updateDragWall(targetPt)
                                    }
                                    is DragSession.RoomDrag -> {
                                        editorState.updateDragRoom(worldPos)
                                    }
                                    is DragSession.None -> {
                                        if (isCanvasPanning) {
                                            val delta = screenPos - primary.previousPosition
                                            viewport.panBy(delta)
                                        }
                                    }
                                }
                            }
                        }

                        // --- MOUSE UP (RELEASE) ---
                        else if (wasDown && !isDown) {
                            if (isDragActive) {
                                primary.consume()
                                editorState.endDrag()
                            } else {
                                // Clean Click / Tap
                                handleCanvasClick(worldPos, editorState, viewport)
                            }
                            isDragActive = false
                            isCanvasPanning = false
                            isLmbPanningState = false
                            hitRotationKnobAtDown = null
                            hitVertexAtDown = null
                            hitItemAtDown = null
                            hitWallAtDown = null
                            hitRoomAtDown = null
                        }

                        // --- HOVER (NO BUTTONS PRESSED) ---
                        else {
                            val hitRadius = (20f / viewport.zoom).coerceIn(10f, 45f)
                            val hVertex = editorState.engine.findNearestVertex(worldPos, hitRadius)
                            editorState.hoveredVertexId = hVertex?.id

                            if (hVertex == null) {
                                val hItem = editorState.engine.findNearestItem(worldPos)
                                editorState.hoveredItemId = hItem?.id
                                if (hItem == null) {
                                    val hWall = editorState.engine.findNearestWall(worldPos, hitRadius)
                                    editorState.hoveredWallId = hWall?.first?.id
                                } else {
                                    editorState.hoveredWallId = null
                                }
                            } else {
                                editorState.hoveredItemId = null
                                editorState.hoveredWallId = null
                            }

                            val originPt = editorState.drawingStartPoint
                            val snap = CadSnapper.resolveSnap(
                                rawPoint = worldPos,
                                originPoint = originPt,
                                vertices = editorState.document.vertices.values,
                                walls = editorState.document.walls.values,
                                vertexMap = editorState.document.vertices,
                                gridStepCm = editorState.gridStepCm,
                                isGridSnapEnabled = editorState.isGridSnapEnabled,
                                isOrthoEnabled = editorState.isOrthoEnabled,
                                vertexSnapRadiusCm = hitRadius,
                                wallSnapRadiusCm = hitRadius * 0.75f
                            )
                            editorState.activeSnap = snap
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize().clipToBounds()) {
            drawContext.canvas.save()
            drawContext.canvas.clipRect(0f, 0f, size.width, size.height)
            try {
                // 1. Pure white CAD canvas background
                drawRect(CadRenderer.CanvasBgColor)

                // 2. CAD precision grid lines
                if (editorState.isGridVisible) {
                    CadRenderer.drawCadGrid(this, viewport, editorState, textMeasurer)
                }

                // 3. Room Floor Fills (soft architectural floor textures/colors)
                CadRenderer.drawRoomFloors(this, editorState, viewport)

                // 4. Ergonomic clearance zones (zones of passage and door opening)
                CadRenderer.drawErgonomicsClearances(this, editorState, viewport)

                // 5. Furniture and fixtures on the floor
                ItemRenderer.drawFurnitureAndFixtures(this, editorState, viewport, itemPainters)

                // 6. Architectural Walls (mitered polygons, hatching, and dimensions)
                CadRenderer.drawWalls(this, editorState, viewport, textMeasurer)

                // 7. Wall Openings (Doors and windows cutting cleanly through walls)
                ItemRenderer.drawOpenings(this, editorState, viewport, itemPainters)

                // 8. Compact room centroid badges (Room name + Area m² pill)
                CadRenderer.drawRoomBadges(this, editorState, viewport, textMeasurer)

                // 9. Selected item selection overlay (bounds, handles, rotate knob)
                ItemRenderer.drawSelectionOverlay(this, editorState, viewport, textMeasurer)

                // 10. Vertex grip handles (only shown when drawing or selected/hovered)
                CadRenderer.drawVertices(this, editorState, viewport)

                // 11. Snapping markers, alignment guidelines, and rubber-band previews
                CadRenderer.drawOverlaysAndSnapping(this, editorState, viewport, textMeasurer)

                // 12. Architectural Grid Rulers (Vertical & Horizontal ruler marks & coordinate badges)
                if (editorState.isGridVisible) {
                    CadRenderer.drawCadRulers(this, viewport, editorState, textMeasurer)
                }
            } finally {
                drawContext.canvas.restore()
            }
        }

        // Context Menu on Right Click (ПКМ only, never on LMB)
        contextMenuState?.let { menu ->
            CanvasContextMenu(
                menuState = menu,
                editorState = editorState,
                onPositioned = { rect -> contextMenuRect = rect },
                onClose = {
                    contextMenuState = null
                    contextMenuRect = null
                }
            )
        }
    }
}

/**
 * Handles clicks when the pointer was released without dragging.
 */
private fun handleCanvasClick(
    worldPoint: Point2D,
    editorState: CadEditorState,
    viewport: CadViewportState
) {
    val activeSnap = editorState.activeSnap
    val effectivePoint = if (activeSnap != null && activeSnap.type != SnapType.NONE) {
        activeSnap.point
    } else {
        worldPoint
    }

    val hitRadius = (20f / viewport.zoom).coerceIn(10f, 45f)

    when (editorState.currentTool) {
        CadTool.SELECT -> {
            val vertex = editorState.engine.findNearestVertex(worldPoint, hitRadius)
            if (vertex != null) {
                editorState.selectVertex(vertex.id)
                return
            }

            val item = editorState.engine.findNearestItem(worldPoint)
            if (item != null) {
                editorState.selectItem(item.id)
                return
            }

            val wallMatch = editorState.engine.findNearestWall(worldPoint, hitRadius)
            if (wallMatch != null) {
                editorState.selectWall(wallMatch.first.id)
                return
            }

            val room = editorState.engine.findRoomAt(worldPoint)
            if (room != null) {
                editorState.selectRoom(room.id)
                return
            }

            editorState.clearSelection()
        }

        CadTool.DRAW_WALL -> {
            val snapVertexId = activeSnap?.snappedVertexId
            editorState.addWallPoint(effectivePoint, snapVertexId)
        }

        CadTool.RECTANGLE_ROOM -> {
            editorState.addRectangleRoomAt(effectivePoint)
        }

        CadTool.L_SHAPED_ROOM -> {
            editorState.addLShapedRoomAt(effectivePoint)
        }

        CadTool.SPLIT_WALL -> {
            val wallMatch = editorState.engine.findNearestWall(worldPoint, 25f)
            if (wallMatch != null) {
                editorState.splitWallAt(wallMatch.first.id, wallMatch.second)
                editorState.selectTool(CadTool.SELECT)
            }
        }

        CadTool.MEASURE -> {
            if (editorState.measureStartPoint == null) {
                editorState.measureStartPoint = effectivePoint
            } else {
                editorState.measureEndPoint = effectivePoint
            }
        }

        CadTool.DELETE -> {
            editorState.deleteItemAt(worldPoint)
        }
    }
}

sealed class ContextTarget {
    data class Wall(val wall: WallSegment, val clickWorld: Point2D) : ContextTarget()
    data class Item(val item: PlacedItem) : ContextTarget()
}

data class CanvasContextMenuState(
    val screenOffset: Offset,
    val target: ContextTarget
)

@Composable
private fun CanvasContextMenu(
    menuState: CanvasContextMenuState,
    editorState: CadEditorState,
    onPositioned: (Rect) -> Unit,
    onClose: () -> Unit
) {
    val target = menuState.target
    Box(
        modifier = Modifier
            .offset { IntOffset(menuState.screenOffset.x.toInt() + 8, menuState.screenOffset.y.toInt() + 8) }
            .onGloballyPositioned { coords ->
                onPositioned(coords.boundsInParent())
            }
            .shadow(6.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
            .padding(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            when (target) {
                is ContextTarget.Wall -> {
                    // Разрезать стену
                    ContextMenuIconButton(onClick = {
                        editorState.splitWallAt(target.wall.id, target.clickWorld)
                        onClose()
                    }) {
                        CadIcons.SplitWall(tint = Color(0xFF475569))
                    }
                    // Удалить стену
                    ContextMenuIconButton(danger = true, onClick = {
                        editorState.deleteSelected()
                        onClose()
                    }) {
                        CadIcons.Trash(tint = Color(0xFFEF4444))
                    }
                }
                is ContextTarget.Item -> {
                    // Поворот против часовой стрелки (-90°)
                    ContextMenuIconButton(onClick = {
                        editorState.rotateSelectedItem(-90f)
                        onClose()
                    }) {
                        CadIcons.RotateCcw(tint = Color(0xFF475569))
                    }
                    // Поворот по часовой стрелке (+90°)
                    ContextMenuIconButton(onClick = {
                        editorState.rotateSelectedItem(90f)
                        onClose()
                    }) {
                        CadIcons.RotateCw(tint = Color(0xFF475569))
                    }
                    // Отразить по горизонтали (Flip H)
                    ContextMenuIconButton(onClick = {
                        editorState.flipSelectedItemHorizontal()
                        onClose()
                    }) {
                        CadIcons.FlipH(tint = Color(0xFF475569))
                    }
                    // Дублировать (Копия)
                    ContextMenuIconButton(onClick = {
                        editorState.duplicateSelectedItem()
                        onClose()
                    }) {
                        CadIcons.Duplicate(tint = Color(0xFF475569))
                    }
                    // На передний план (Layer Up)
                    ContextMenuIconButton(onClick = {
                        editorState.bringSelectedItemToFront()
                        onClose()
                    }) {
                        CadIcons.LayerUp(tint = Color(0xFF475569))
                    }
                    // На задний план (Layer Down)
                    ContextMenuIconButton(onClick = {
                        editorState.sendSelectedItemToBack()
                        onClose()
                    }) {
                        CadIcons.LayerDown(tint = Color(0xFF475569))
                    }
                    // Удалить (Корзина)
                    ContextMenuIconButton(danger = true, onClick = {
                        editorState.deleteSelectedItem()
                        onClose()
                    }) {
                        CadIcons.Trash(tint = Color(0xFFEF4444))
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextMenuIconButton(
    danger: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFFFFFFF))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

private fun getItemRotationKnobScreen(item: PlacedItem, viewport: CadViewportState): Offset {
    val centerScreen = viewport.worldToScreen(item.position)
    val depthScreen = item.depthCm * viewport.zoom
    val halfD = depthScreen * 0.5f + 4f
    val stemLen = 24f
    val dist = halfD + stemLen
    val rad = Math.toRadians(item.rotationDegrees.toDouble())
    val sinA = sin(rad).toFloat()
    val cosA = cos(rad).toFloat()
    return Offset(centerScreen.x + dist * sinA, centerScreen.y - dist * cosA)
}
