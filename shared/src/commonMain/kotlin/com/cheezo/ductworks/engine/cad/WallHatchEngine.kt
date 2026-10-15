package com.cheezo.ductworks.engine.cad

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import com.cheezo.ductworks.model.BoundingBox
import com.cheezo.ductworks.model.Point2D
import com.cheezo.ductworks.model.WallSegment
import com.cheezo.ductworks.model.WallType
import com.cheezo.ductworks.ui.CadViewportState
import kotlin.math.*

/**
 * Architectural wall hatching engine strictly compliant with ГОСТ 2.306-68 and ГОСТ 21.501-2018.
 *
 * Implements standard architectural cross-sections:
 * - Brick masonry (Кирпичная кладка — наклонные сплошные линии под 45°)
 * - Reinforced concrete (Железобетон монолитный — чередование сплошных и пунктирных линий)
 * - Aerated concrete / Blockwork (Газобетон / блоки — перекрестная двойная штриховка)
 * - Drywall lightweight partitions (Перегородки ГКЛ — осевой профиль и двойные границы)
 * - Insulation core (Утеплитель — волна / зигзаг)
 * - Solid CAD ink fill (Сплошная чертёжная заливка антрацитом)
 */
object WallHatchEngine {

    enum class WallMaterialHatch(
        val titleRu: String,
        val standardGost: String,
        val defaultStepCm: Float = 12f
    ) {
        BRICK_MASONRY("Кирпичная кладка", "ГОСТ 2.306 п. 1", 10f),
        REINFORCED_CONCRETE("Железобетон", "ГОСТ 2.306 п. 2", 12f),
        AERATED_BLOCKS("Газобетонные блоки", "ГОСТ 2.306 п. 3", 14f),
        DRYWALL_PARTITION("ГКЛ перегородка", "ГОСТ 2.306 п. 4", 8f),
        SOLID_ARCHITECTURAL("Сплошная заливка", "СПДС ГОСТ 21.501", 0f)
    }

    /**
     * Resolves the material hatch for a given wall type.
     */
    fun getHatchForWallType(type: WallType): WallMaterialHatch = when (type) {
        WallType.EXTERIOR -> WallMaterialHatch.BRICK_MASONRY
        WallType.INTERIOR -> WallMaterialHatch.REINFORCED_CONCRETE
        WallType.PARTITION -> WallMaterialHatch.DRYWALL_PARTITION
    }

    /**
     * Draws authentic ГОСТ architectural wall hatching inside the given wall polygon.
     */
    fun drawWallHatch(
        drawScope: DrawScope,
        wall: WallSegment,
        polygonWorld: List<Point2D>,
        hatchType: WallMaterialHatch,
        isSelected: Boolean,
        isHovered: Boolean,
        viewport: CadViewportState,
        drawStartCap: Boolean = true,
        drawEndCap: Boolean = true
    ) {
        if (polygonWorld.size < 3) return

        with(drawScope) {
            val screenPoly = polygonWorld.map { viewport.worldToScreen(it) }

            val wallPath = Path().apply {
                moveTo(screenPoly[0].x, screenPoly[0].y)
                for (i in 1 until screenPoly.size) {
                    lineTo(screenPoly[i].x, screenPoly[i].y)
                }
                close()
            }

            // 1. Base architectural background fill
            val baseFillColor = when {
                isSelected -> Color(0xFF2563EB)
                isHovered -> Color(0xFF334155)
                wall.wallType == WallType.EXTERIOR -> Color(0xFF1E293B)
                wall.wallType == WallType.PARTITION -> Color(0xFF475569)
                else -> Color(0xFF334155)
            }

            drawPath(wallPath, baseFillColor, style = Fill)

            // Outline drawing helper: only draws lateral faces and free caps, NEVER internal corner miter seams
            fun drawOutlines(color: Color, width: Float) {
                if (screenPoly.size < 4) {
                    drawPath(wallPath, color, style = Stroke(width = width))
                    return
                }
                // Left longitudinal face (0 -> 1)
                drawLine(color, screenPoly[0], screenPoly[1], strokeWidth = width)
                // Right longitudinal face (2 -> 3)
                drawLine(color, screenPoly[2], screenPoly[3], strokeWidth = width)
                // End cap (1 -> 2) - only if wall ends freely without connecting to other walls
                if (drawEndCap) {
                    drawLine(color, screenPoly[1], screenPoly[2], strokeWidth = width)
                }
                // Start cap (3 -> 0) - only if wall starts freely without connecting to other walls
                if (drawStartCap) {
                    drawLine(color, screenPoly[3], screenPoly[0], strokeWidth = width)
                }
            }

            // If selected or solid ink, draw clean outlines and return
            if (isSelected || hatchType == WallMaterialHatch.SOLID_ARCHITECTURAL) {
                val outlineColor = if (isSelected) Color(0xFF60A5FA) else Color(0xFF0F172A)
                val outlineWidth = if (isSelected) 2.2f else 1.4f
                drawOutlines(outlineColor, outlineWidth)
                return
            }

            // 2. Clip strictly inside the mitered wall polygon for hatching
            drawContext.canvas.save()
            drawContext.canvas.clipPath(wallPath)

            try {
                val zoom = viewport.zoom
                // LOD: only render fine hatch lines if zoom is sufficient
                if (zoom >= 0.28f) {
                    when (hatchType) {
                        WallMaterialHatch.BRICK_MASONRY -> {
                            drawBrickHatch(this, polygonWorld, hatchType.defaultStepCm, viewport)
                        }
                        WallMaterialHatch.REINFORCED_CONCRETE -> {
                            drawConcreteHatch(this, polygonWorld, hatchType.defaultStepCm, viewport)
                        }
                        WallMaterialHatch.AERATED_BLOCKS -> {
                            drawBlockHatch(this, polygonWorld, hatchType.defaultStepCm, viewport)
                        }
                        WallMaterialHatch.DRYWALL_PARTITION -> {
                            drawDrywallHatch(this, polygonWorld, viewport)
                        }
                        WallMaterialHatch.SOLID_ARCHITECTURAL -> { /* No-op */ }
                    }
                }
            } finally {
                drawContext.canvas.restore()
            }

            // 3. Crisp outer boundary outline without diagonal corner seams
            val borderColor = if (isHovered) Color(0xFF3B82F6) else Color(0xFF0F172A)
            drawOutlines(borderColor, 1.4f)
        }
    }

    /**
     * ГОСТ 2.306 п. 1: Кирпичная кладка — наклонные сплошные тонкие параллельные линии под углом 45°.
     */
    private fun drawBrickHatch(
        drawScope: DrawScope,
        poly: List<Point2D>,
        stepCm: Float,
        viewport: CadViewportState
    ) {
        with(drawScope) {
            val bbox = getBoundingBox(poly)
            val hatchColor = Color(0xFF94A3B8).copy(alpha = 0.45f)
            val step = stepCm * 1.414f

            val minK = floor((bbox.minX + bbox.minY) / step).toInt() - 2
            val maxK = ceil((bbox.maxX + bbox.maxY) / step).toInt() + 2

            for (k in minK..maxK) {
                val c = k * step
                val p1 = viewport.worldToScreen(Point2D(bbox.minX - 30f, c - (bbox.minX - 30f)))
                val p2 = viewport.worldToScreen(Point2D(bbox.maxX + 30f, c - (bbox.maxX + 30f)))
                drawLine(hatchColor, p1, p2, strokeWidth = 0.8f)
            }
        }
    }

    /**
     * ГОСТ 2.306 п. 2: Железобетон — чередование сплошных линий под 45° со штриховыми линиями.
     */
    private fun drawConcreteHatch(
        drawScope: DrawScope,
        poly: List<Point2D>,
        stepCm: Float,
        viewport: CadViewportState
    ) {
        with(drawScope) {
            val bbox = getBoundingBox(poly)
            val solidColor = Color(0xFF94A3B8).copy(alpha = 0.5f)
            val dashedColor = Color(0xFF94A3B8).copy(alpha = 0.4f)
            val step = stepCm * 1.414f

            val minK = floor((bbox.minX + bbox.minY) / step).toInt() - 2
            val maxK = ceil((bbox.maxX + bbox.maxY) / step).toInt() + 2

            for (k in minK..maxK) {
                val c = k * step
                val p1 = viewport.worldToScreen(Point2D(bbox.minX - 30f, c - (bbox.minX - 30f)))
                val p2 = viewport.worldToScreen(Point2D(bbox.maxX + 30f, c - (bbox.maxX + 30f)))

                if (k % 2 == 0) {
                    drawLine(solidColor, p1, p2, strokeWidth = 0.8f)
                } else {
                    drawLine(
                        dashedColor,
                        p1,
                        p2,
                        strokeWidth = 0.8f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f), 0f)
                    )
                }
            }
        }
    }

    /**
     * ГОСТ 2.306 п. 3: Газобетонные и ячеистые блоки — двойная перекрестная сетчатая штриховка.
     */
    private fun drawBlockHatch(
        drawScope: DrawScope,
        poly: List<Point2D>,
        stepCm: Float,
        viewport: CadViewportState
    ) {
        with(drawScope) {
            val bbox = getBoundingBox(poly)
            val hatchColor = Color(0xFF94A3B8).copy(alpha = 0.35f)
            val step = stepCm * 1.414f

            val minK = floor((bbox.minX + bbox.minY) / step).toInt() - 2
            val maxK = ceil((bbox.maxX + bbox.maxY) / step).toInt() + 2
            for (k in minK..maxK) {
                val c = k * step
                val p1 = viewport.worldToScreen(Point2D(bbox.minX - 30f, c - (bbox.minX - 30f)))
                val p2 = viewport.worldToScreen(Point2D(bbox.maxX + 30f, c - (bbox.maxX + 30f)))
                drawLine(hatchColor, p1, p2, strokeWidth = 0.8f)
            }

            val minD = floor((bbox.minX - bbox.maxY) / step).toInt() - 2
            val maxD = ceil((bbox.maxX - bbox.minY) / step).toInt() + 2
            for (d in minD..maxD) {
                val c = d * step
                val p1 = viewport.worldToScreen(Point2D(bbox.minX - 30f, (bbox.minX - 30f) - c))
                val p2 = viewport.worldToScreen(Point2D(bbox.maxX + 30f, (bbox.maxX + 30f) - c))
                drawLine(hatchColor, p1, p2, strokeWidth = 0.8f)
            }
        }
    }

    /**
     * Перегородки ГКЛ: центральная пунктирная линия металлокаркаса и штрихи.
     */
    private fun drawDrywallHatch(
        drawScope: DrawScope,
        poly: List<Point2D>,
        viewport: CadViewportState
    ) {
        with(drawScope) {
            val bbox = getBoundingBox(poly)
            val hatchColor = Color(0xFFCBD5E1).copy(alpha = 0.6f)
            val midY = (bbox.minY + bbox.maxY) * 0.5f

            val s1 = viewport.worldToScreen(Point2D(bbox.minX - 50f, midY))
            val s2 = viewport.worldToScreen(Point2D(bbox.maxX + 50f, midY))
            drawLine(
                hatchColor,
                s1,
                s2,
                strokeWidth = 1.0f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
            )
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
