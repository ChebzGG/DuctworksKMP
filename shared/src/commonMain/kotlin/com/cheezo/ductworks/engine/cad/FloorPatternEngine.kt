package com.cheezo.ductworks.engine.cad

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import com.cheezo.ductworks.model.BoundingBox
import com.cheezo.ductworks.model.Point2D
import com.cheezo.ductworks.model.Room
import com.cheezo.ductworks.model.RoomType
import com.cheezo.ductworks.ui.CadViewportState
import kotlin.math.*

/**
 * Architectural floor material and texture rendering engine (Planner 5D / ArchiCAD standard).
 * Renders high-fidelity procedural floor coverings:
 * - Straight plank parquet & laminate with staggered joints and bevels
 * - French chevron (шеврон под 45°)
 * - English herringbone (классическая паркетная ёлочка)
 * - Large-format porcelain stoneware (керамогранит 60×60)
 * - Bathroom ceramic tile (плитка 30×30)
 * - Calacatta marble slabs
 * - Terrace decking and cozy carpet
 */
object FloorPatternEngine {

    enum class FloorMaterial(
        val titleRu: String,
        val baseColorHex: Long,
        val seamColorHex: Long,
        val plankWidthCm: Float = 20f,
        val plankLengthCm: Float = 120f
    ) {
        NATURAL_OAK("Дуб натуральный", 0xFFFBF8F3, 0xFFE2D9CC, 16f, 120f),
        SCANDI_ASH("Светлый ясень", 0xFFFAF9F6, 0xFFEBE6DC, 18f, 130f),
        DARK_WALNUT("Темный орех", 0xFFF3EFE9, 0xFFD8CEBF, 16f, 120f),
        HERRINGBONE("Паркет Ёлочка", 0xFFF7F3EB, 0xFFDFD6C7, 12f, 60f),
        FRENCH_CHEVRON("Французский шеврон", 0xFFF8F4EC, 0xFFDFD6C8, 12f, 60f),
        TILES_60X60("Керамогранит 60×60", 0xFFF1F5F9, 0xFFCBD5E1, 60f, 60f),
        TILES_30X30("Плитка 30×30", 0xFFF0FDF4, 0xFFCFEBD7, 30f, 30f),
        TILES_DIAGONAL("Диагональная плитка", 0xFFF8FAFC, 0xFFCBD5E1, 40f, 40f),
        MARBLE_CALACATTA("Мрамор Calacatta", 0xFFFAFBFD, 0xFFE2E8F0, 80f, 80f),
        CONCRETE_SCREED("Лофт бетон", 0xFFF1F5F9, 0xFFCBD5E1, 100f, 100f),
        COZY_CARPET("Ковролин", 0xFFF8FAFC, 0xFFE2E8F0, 10f, 10f),
        TERRACE_DECKING("Террасная доска", 0xFFF8F2E8, 0xFFD5C4B0, 14f, 200f)
    }

    /**
     * Resolves the default floor material according to architectural room function.
     */
    fun getMaterialForRoomType(type: RoomType): FloorMaterial = when (type) {
        RoomType.LIVING_ROOM -> FloorMaterial.NATURAL_OAK
        RoomType.BEDROOM -> FloorMaterial.SCANDI_ASH
        RoomType.KITCHEN -> FloorMaterial.TILES_60X60
        RoomType.BATHROOM -> FloorMaterial.TILES_30X30
        RoomType.CORRIDOR -> FloorMaterial.TILES_60X60
        RoomType.OFFICE -> FloorMaterial.HERRINGBONE
        RoomType.STORAGE -> FloorMaterial.TILES_30X30
        RoomType.CUSTOM -> FloorMaterial.NATURAL_OAK
    }

    /**
     * Draws procedural floor material with clipping strictly inside the room polygon.
     */
    fun drawRoomFloor(
        drawScope: DrawScope,
        room: Room,
        polygonWorld: List<Point2D>,
        material: FloorMaterial,
        isSelected: Boolean,
        viewport: CadViewportState
    ) {
        if (polygonWorld.size < 3) return

        with(drawScope) {
            val screenPoints = polygonWorld.map { viewport.worldToScreen(it) }

            val roomPath = Path().apply {
                moveTo(screenPoints[0].x, screenPoints[0].y)
                for (i in 1 until screenPoints.size) {
                    lineTo(screenPoints[i].x, screenPoints[i].y)
                }
                close()
            }

            // 1. Draw base floor background color
            val baseColor = if (isSelected) Color(0xFFDBEAFE) else Color(material.baseColorHex)
            drawPath(roomPath, baseColor, style = Fill)

            // 2. Draw procedural texture pattern with clipping
            drawContext.canvas.save()
            drawContext.canvas.clipPath(roomPath)

            try {
                val zoom = viewport.zoom
                // LOD: only render detailed plank lines if zoom is sufficient to avoid visual noise
                if (zoom >= 0.22f) {
                    when (material) {
                        FloorMaterial.NATURAL_OAK,
                        FloorMaterial.SCANDI_ASH,
                        FloorMaterial.DARK_WALNUT,
                        FloorMaterial.TERRACE_DECKING -> {
                            drawPlankParquet(this, polygonWorld, material, viewport)
                        }

                        FloorMaterial.TILES_60X60,
                        FloorMaterial.TILES_30X30,
                        FloorMaterial.CONCRETE_SCREED -> {
                            drawTileGrid(this, polygonWorld, material, viewport)
                        }

                        FloorMaterial.TILES_DIAGONAL -> {
                            drawDiagonalTileGrid(this, polygonWorld, material, viewport)
                        }

                        FloorMaterial.HERRINGBONE,
                        FloorMaterial.FRENCH_CHEVRON -> {
                            drawHerringboneParquet(this, polygonWorld, material, viewport)
                        }

                        FloorMaterial.MARBLE_CALACATTA -> {
                            drawMarbleSlabs(this, polygonWorld, material, viewport)
                        }

                        FloorMaterial.COZY_CARPET -> {
                            drawCarpetTexture(this, polygonWorld, material, viewport)
                        }
                    }
                }
            } finally {
                drawContext.canvas.restore()
            }

            // 3. Crisp architectural boundary stroke
            val borderColor = if (isSelected) Color(0xFF2563EB) else Color(0xFFCBD5E1)
            drawPath(roomPath, borderColor, style = Stroke(width = if (isSelected) 2.2f else 1.0f))
        }
    }

    /**
     * Staggered straight wood planks with micro-bevel joints.
     */
    private fun drawPlankParquet(
        drawScope: DrawScope,
        poly: List<Point2D>,
        material: FloorMaterial,
        viewport: CadViewportState
    ) {
        with(drawScope) {
            val bbox = getBoundingBox(poly)
            val seamColor = Color(material.seamColorHex).copy(alpha = 0.55f)
            val plankW = material.plankWidthCm
            val plankL = material.plankLengthCm

            var curY = floor(bbox.minY / plankW) * plankW
            var rowIndex = 0

            while (curY <= bbox.maxY) {
                val s1 = viewport.worldToScreen(Point2D(bbox.minX - 50f, curY))
                val s2 = viewport.worldToScreen(Point2D(bbox.maxX + 50f, curY))

                // Long horizontal plank seam
                drawLine(seamColor, s1, s2, strokeWidth = 0.9f)

                // Staggered vertical end-joints (offset by 1/3 every row)
                val staggerOffset = (rowIndex % 3) * (plankL / 3f)
                var curX = floor(bbox.minX / plankL) * plankL + staggerOffset
                while (curX <= bbox.maxX) {
                    val pTop = viewport.worldToScreen(Point2D(curX, curY))
                    val pBot = viewport.worldToScreen(Point2D(curX, curY + plankW))
                    drawLine(seamColor, pTop, pBot, strokeWidth = 0.8f)
                    curX += plankL
                }

                curY += plankW
                rowIndex++
            }
        }
    }

    /**
     * Orthogonal square tile grid with thin grout seams.
     */
    private fun drawTileGrid(
        drawScope: DrawScope,
        poly: List<Point2D>,
        material: FloorMaterial,
        viewport: CadViewportState
    ) {
        with(drawScope) {
            val bbox = getBoundingBox(poly)
            val seamColor = Color(material.seamColorHex).copy(alpha = 0.6f)
            val step = material.plankWidthCm

            var curX = floor(bbox.minX / step) * step
            while (curX <= bbox.maxX) {
                val s1 = viewport.worldToScreen(Point2D(curX, bbox.minY - 50f))
                val s2 = viewport.worldToScreen(Point2D(curX, bbox.maxY + 50f))
                drawLine(seamColor, s1, s2, strokeWidth = 0.9f)
                curX += step
            }

            var curY = floor(bbox.minY / step) * step
            while (curY <= bbox.maxY) {
                val s1 = viewport.worldToScreen(Point2D(bbox.minX - 50f, curY))
                val s2 = viewport.worldToScreen(Point2D(bbox.maxX + 50f, curY))
                drawLine(seamColor, s1, s2, strokeWidth = 0.9f)
                curY += step
            }
        }
    }

    /**
     * Diagonal tile grid rotated 45 degrees.
     */
    private fun drawDiagonalTileGrid(
        drawScope: DrawScope,
        poly: List<Point2D>,
        material: FloorMaterial,
        viewport: CadViewportState
    ) {
        with(drawScope) {
            val bbox = getBoundingBox(poly)
            val seamColor = Color(material.seamColorHex).copy(alpha = 0.55f)
            val step = material.plankWidthCm * 1.414f

            val minK = floor((bbox.minX + bbox.minY) / step).toInt() - 2
            val maxK = ceil((bbox.maxX + bbox.maxY) / step).toInt() + 2

            for (k in minK..maxK) {
                val c = k * step
                val p1 = viewport.worldToScreen(Point2D(bbox.minX - 50f, c - (bbox.minX - 50f)))
                val p2 = viewport.worldToScreen(Point2D(bbox.maxX + 50f, c - (bbox.maxX + 50f)))
                drawLine(seamColor, p1, p2, strokeWidth = 0.9f)
            }

            val minD = floor((bbox.minX - bbox.maxY) / step).toInt() - 2
            val maxD = ceil((bbox.maxX - bbox.minY) / step).toInt() + 2

            for (d in minD..maxD) {
                val c = d * step
                val p1 = viewport.worldToScreen(Point2D(bbox.minX - 50f, (bbox.minX - 50f) - c))
                val p2 = viewport.worldToScreen(Point2D(bbox.maxX + 50f, (bbox.maxX + 50f) - c))
                drawLine(seamColor, p1, p2, strokeWidth = 0.9f)
            }
        }
    }

    /**
     * Herringbone / Chevron interlocking wood blocks.
     */
    private fun drawHerringboneParquet(
        drawScope: DrawScope,
        poly: List<Point2D>,
        material: FloorMaterial,
        viewport: CadViewportState
    ) {
        with(drawScope) {
            val bbox = getBoundingBox(poly)
            val seamColor = Color(material.seamColorHex).copy(alpha = 0.5f)
            val slatW = material.plankWidthCm
            val slatL = material.plankLengthCm

            val colStep = slatL * 1.414f * 0.5f
            var colX = floor(bbox.minX / colStep) * colStep

            while (colX <= bbox.maxX + colStep) {
                var rowY = floor(bbox.minY / colStep) * colStep
                while (rowY <= bbox.maxY + colStep) {
                    val pCenter = Point2D(colX, rowY)
                    val p1 = viewport.worldToScreen(pCenter + Point2D(-slatW, -slatL * 0.5f).rotate(45f))
                    val p2 = viewport.worldToScreen(pCenter + Point2D(slatW, slatL * 0.5f).rotate(45f))
                    drawLine(seamColor, p1, p2, strokeWidth = 0.8f)

                    val p3 = viewport.worldToScreen(pCenter + Point2D(-slatW, -slatL * 0.5f).rotate(-45f))
                    val p4 = viewport.worldToScreen(pCenter + Point2D(slatW, slatL * 0.5f).rotate(-45f))
                    drawLine(seamColor, p3, p4, strokeWidth = 0.8f)

                    rowY += colStep
                }
                colX += colStep
            }
        }
    }

    /**
     * Large marble slabs with subtle veining.
     */
    private fun drawMarbleSlabs(
        drawScope: DrawScope,
        poly: List<Point2D>,
        material: FloorMaterial,
        viewport: CadViewportState
    ) {
        with(drawScope) {
            drawTileGrid(drawScope, poly, material, viewport)

            // Subtle marble vein accents
            val bbox = getBoundingBox(poly)
            val veinColor = Color(0xFFE2E8F0).copy(alpha = 0.4f)
            var vy = bbox.minY + 40f
            while (vy <= bbox.maxY) {
                val s1 = viewport.worldToScreen(Point2D(bbox.minX + 20f, vy))
                val s2 = viewport.worldToScreen(Point2D(bbox.minX + 90f, vy + 25f))
                val s3 = viewport.worldToScreen(Point2D(bbox.minX + 160f, vy + 10f))
                drawLine(veinColor, s1, s2, strokeWidth = 1.0f)
                drawLine(veinColor, s2, s3, strokeWidth = 0.8f)
                vy += 120f
            }
        }
    }

    /**
     * Micro-weave texture for carpeted rooms.
     */
    private fun drawCarpetTexture(
        drawScope: DrawScope,
        poly: List<Point2D>,
        material: FloorMaterial,
        viewport: CadViewportState
    ) {
        with(drawScope) {
            val bbox = getBoundingBox(poly)
            val dotColor = Color(material.seamColorHex).copy(alpha = 0.35f)
            val step = 25f

            var cx = floor(bbox.minX / step) * step
            while (cx <= bbox.maxX) {
                var cy = floor(bbox.minY / step) * step
                while (cy <= bbox.maxY) {
                    val sp = viewport.worldToScreen(Point2D(cx, cy))
                    drawCircle(dotColor, 1.2f, sp)
                    cy += step
                }
                cx += step
            }
        }
    }

    private fun getBoundingBox(poly: List<Point2D>): BoundingBox {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        for (p in poly) {
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }
        return BoundingBox(minX, minY, maxX, maxY)
    }
}
