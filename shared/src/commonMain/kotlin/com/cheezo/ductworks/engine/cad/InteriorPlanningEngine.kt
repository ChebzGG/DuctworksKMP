package com.cheezo.ductworks.engine.cad

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
import com.cheezo.ductworks.model.*
import com.cheezo.ductworks.ui.CadEditorState
import com.cheezo.ductworks.ui.CadViewportState
import kotlin.math.*

/**
 * High-level Interior Planning & Layout Engine (Planner 5D / ArchiCAD standard).
 *
 * Provides:
 * 1. Magnetic Wall Snapping with Auto-Orientation (мебель автоматически прилипает и поворачивается спинкой к стене)
 * 2. Ergonomic Clearance Zones (зоны открывания дверец, выдвижения ящиков, проходы вокруг кровати и стола)
 * 3. ГОСТ 21.501 Room Explanations (штампы экспликации помещений: кружок с номером, название, подчеркнутая площадь)
 * 4. Furniture Inter-Object Alignment Guides (смарт-линии выравнивания между предметами мебели)
 * 5. Layout Collision & Overlap Detection (обнаружение пересечений со стенами и другой мебелью)
 */
object InteriorPlanningEngine {

    /**
     * Result of magnetic wall snapping for furniture.
     */
    data class WallSnapResult(
        val snappedPosition: Point2D,
        val snappedRotationDegrees: Float,
        val targetWallId: String,
        val distanceToWallCm: Float
    )

    /**
     * Ergonomic clearance boundary to draw around an item.
     */
    data class ClearanceZone(
        val center: Point2D,
        val widthCm: Float,
        val depthCm: Float,
        val rotationDegrees: Float,
        val labelRu: String
    )

    // =========================================================================
    // 1. MAGNETIC WALL SNAPPING & AUTO-ORIENTATION
    // =========================================================================

    /**
     * When dragging furniture near a wall (< [snapThresholdCm]), automatically snaps its rear face
     * flush to the inner face of the wall and rotates it to align with the wall.
     */
    fun resolveWallSnap(
        item: PlacedItem,
        rawTargetPos: Point2D,
        walls: Collection<WallSegment>,
        vertices: Map<String, WallVertex>,
        snapThresholdCm: Float = 40f
    ): WallSnapResult? {
        // Openings handle their own wall snapping
        if (item.wallId != null || isOpeningType(item.itemType)) return null

        var bestSnap: WallSnapResult? = null
        var minDistance = snapThresholdCm

        for (wall in walls) {
            val line = wall.toLineSegment(vertices) ?: continue
            val wallLen = line.length
            if (wallLen < 20f) continue

            // Closest point on wall centerline to item center
            val projCenter = line.closestPointTo(rawTargetPos)
            val distCenterToWall = rawTargetPos.distanceTo(projCenter)

            // Half dimensions
            val halfDepth = item.depthCm * 0.5f
            val halfThick = wall.thicknessCm * 0.5f
            val requiredCenterDist = halfThick + halfDepth

            val gap = abs(distCenterToWall - requiredCenterDist)

            if (gap < minDistance) {
                minDistance = gap
                val wallDir = line.direction
                val wallNormal = line.normal

                // Determine which side of the wall the item is on
                val toItem = rawTargetPos - projCenter
                val sideSign = if (wallNormal.dot(toItem) >= 0) 1f else -1f
                val effectiveNormal = wallNormal * sideSign

                // Flush position against wall face
                val snappedPos = projCenter + effectiveNormal * requiredCenterDist

                // Orient item so its back faces the wall (effectiveNormal points outward from wall into room)
                val targetAngleRad = atan2(effectiveNormal.y, effectiveNormal.x)
                var targetAngleDeg = Math.toDegrees(targetAngleRad.toDouble()).toFloat() + 90f
                if (targetAngleDeg < 0) targetAngleDeg += 360f
                if (targetAngleDeg >= 360f) targetAngleDeg -= 360f

                bestSnap = WallSnapResult(
                    snappedPosition = snappedPos,
                    snappedRotationDegrees = targetAngleDeg,
                    targetWallId = wall.id,
                    distanceToWallCm = gap
                )
            }
        }

        return bestSnap
    }

    // =========================================================================
    // 2. ERGONOMIC CLEARANCE ZONES
    // =========================================================================

    /**
     * Calculates ergonomic passage and opening clearance zones according to Ernst Neufert Architectural Data:
     * - Wardrobes & Dressers: front clearance for opening doors & pulling out drawers (60-80 cm)
     * - Desks: chair clearance behind the desk (75-90 cm)
     * - Dining tables: perimeter seating clearance (60 cm)
     * - Beds: side access walkways (60-70 cm)
     */
    fun calculateClearanceZone(item: PlacedItem): ClearanceZone? {
        val halfW = item.widthCm * 0.5f
        val halfD = item.depthCm * 0.5f

        return when (item.itemType) {
            ItemType.WARDROBE, ItemType.WARDROBE_CORNER -> {
                // Front opening clearance facing room (-halfD)
                val clearanceD = 65f
                val offsetLocal = Point2D(0f, -halfD - clearanceD * 0.5f)
                ClearanceZone(
                    center = item.position + offsetLocal.rotate(item.rotationDegrees),
                    widthCm = item.widthCm,
                    depthCm = clearanceD,
                    rotationDegrees = item.rotationDegrees,
                    labelRu = "Зона открывания дверей"
                )
            }

            ItemType.DRESSER -> {
                val clearanceD = 60f
                val offsetLocal = Point2D(0f, -halfD - clearanceD * 0.5f)
                ClearanceZone(
                    center = item.position + offsetLocal.rotate(item.rotationDegrees),
                    widthCm = item.widthCm,
                    depthCm = clearanceD,
                    rotationDegrees = item.rotationDegrees,
                    labelRu = "Выдвижение ящиков"
                )
            }

            ItemType.DESK_OFFICE, ItemType.DESK_CORNER -> {
                // Chair seating clearance in front of desk facing room (-halfD)
                val clearanceD = 75f
                val offsetLocal = Point2D(0f, -halfD - clearanceD * 0.5f)
                ClearanceZone(
                    center = item.position + offsetLocal.rotate(item.rotationDegrees),
                    widthCm = item.widthCm * 0.85f,
                    depthCm = clearanceD,
                    rotationDegrees = item.rotationDegrees,
                    labelRu = "Рабочая зона кресла"
                )
            }

            ItemType.DINING_TABLE_4, ItemType.DINING_TABLE_6 -> {
                // Perimeter dining chair pullback clearance
                val clearanceMargin = 45f
                ClearanceZone(
                    center = item.position,
                    widthCm = item.widthCm + clearanceMargin * 2f,
                    depthCm = item.depthCm + clearanceMargin * 2f,
                    rotationDegrees = item.rotationDegrees,
                    labelRu = "Зона стульев"
                )
            }

            ItemType.BED_DOUBLE -> {
                // Passageway clearance around bed (sides and foot facing room -halfD)
                val passageW = 55f
                val offsetLocal = Point2D(0f, -passageW * 0.5f)
                ClearanceZone(
                    center = item.position + offsetLocal.rotate(item.rotationDegrees),
                    widthCm = item.widthCm + passageW * 2f,
                    depthCm = item.depthCm + passageW,
                    rotationDegrees = item.rotationDegrees,
                    labelRu = "Проходы к кровати"
                )
            }

            else -> null
        }
    }

    // =========================================================================
    // 3. SMART INTER-OBJECT ALIGNMENT GUIDES
    // =========================================================================

    data class SmartAlignGuide(
        val start: Point2D,
        val end: Point2D,
        val isHorizontal: Boolean
    )

    /**
     * Detects center and edge alignments between the moving item and other placed furniture.
     */
    fun findSmartAlignGuides(
        movingItem: PlacedItem,
        allItems: Collection<PlacedItem>,
        toleranceCm: Float = 6f
    ): List<SmartAlignGuide> {
        val guides = mutableListOf<SmartAlignGuide>()
        val pMove = movingItem.position

        for (other in allItems) {
            if (other.id == movingItem.id) continue
            val pOther = other.position

            // Vertical center alignment (same X)
            if (abs(pMove.x - pOther.x) < toleranceCm) {
                val minY = minOf(pMove.y, pOther.y) - 60f
                val maxY = maxOf(pMove.y, pOther.y) + 60f
                guides.add(
                    SmartAlignGuide(
                        start = Point2D(pOther.x, minY),
                        end = Point2D(pOther.x, maxY),
                        isHorizontal = false
                    )
                )
            }

            // Horizontal center alignment (same Y)
            if (abs(pMove.y - pOther.y) < toleranceCm) {
                val minX = minOf(pMove.x, pOther.x) - 60f
                val maxX = maxOf(pMove.x, pOther.x) + 60f
                guides.add(
                    SmartAlignGuide(
                        start = Point2D(minX, pOther.y),
                        end = Point2D(maxX, pOther.y),
                        isHorizontal = true
                    )
                )
            }
        }

        return guides
    }

    data class ItemSnapResult(
        val snappedPosition: Point2D,
        val guides: List<SmartAlignGuide> = emptyList()
    )

    /**
     * Magnetically aligns furniture edge-to-edge, flush, or center-to-center against adjacent placed items.
     * Facilitates tight groupings (e.g. nightstand next to bed, modular kitchen counters, dining sets).
     */
    fun resolveItemToItemSnap(
        movingItem: PlacedItem,
        rawTargetPos: Point2D,
        otherItems: Collection<PlacedItem>,
        snapDistanceCm: Float = 14f
    ): ItemSnapResult? {
        if (otherItems.isEmpty()) return null

        val rotM = ((movingItem.rotationDegrees % 360f) + 360f) % 360f
        val isRotM90 = (abs(rotM - 90f) < 15f || abs(rotM - 270f) < 15f)
        val wM = if (isRotM90) movingItem.depthCm else movingItem.widthCm
        val dM = if (isRotM90) movingItem.widthCm else movingItem.depthCm
        val hwM = wM * 0.5f
        val hdM = dM * 0.5f

        var bestSnapX: Float? = null
        var minDiffX = snapDistanceCm
        var guideX: SmartAlignGuide? = null

        var bestSnapY: Float? = null
        var minDiffY = snapDistanceCm
        var guideY: SmartAlignGuide? = null

        for (other in otherItems) {
            if (other.id == movingItem.id || other.isOpening) continue

            val rotO = ((other.rotationDegrees % 360f) + 360f) % 360f
            val isRotO90 = (abs(rotO - 90f) < 15f || abs(rotO - 270f) < 15f)
            val wO = if (isRotO90) other.depthCm else other.widthCm
            val dO = if (isRotO90) other.widthCm else other.depthCm
            val hwO = wO * 0.5f
            val hdO = dO * 0.5f
            val pO = other.position

            // Check X axis snap candidates if items are roughly adjacent or overlapping on Y
            val distY = abs(rawTargetPos.y - pO.y)
            if (distY <= (hdM + hdO + 50f)) {
                val candidatesX = listOf(
                    pO.x - hwO - hwM, // Right of moving against Left of other
                    pO.x + hwO + hwM, // Left of moving against Right of other
                    pO.x - hwO + hwM, // Flush Left
                    pO.x + hwO - hwM, // Flush Right
                    pO.x              // Center X
                )
                for (candX in candidatesX) {
                    val diff = abs(rawTargetPos.x - candX)
                    if (diff < minDiffX) {
                        minDiffX = diff
                        bestSnapX = candX
                        val minY = minOf(rawTargetPos.y - hdM, pO.y - hdO) - 20f
                        val maxY = maxOf(rawTargetPos.y + hdM, pO.y + hdO) + 20f
                        guideX = SmartAlignGuide(Point2D(candX, minY), Point2D(candX, maxY), false)
                    }
                }
            }

            // Check Y axis snap candidates if items are roughly adjacent or overlapping on X
            val distX = abs(rawTargetPos.x - pO.x)
            if (distX <= (hwM + hwO + 50f)) {
                val candidatesY = listOf(
                    pO.y - hdO - hdM, // Bottom of moving against Top of other
                    pO.y + hdO + hdM, // Top of moving against Bottom of other
                    pO.y - hdO + hdM, // Flush Top
                    pO.y + hdO - hdM, // Flush Bottom
                    pO.y              // Center Y
                )
                for (candY in candidatesY) {
                    val diff = abs(rawTargetPos.y - candY)
                    if (diff < minDiffY) {
                        minDiffY = diff
                        bestSnapY = candY
                        val minX = minOf(rawTargetPos.x - hwM, pO.x - hwO) - 20f
                        val maxX = maxOf(rawTargetPos.x + hwM, pO.x + hwO) + 20f
                        guideY = SmartAlignGuide(Point2D(minX, candY), Point2D(maxX, candY), true)
                    }
                }
            }
        }

        if (bestSnapX == null && bestSnapY == null) return null

        val finalX = bestSnapX ?: rawTargetPos.x
        val finalY = bestSnapY ?: rawTargetPos.y
        val guides = listOfNotNull(guideX, guideY)

        return ItemSnapResult(Point2D(finalX, finalY), guides)
    }

    // =========================================================================
    // 4. RENDERING METHODS
    // =========================================================================

    /**
     * Renders ergonomic clearance zones with subtle dashed bounding boundaries.
     */
    fun drawClearanceZone(
        drawScope: DrawScope,
        zone: ClearanceZone,
        viewport: CadViewportState
    ) {
        with(drawScope) {
            val centerScreen = viewport.worldToScreen(zone.center)
            val wScreen = zone.widthCm * viewport.zoom
            val dScreen = zone.depthCm * viewport.zoom

            drawContext.canvas.save()
            drawContext.canvas.translate(centerScreen.x, centerScreen.y)
            drawContext.canvas.rotate(zone.rotationDegrees)

            val rect = Offset(-wScreen * 0.5f, -dScreen * 0.5f)
            val size = Size(wScreen, dScreen)

            // Translucent ergonomic area fill
            drawRoundRect(
                color = Color(0x0C0284C7),
                topLeft = rect,
                size = size,
                cornerRadius = CornerRadius(4f, 4f)
            )

            // Dashed outline
            drawRoundRect(
                color = Color(0x550284C7),
                topLeft = rect,
                size = size,
                cornerRadius = CornerRadius(4f, 4f),
                style = Stroke(
                    width = 1.0f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                )
            )

            drawContext.canvas.restore()
        }
    }

    /**
     * Renders ГОСТ 21.501 room explanation stamps at room centroids:
     * - Number circle (кружок экспликации ГОСТ)
     * - Room name
     * - Underlined clean floor area in m²
     * - Clear ceiling height h = 2.70 м
     */
    fun drawGostRoomExplanations(
        drawScope: DrawScope,
        rooms: List<Room>,
        vertices: Map<String, WallVertex>,
        editorState: CadEditorState,
        viewport: CadViewportState,
        textMeasurer: TextMeasurer
    ) {
        if (!editorState.showRoomAreas) return

        with(drawScope) {
            for ((index, room) in rooms.withIndex()) {
                val poly = room.getPolygonPoints(vertices)
                if (poly.size < 3) continue

                val isSelected = room.id == editorState.selectedRoomId
                val centroidWorld = room.calculateCentroid(vertices)
                val centroidScreen = viewport.worldToScreen(centroidWorld)

                val areaM2 = room.calculateAreaM2(vertices)
                val roomNumber = (index + 1).toString()
                val nameText = room.name
                val areaText = "${(round(areaM2 * 100) / 100f)} м²"
                val heightText = "h = ${(room.ceilingHeightCm / 100f)} м"

                // Measure text
                val numMeasured = textMeasurer.measure(
                    roomNumber,
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A))
                )
                val nameMeasured = textMeasurer.measure(
                    nameText,
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSelected) Color(0xFF1D4ED8) else Color(0xFF1E293B))
                )
                val areaMeasured = textMeasurer.measure(
                    areaText,
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (isSelected) Color(0xFF1E3A8A) else Color(0xFF0F172A))
                )
                val heightMeasured = textMeasurer.measure(
                    heightText,
                    style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Normal, color = Color(0xFF64748B))
                )

                val contentW = maxOf(nameMeasured.size.width, areaMeasured.size.width) + 32f
                val contentH = nameMeasured.size.height + areaMeasured.size.height + heightMeasured.size.height + 26f
                val badgeTopLeft = Offset(centroidScreen.x - contentW * 0.5f, centroidScreen.y - contentH * 0.5f)

                // 1. ГОСТ Card background with soft drop shadow
                drawRoundRect(
                    color = Color(0x12000000),
                    topLeft = Offset(badgeTopLeft.x, badgeTopLeft.y + 2f),
                    size = Size(contentW, contentH),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                drawRoundRect(
                    color = Color(0xF8FFFFFF),
                    topLeft = badgeTopLeft,
                    size = Size(contentW, contentH),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                drawRoundRect(
                    color = if (isSelected) Color(0xFF2563EB) else Color(0xFFCBD5E1),
                    topLeft = badgeTopLeft,
                    size = Size(contentW, contentH),
                    cornerRadius = CornerRadius(6f, 6f),
                    style = Stroke(width = if (isSelected) 1.8f else 1.0f)
                )

                // 2. ГОСТ 21.501 Room sequence number in circle at top
                val circleRadius = 10f
                val circleCenter = Offset(centroidScreen.x, badgeTopLeft.y + 14f)
                drawCircle(Color(0xFFF1F5F9), circleRadius, circleCenter)
                drawCircle(if (isSelected) Color(0xFF2563EB) else Color(0xFF475569), circleRadius, circleCenter, style = Stroke(1.2f))
                drawText(numMeasured, topLeft = Offset(circleCenter.x - numMeasured.size.width * 0.5f, circleCenter.y - numMeasured.size.height * 0.5f))

                // 3. Room Name
                val nameY = circleCenter.y + circleRadius + 4f
                drawText(nameMeasured, topLeft = Offset(centroidScreen.x - nameMeasured.size.width * 0.5f, nameY))

                // 4. Area under horizontal underline (ГОСТ 21.501 standard)
                val lineY = nameY + nameMeasured.size.height + 2f
                val underLineWidth = maxOf(nameMeasured.size.width, areaMeasured.size.width).toFloat() + 8f
                drawLine(
                    color = if (isSelected) Color(0xFF2563EB) else Color(0xFF64748B),
                    start = Offset(centroidScreen.x - underLineWidth * 0.5f, lineY),
                    end = Offset(centroidScreen.x + underLineWidth * 0.5f, lineY),
                    strokeWidth = 1.2f
                )

                val areaY = lineY + 3f
                drawText(areaMeasured, topLeft = Offset(centroidScreen.x - areaMeasured.size.width * 0.5f, areaY))

                // 5. Ceiling height
                val hY = areaY + areaMeasured.size.height + 1f
                drawText(heightMeasured, topLeft = Offset(centroidScreen.x - heightMeasured.size.width * 0.5f, hY))
            }
        }
    }

    /**
     * Renders smart inter-object alignment guidelines.
     */
    fun drawSmartAlignGuides(
        drawScope: DrawScope,
        guides: List<SmartAlignGuide>,
        viewport: CadViewportState
    ) {
        with(drawScope) {
            val guideColor = Color(0xFF0284C7)
            for (g in guides) {
                val sScreen = viewport.worldToScreen(g.start)
                val eScreen = viewport.worldToScreen(g.end)
                drawLine(
                    color = guideColor,
                    start = sScreen,
                    end = eScreen,
                    strokeWidth = 1.2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                )
            }
        }
    }

    private fun isOpeningType(type: ItemType): Boolean = type.isOpening
}
