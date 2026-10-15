package com.cheezo.ductworks.engine.cad

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cheezo.ductworks.model.*
import com.cheezo.ductworks.ui.CadEditorState
import com.cheezo.ductworks.ui.CadViewportState
import com.cheezo.ductworks.ui.MeasurementUnit
import kotlin.math.*

/**
 * Ограничители размерных линий по стандарту ЕСКД (ГОСТ 2.307-2011) и СПДС (ГОСТ 21.501-2018).
 */
enum class GostDimensionTerminator(val displayName: String) {
    /** Стрелки на концах размерных линий */
    ARROWS_GOST_20("Стрелки"),
    /** Архитектурные засечки под 45° */
    ARCHITECTURAL_TICKS_45("Засечки")
}

/**
 * Архитектурный и машиностроительный движок размерных линий по ГОСТ 2.307-2011 ЕСКД
 * и СПДС ГОСТ 21.501-2018 (с поведением Planner 5D / ArchiCAD).
 *
 * Нормативные требования ГОСТ 2.307-2011:
 * 1. Параллельность: размерная линия проводится строго параллельно измеряемому отрезку.
 * 2. Расстояние от контура: наносится вне контура на расстоянии не менее 10 мм от линии видимого контура.
 * 3. Расстояние между линиями: между параллельными размерными линиями — не менее 7 мм.
 * 4. Выносные линии: перпендикулярны размерным линиям, начинаются от контура и выходят за концы стрелок/засечек на 1–5 мм (2–3 мм).
 * 5. Ограничение стрелками: длина стрелки 5.5–6 толщин основной линии, угол при вершине ~20°.
 *    При недостатке места стрелки наносятся снаружи остриями внутрь или заменяются засечками 45° / точками.
 * 6. Размерные числа: наносятся НАД размерной линией (ближе к середине) на высоте около 1 мм от неё.
 *    Линейные размеры указываются в миллиметрах без знака единицы измерения.
 */
object GostDimensionEngine {

    // --- Styling Tokens (ГОСТ 2.303-68 / ГОСТ 2.307-2011) ---
    val DimensionLineColor = Color(0xFF1E293B)          // Dark ink slate (сплошная тонкая размерная линия)
    val DimensionSelectedColor = Color(0xFF2563EB)      // CAD Royal Blue (выбранная стена или цепочка)
    val DimensionOpeningColor = Color(0xFF0284C7)       // Deep Sky Blue для размеров окон и дверей
    val DimensionTextColor = Color(0xFF0F172A)          // Четкий чертежный текст
    val ExtensionLineColor = Color(0xFF475569)          // Тонкая выносная линия (сплошная тонкая)
    val FurnitureGuideColor = Color(0xFF0284C7)         // Направляющие расстояний до мебели
    val InteriorDimensionColor = Color(0xFF334155)      // Чистые внутренние размеры помещений

    // --- Нормативные константы ГОСТ 2.307-2011 в экранных пикселях (при 96 DPI: 1 мм ~ 3.78 px) ---
    const val MM_TO_SCREEN_PX = 3.78f

    /** Расстояние от линии видимого контура: не менее 10 мм по ГОСТ 2.307 (>= 38 px на экране) */
    const val MIN_CONTOUR_OFFSET_PX = 38.0f

    /** Расстояние между параллельными размерными линиями: не менее 7 мм по ГОСТ 2.307 (>= 28 px на экране) */
    const val MIN_INTERLINE_OFFSET_PX = 28.0f

    /** Выход выносных линий за размерную: 2-3 мм по ГОСТ 2.307 (9.5 px = 2.5 мм) */
    const val EXTENSION_OVERSHOOT_PX = 9.5f

    /** Зазор от линии видимого контура до начала выносной линии (1 мм = 3.8 px) */
    const val EXTENSION_CLEARANCE_PX = 3.5f

    /** Высота размерных чисел над размерной линией: около 1 мм по ГОСТ 2.307 (3.8 px) */
    const val TEXT_HEIGHT_ABOVE_LINE_PX = 3.8f

    /** Длина стрелки ГОСТ 2.307: 5.5-6 толщин линии видимого контура (~14 px = ~3.7 мм) */
    const val ARROW_LENGTH_PX = 14.0f

    /** Угол при вершине стрелки ГОСТ 2.307: ~20° (половинный угол 10°) */
    const val ARROW_APEX_ANGLE_DEG = 20.0f

    /** Длина архитектурной засечки под 45° по ГОСТ 21.501: 3-4 мм (12 px) */
    const val TICK_LENGTH_PX = 12.0f

    /**
     * Отрезок в цепочке размеров (простенок или оконный/дверной проем).
     */
    data class DimensionSegment(
        val startWorld: Point2D,
        val endWorld: Point2D,
        val lengthCm: Float,
        val isOpening: Boolean = false,
        val openingName: String? = null
    )

    /**
     * Размерная цепочка стены по ГОСТ 2.307-2011 / СПДС ГОСТ 21.501.
     */
    data class WallDimensionChain(
        val wallId: String,
        val level: Int,                       // 1 = простенки и проемы, 2 = габаритная длина
        val baselineStart: Point2D,
        val baselineEnd: Point2D,
        val wallNormal: Point2D,              // Внешняя нормаль от стены наружу здания
        val offsetCm: Float,                  // Расстояние от оси стены до размерной линии в см
        val wallThicknessCm: Float,
        val segments: List<DimensionSegment>
    )

    /**
     * Динамическая направляющая расстояния от мебели до ближайшей стены.
     */
    data class FurnitureDistanceGuide(
        val itemPoint: Point2D,
        val wallPoint: Point2D,
        val distanceCm: Float,
        val wallId: String,
        val direction: Point2D
    )

    /**
     * Чистый внутренний размер между гранями стен комнаты (Planner 5D / ArchiCAD).
     */
    data class RoomClearDimension(
        val roomId: String,
        val innerStart: Point2D,
        val innerEnd: Point2D,
        val clearLengthCm: Float,
        val normalIntoRoom: Point2D
    )

    // =========================================================================
    // 1. РАСЧЁТ РАЗМЕРНЫХ ЦЕПОЧЕК (ГОСТ 2.307-2011 / ГОСТ 21.501-2018)
    // =========================================================================

    // --- Helper: Line-Line Intersection ---
    private fun intersectLines(p1: Point2D, d1: Point2D, p2: Point2D, d2: Point2D): Point2D? {
        val cross = d1.x * d2.y - d1.y * d2.x
        if (abs(cross) < 1e-4f) return null // Parallel or collinear
        val dp = p2 - p1
        val t = (dp.x * d2.y - dp.y * d2.x) / cross
        return p1 + d1 * t
    }

    // --- Helper: Find Outer Corner of Building at Wall Vertex ---
    private fun findOuterCorner(
        wall: WallSegment,
        vertexId: String,
        extNormal: Point2D,
        walls: Collection<WallSegment>,
        vertices: Map<String, WallVertex>
    ): Point2D {
        val vPt = vertices[vertexId]?.position ?: return Point2D.Zero
        val line = wall.toLineSegment(vertices) ?: return vPt
        val wallDir = line.vector.normalized()
        val isStart = (vertexId == wall.startVertexId)
        val outwardAlongWall = if (isStart) wallDir * -1f else wallDir
        val wallFacePt = vPt + extNormal * (wall.thicknessCm * 0.5f)

        // Find connected wall at this vertex that is exterior or forms a corner
        val otherWalls = walls.filter { it.id != wall.id && (it.startVertexId == vertexId || it.endVertexId == vertexId) }
        if (otherWalls.isEmpty()) {
            return wallFacePt
        }

        for (other in otherWalls) {
            val oLine = other.toLineSegment(vertices) ?: continue
            val oDir = if (other.startVertexId == vertexId) oLine.vector.normalized() else (oLine.vector * -1f).normalized()
            val dot = abs(wallDir.dot(oDir))
            // Non-collinear connected wall (corner)
            if (dot < 0.94f) {
                val cand1 = oDir.normal()
                val cand2 = cand1 * -1f
                // The correct exterior normal for `other` is the one that points in the same outward direction along `wall`
                val oNormal = if (cand1.dot(outwardAlongWall) >= cand2.dot(outwardAlongWall)) cand1 else cand2
                val oFacePt = vPt + oNormal * (other.thicknessCm * 0.5f)

                val pt = intersectLines(wallFacePt, wallDir, oFacePt, oDir)
                if (pt != null) {
                    val distFromVertex = pt.distanceTo(vPt)
                    val maxAllowable = (wall.thicknessCm + other.thicknessCm) * 1.5f
                    if (distFromVertex <= maxAllowable) {
                        return pt
                    }
                }
            }
        }

        // Direct geometric fallback: extend along wall direction by connected wall's half thickness
        val maxOtherThick = otherWalls.maxOfOrNull { it.thicknessCm } ?: wall.thicknessCm
        return wallFacePt + outwardAlongWall * (maxOtherThick * 0.5f)
    }

    /**
     * Рассчитывает двухуровневые размерные цепочки стен:
     * - Уровень 1: Цепочка простенков и проемов (окна, двери)
     * - Уровень 2: Габаритный размер стены от внешних граней (краев) здания
     *
     * Строго соблюдаются правила ГОСТ 2.307-2011:
     * - Параллельность измеряемой стене.
     * - Измерение от наружных граней (краев) стен, а не от осей.
     * - Расстояние от контура видимой грани >= 10 мм.
     * - Расстояние между Уровнем 1 и Уровнем 2 >= 7 мм.
     */
    fun calculateWallDimensionChains(
        walls: Collection<WallSegment>,
        vertices: Map<String, WallVertex>,
        items: Collection<PlacedItem>,
        rooms: Collection<Room> = emptyList(),
        viewport: CadViewportState
    ): List<WallDimensionChain> {
        val result = mutableListOf<WallDimensionChain>()
        val openingsByWall = items.filter { it.wallId != null }.groupBy { it.wallId!! }

        for (wall in walls) {
            val line = wall.toLineSegment(vertices) ?: continue
            val wallLen = line.length
            if (wallLen < 15f) continue

            val wallOpenings = openingsByWall[wall.id].orEmpty()
            val thickness = wall.thicknessCm

            // Определяем внешнюю сторону стены (наружу от помещений)
            var extNormal = line.normal
            val midWall = line.midpoint

            for (room in rooms) {
                val vIds = room.vertexIds
                val isWallInRoom = vIds.indices.any { i ->
                    val v1 = vIds[i]
                    val v2 = vIds[(i + 1) % vIds.size]
                    (wall.startVertexId == v1 && wall.endVertexId == v2) ||
                    (wall.startVertexId == v2 && wall.endVertexId == v1)
                }
                if (isWallInRoom) {
                    val poly = room.getPolygonPoints(vertices)
                    if (poly.size >= 3) {
                        val centroid = PolygonMath.calculateCentroid(poly)
                        val toCentroid = centroid - midWall
                        // Если нормаль направлена внутрь комнаты, инвертируем ее наружу здания
                        if (extNormal.dot(toCentroid) > 0) {
                            extNormal = extNormal * -1f
                        }
                    }
                    break
                }
            }

            // Требование ГОСТ 2.307: не менее 10 мм от линии видимого контура
            val halfThickness = thickness * 0.5f
            val minContourOffsetCm = max(35f, MIN_CONTOUR_OFFSET_PX / viewport.zoom)
            val level1OffsetCm = halfThickness + minContourOffsetCm

            // Расстояние между параллельными линиями: не менее 7 мм по ГОСТ 2.307
            val minInterlineOffsetCm = max(28f, MIN_INTERLINE_OFFSET_PX / viewport.zoom)
            val level2OffsetCm = level1OffsetCm + minInterlineOffsetCm

            // Измерение от наружных граней (краев) стен
            val wallDir = line.vector.normalized()
            val outerStart = findOuterCorner(wall, wall.startVertexId, extNormal, walls, vertices)
            val outerEnd = findOuterCorner(wall, wall.endVertexId, extNormal, walls, vertices)

            val tStart = (outerStart - line.start).dot(wallDir)
            val tEnd = (outerEnd - line.start).dot(wallDir)
            val outerLen = abs(tEnd - tStart)
            val baseStart = line.start + wallDir * tStart
            val baseEnd = line.start + wallDir * tEnd

            if (wallOpenings.isEmpty()) {
                // Стена без проемов: габаритный уровень от внешних граней
                result.add(
                    WallDimensionChain(
                        wallId = wall.id,
                        level = 1,
                        baselineStart = baseStart,
                        baselineEnd = baseEnd,
                        wallNormal = extNormal,
                        offsetCm = level1OffsetCm,
                        wallThicknessCm = thickness,
                        segments = listOf(
                            DimensionSegment(
                                startWorld = baseStart,
                                endWorld = baseEnd,
                                lengthCm = outerLen,
                                isOpening = false
                            )
                        )
                    )
                )
            } else {
                // Стена с проемами: рассчитываем цепочку простенков и проемов от наружных краев
                data class OpeningInterval(val t0: Float, val t1: Float, val item: PlacedItem)

                val intervals = wallOpenings.mapNotNull { op ->
                    val proj = line.closestPointTo(op.position)
                    val tCenter = ((proj - line.start).dot(line.vector) / (wallLen * wallLen)).coerceIn(0f, 1f)
                    val halfWidthT = (op.widthCm * 0.5f) / wallLen
                    val t0 = (tCenter - halfWidthT).coerceIn(0f, 1f)
                    val t1 = (tCenter + halfWidthT).coerceIn(0f, 1f)
                    if (t1 > t0 + 0.01f) OpeningInterval(t0, t1, op) else null
                }.sortedBy { it.t0 }

                // Объединение перекрывающихся проемов
                val merged = mutableListOf<OpeningInterval>()
                for (inter in intervals) {
                    if (merged.isEmpty()) {
                        merged.add(inter)
                    } else {
                        val last = merged.last()
                        if (inter.t0 <= last.t1 + 0.02f) {
                            merged[merged.lastIndex] = OpeningInterval(last.t0, maxOf(last.t1, inter.t1), last.item)
                        } else {
                            merged.add(inter)
                        }
                    }
                }

                // Уровень 1: Простенки и проемы от наружного края стены
                val level1Segments = mutableListOf<DimensionSegment>()
                var currentWorld = baseStart

                for (op in merged) {
                    val opStart = line.start + line.vector * op.t0
                    val opEnd = line.start + line.vector * op.t1

                    // Простенок перед проемом
                    val pierLen = currentWorld.distanceTo(opStart)
                    if (pierLen >= 5f) {
                        level1Segments.add(
                            DimensionSegment(
                                startWorld = currentWorld,
                                endWorld = opStart,
                                lengthCm = pierLen,
                                isOpening = false
                            )
                        )
                    }

                    // Проем (окно/дверь)
                    val opLen = opStart.distanceTo(opEnd)
                    level1Segments.add(
                        DimensionSegment(
                            startWorld = opStart,
                            endWorld = opEnd,
                            lengthCm = opLen,
                            isOpening = true,
                            openingName = op.item.name
                        )
                    )
                    currentWorld = opEnd
                }

                // Завершающий простенок после последнего проема до наружного края стены
                val lastPierLen = currentWorld.distanceTo(baseEnd)
                if (lastPierLen >= 5f) {
                    level1Segments.add(
                        DimensionSegment(
                            startWorld = currentWorld,
                            endWorld = baseEnd,
                            lengthCm = lastPierLen,
                            isOpening = false
                        )
                    )
                }

                if (level1Segments.isNotEmpty()) {
                    result.add(
                        WallDimensionChain(
                            wallId = wall.id,
                            level = 1,
                            baselineStart = baseStart,
                            baselineEnd = baseEnd,
                            wallNormal = extNormal,
                            offsetCm = level1OffsetCm,
                            wallThicknessCm = thickness,
                            segments = level1Segments
                        )
                    )
                }

                // Уровень 2: Габаритный размер всей стены от наружных граней
                result.add(
                    WallDimensionChain(
                        wallId = wall.id,
                        level = 2,
                        baselineStart = baseStart,
                        baselineEnd = baseEnd,
                        wallNormal = extNormal,
                        offsetCm = level2OffsetCm,
                        wallThicknessCm = thickness,
                        segments = listOf(
                            DimensionSegment(
                                startWorld = baseStart,
                                endWorld = baseEnd,
                                lengthCm = outerLen,
                                isOpening = false
                            )
                        )
                    )
                )
            }
        }

        return result
    }

    // =========================================================================
    // 2. ЧИСТЫЕ ВНУТРЕННИЕ РАЗМЕРЫ КОМНАТ (Planner 5D / СПДС)
    // =========================================================================

    /**
     * Рассчитывает чистые внутренние размеры между гранями стен внутри каждого помещения.
     * Внутренние углы рассчитываются через пересечение внутренних линий граней стен,
     * поэтому изменение толщины вертикальной стены не влияет на вертикальный размер комнаты.
     */
    fun calculateRoomClearDimensions(
        rooms: Collection<Room>,
        vertices: Map<String, WallVertex>,
        walls: Map<String, WallSegment>,
        items: Collection<PlacedItem> = emptyList()
    ): List<RoomClearDimension> {
        val result = mutableListOf<RoomClearDimension>()

        for (room in rooms) {
            val poly = room.getPolygonPoints(vertices)
            val n = poly.size
            if (n < 3) continue

            val centroid = PolygonMath.calculateCentroid(poly)

            // 1. Для каждого ребра определяем толщину стены, направление и внутреннюю нормаль
            data class RoomEdgeInfo(
                val pStart: Point2D,
                val pEnd: Point2D,
                val edgeDir: Point2D,
                val innerNormal: Point2D,
                val thickness: Float
            )

            val edgeInfos = mutableListOf<RoomEdgeInfo>()
            for (i in 0 until n) {
                val p1 = poly[i]
                val p2 = poly[(i + 1) % n]
                val edgeVec = p2 - p1
                val edgeLen = edgeVec.length()
                val edgeDir = if (edgeLen > 0.01f) edgeVec / edgeLen else Point2D(1f, 0f)
                val norm = edgeDir.normal()
                val mid = Point2D((p1.x + p2.x) * 0.5f, (p1.y + p2.y) * 0.5f)
                val innerNorm = if (norm.dot(centroid - mid) > 0) norm else norm * -1f

                val v1Id = room.vertexIds[i]
                val v2Id = room.vertexIds[(i + 1) % n]
                val wall = walls.values.firstOrNull { it.hasVertex(v1Id) && it.hasVertex(v2Id) }
                val thick = wall?.thicknessCm ?: 20f

                edgeInfos.add(RoomEdgeInfo(p1, p2, edgeDir, innerNorm, thick))
            }

            // 2. Рассчитываем точные внутренние углы комнаты через пересечение внутренних линий граней
            val innerCorners = Array(n) { Point2D.Zero }
            for (i in 0 until n) {
                val prev = edgeInfos[(i - 1 + n) % n]
                val curr = edgeInfos[i]
                val vPt = poly[i]

                val lPrevPt = vPt + prev.innerNormal * (prev.thickness * 0.5f)
                val lCurrPt = vPt + curr.innerNormal * (curr.thickness * 0.5f)

                val pt = intersectLines(lPrevPt, prev.edgeDir, lCurrPt, curr.edgeDir)
                if (pt != null && pt.distanceTo(vPt) <= (prev.thickness + curr.thickness) * 1.5f) {
                    innerCorners[i] = pt
                } else {
                    innerCorners[i] = vPt + (prev.innerNormal * (prev.thickness * 0.5f) + curr.innerNormal * (curr.thickness * 0.5f)) * 0.5f
                }
            }

            // 3. Чистый размер каждого ребра равен расстоянию между точными внутренними углами
            data class EdgeCandidate(
                val dim: RoomClearDimension,
                val edgeDir: Point2D,
                val midEdge: Point2D,
                val clearLength: Float,
                var obstructionScore: Float
            )

            val candidates = mutableListOf<EdgeCandidate>()

            for (i in 0 until n) {
                val edge = edgeInfos[i]
                val clearStart = innerCorners[i]
                val clearEnd = innerCorners[(i + 1) % n]
                val clearLength = clearStart.distanceTo(clearEnd)
                if (clearLength < 20f) continue

                val midEdge = Point2D((clearStart.x + clearEnd.x) * 0.5f, (clearStart.y + clearEnd.y) * 0.5f)

                // Проверка перекрытия мебелью
                val insetCm = 25f
                val lineStart = clearStart + edge.innerNormal * insetCm
                val lineEnd = clearEnd + edge.innerNormal * insetCm
                val dimSegment = LineSegment(lineStart, lineEnd)

                var score = 0f
                for (it in items) {
                    val itDist = it.position.distanceTo(dimSegment.closestPointTo(it.position))
                    val itRadius = max(it.widthCm, it.depthCm) * 0.5f
                    if (itDist < itRadius + 15f) {
                        score += if (it.wallId != null) 10f else 100f
                    }
                }

                val dim = RoomClearDimension(
                    roomId = room.id,
                    innerStart = clearStart,
                    innerEnd = clearEnd,
                    clearLengthCm = clearLength,
                    normalIntoRoom = edge.innerNormal
                )

                candidates.add(
                    EdgeCandidate(
                        dim = dim,
                        edgeDir = edge.edgeDir,
                        midEdge = midEdge,
                        clearLength = clearLength,
                        obstructionScore = score
                    )
                )
            }

            // Deduplicate opposing parallel walls with identical dimension
            val suppressed = mutableSetOf<Int>()

            for (i in candidates.indices) {
                if (i in suppressed) continue
                val c1 = candidates[i]

                for (j in (i + 1) until candidates.size) {
                    if (j in suppressed) continue
                    val c2 = candidates[j]

                    val isParallel = abs(abs(c1.edgeDir.dot(c2.edgeDir)) - 1f) < 0.08f
                    val isOpposing = c1.dim.normalIntoRoom.dot(c2.dim.normalIntoRoom) < -0.7f
                    val sameLength = abs(c1.clearLength - c2.clearLength) < 3f

                    if (isParallel && isOpposing && sameLength) {
                        // Pick the cleaner wall without furniture
                        if (c1.obstructionScore < c2.obstructionScore) {
                            suppressed.add(j)
                        } else if (c2.obstructionScore < c1.obstructionScore) {
                            suppressed.add(i)
                        } else {
                            // Equal score: prefer top for horizontal, left for vertical
                            val isHorizontal = abs(c1.edgeDir.y) < 0.35f
                            if (isHorizontal) {
                                if (c1.midEdge.y <= c2.midEdge.y) suppressed.add(j) else suppressed.add(i)
                            } else {
                                if (c1.midEdge.x <= c2.midEdge.x) suppressed.add(j) else suppressed.add(i)
                            }
                        }
                    }
                }
            }

            for (i in candidates.indices) {
                if (i !in suppressed) {
                    result.add(candidates[i].dim)
                }
            }
        }

        return result
    }

    // =========================================================================
    // 3. НАПРАВЛЯЮЩИЕ ПРИВЯЗКИ МЕБЕЛИ К СТЕНАМ (Planner 5D)
    // =========================================================================

    /**
     * Рассчитывает расстояния от 4 граней выбранной мебели до ближайших стен помещения.
     */
    fun calculateFurnitureDistanceGuides(
        item: PlacedItem,
        walls: Collection<WallSegment>,
        vertices: Map<String, WallVertex>,
        maxSearchDistanceCm: Float = 350f
    ): List<FurnitureDistanceGuide> {
        val halfW = item.widthCm * 0.5f
        val halfD = item.depthCm * 0.5f
        val center = item.position
        val rot = item.rotationDegrees

        // Calculate rotated corners and edges of the furniture polygon
        val localCorners = listOf(
            Point2D(-halfW, -halfD),
            Point2D(halfW, -halfD),
            Point2D(halfW, halfD),
            Point2D(-halfW, halfD)
        )
        val worldCorners = localCorners.map { it.rotate(rot) + center }
        val itemEdges = listOf(
            Pair(worldCorners[0], worldCorners[1]),
            Pair(worldCorners[1], worldCorners[2]),
            Pair(worldCorners[2], worldCorners[3]),
            Pair(worldCorners[3], worldCorners[0])
        )

        fun raySegmentIntersection(
            rayOrigin: Point2D,
            rayDir: Point2D,
            segStart: Point2D,
            segEnd: Point2D
        ): Float? {
            val s = segEnd - segStart
            val rCrossS = rayDir.cross(s)
            if (abs(rCrossS) < 1e-5f) return null
            val qMinusP = segStart - rayOrigin
            val t = qMinusP.cross(s) / rCrossS
            val u = qMinusP.cross(rayDir) / rCrossS
            return if (t > 0.001f && u in 0f..1f) t else null
        }

        // 4 Strictly Orthogonal Directions (Up, Right, Down, Left)
        val cardinalDirs = listOf(
            Point2D(0f, -1f), // Вверх (вертикально)
            Point2D(1f, 0f),  // Вправо (горизонтально)
            Point2D(0f, 1f),  // Вниз (вертикально)
            Point2D(-1f, 0f)  // Влево (горизонтально)
        )

        val wallSegments = walls.mapNotNull { w ->
            w.toLineSegment(vertices)?.let { line -> Pair(w.id, line) }
        }

        val guides = mutableListOf<FurnitureDistanceGuide>()

        for (dir in cardinalDirs) {
            // Find exact point where the ray from center exits the furniture boundary
            val tItem = itemEdges.mapNotNull { (eStart, eEnd) ->
                raySegmentIntersection(center, dir, eStart, eEnd)
            }.minOrNull() ?: 0f
            val itemBoundaryPt = center + dir * tItem

            var closestDist = maxSearchDistanceCm
            var closestWallPt: Point2D? = null
            var closestWallId: String? = null

            for ((wId, line) in wallSegments) {
                val t = raySegmentIntersection(itemBoundaryPt, dir, line.start, line.end)
                if (t != null && t in 1f..closestDist) {
                    closestDist = t
                    closestWallPt = itemBoundaryPt + dir * t
                    closestWallId = wId
                }
            }

            if (closestWallPt != null && closestWallId != null && closestDist >= 5f) {
                guides.add(
                    FurnitureDistanceGuide(
                        itemPoint = itemBoundaryPt,
                        wallPoint = closestWallPt,
                        distanceCm = closestDist,
                        wallId = closestWallId,
                        direction = dir
                    )
                )
            }
        }

        return guides
    }

    // =========================================================================
    // 4. ОТРИСОВКА ПО ГОСТ 2.307-2011 (Векторный рендеринг DrawScope)
    // =========================================================================

    /**
     * Отрисовывает размерные цепочки строго по требованиям ГОСТ 2.307-2011:
     * - Сплошная тонкая размерная линия (0.9-1.0 px)
     * - Выносные линии от видимого контура стены с выходом за концы стрелок/засечек на 2-3 мм
     * - Стрелки ГОСТ 20° (длина 5.5-6s) или засечки под 45°
     * - Размерные числа НАД размерной линией на высоте 1 мм (в мм без добавления единиц)
     */
    fun drawWallDimensionChains(
        drawScope: DrawScope,
        chains: List<WallDimensionChain>,
        editorState: CadEditorState,
        viewport: CadViewportState,
        textMeasurer: TextMeasurer
    ) {
        with(drawScope) {
            val terminator = editorState.dimensionTerminator

            for (chain in chains) {
                val isWallSelected = chain.wallId == editorState.selectedWallId
                val chainColor = if (isWallSelected) DimensionSelectedColor else DimensionLineColor
                val witnessColor = if (isWallSelected) DimensionSelectedColor.copy(alpha = 0.85f) else ExtensionLineColor

                // Концы размерной линии в мировых координатах
                val dimStartWorld = chain.baselineStart + chain.wallNormal * chain.offsetCm
                val dimEndWorld = chain.baselineEnd + chain.wallNormal * chain.offsetCm

                val dimStartScreen = viewport.worldToScreen(dimStartWorld)
                val dimEndScreen = viewport.worldToScreen(dimEndWorld)

                val dirScreen = dimEndScreen - dimStartScreen
                val chainScreenLen = dirScreen.getDistance()
                if (chainScreenLen < 20f) continue

                val unitDir = dirScreen / chainScreenLen
                val normalScreen = Offset(-unitDir.y, unitDir.x)

                // 1. Непрерывная тонкая размерная линия по ГОСТ 2.307
                drawLine(
                    color = chainColor,
                    start = dimStartScreen,
                    end = dimEndScreen,
                    strokeWidth = 0.95f
                )

                // 2. Отрисовка каждого сегмента с выносными линиями, стрелками/засечками и числами
                val halfThick = chain.wallThicknessCm * 0.5f

                for (seg in chain.segments) {
                    val segStartW = seg.startWorld + chain.wallNormal * chain.offsetCm
                    val segEndW = seg.endWorld + chain.wallNormal * chain.offsetCm

                    val sScreen = viewport.worldToScreen(segStartW)
                    val eScreen = viewport.worldToScreen(segEndW)

                    // Точки видимого контура стены (откуда начинаются выносные линии)
                    val contourStartW = seg.startWorld + chain.wallNormal * halfThick
                    val contourEndW = seg.endWorld + chain.wallNormal * halfThick

                    val contourStartScreen = viewport.worldToScreen(contourStartW)
                    val contourEndScreen = viewport.worldToScreen(contourEndW)

                    // Выносные линии по ГОСТ 2.307:
                    // Начинаются от видимого контура стены и выходят за размерную линию на 2-3 мм (EXTENSION_OVERSHOOT_PX)
                    val extDirS = (sScreen - contourStartScreen).let {
                        val d = it.getDistance()
                        if (d > 1f) it / d else normalScreen
                    }
                    val witnessStartS = contourStartScreen + extDirS * EXTENSION_CLEARANCE_PX
                    val witnessEndS = sScreen + extDirS * EXTENSION_OVERSHOOT_PX

                    val extDirE = (eScreen - contourEndScreen).let {
                        val d = it.getDistance()
                        if (d > 1f) it / d else normalScreen
                    }
                    val witnessStartE = contourEndScreen + extDirE * EXTENSION_CLEARANCE_PX
                    val witnessEndE = eScreen + extDirE * EXTENSION_OVERSHOOT_PX

                    drawLine(
                        color = witnessColor,
                        start = witnessStartS,
                        end = witnessEndS,
                        strokeWidth = 0.8f
                    )
                    drawLine(
                        color = witnessColor,
                        start = witnessStartE,
                        end = witnessEndE,
                        strokeWidth = 0.8f
                    )

                    // Ограничение стрелками / засечками по ГОСТ 2.307
                    drawSegmentTerminators(
                        drawScope = this,
                        sScreen = sScreen,
                        eScreen = eScreen,
                        unitDir = unitDir,
                        normalScreen = normalScreen,
                        color = chainColor,
                        terminator = terminator,
                        chainLevel = chain.level
                    )

                    // Размерное число НАД размерной линией по ГОСТ 2.307 п. 5.31
                    val segScreenLen = (eScreen - sScreen).getDistance()
                    if (segScreenLen > 18f) {
                        val midScreen = Offset((sScreen.x + eScreen.x) * 0.5f, (sScreen.y + eScreen.y) * 0.5f)
                        val textValue = formatGostLength(seg.lengthCm, editorState.measurementUnit)

                        drawGostDimensionNumber(
                            drawScope = this,
                            text = textValue,
                            midScreen = midScreen,
                            unitDir = unitDir,
                            normalScreen = normalScreen,
                            segScreenLen = segScreenLen,
                            isOpening = seg.isOpening,
                            isSelected = isWallSelected,
                            textMeasurer = textMeasurer
                        )
                    }
                }
            }
        }
    }

    /**
     * Отрисовывает ограничители на концах размерного отрезка по ГОСТ 2.307-2011:
     * - Стрелки ГОСТ с углом при вершине 20° и длиной 5.5-6s.
     * - При недостатке места (сегмент < 36 px) стрелки выносятся наружу остриями к выносным линиям.
     * - Или архитектурные засечки 45° по СПДС ГОСТ 21.501.
     */
    private fun drawSegmentTerminators(
        drawScope: DrawScope,
        sScreen: Offset,
        eScreen: Offset,
        unitDir: Offset,
        normalScreen: Offset,
        color: Color,
        terminator: GostDimensionTerminator,
        chainLevel: Int
    ) {
        val segLen = (eScreen - sScreen).getDistance()

        val useArrows = when (terminator) {
            GostDimensionTerminator.ARROWS_GOST_20 -> true
            GostDimensionTerminator.ARCHITECTURAL_TICKS_45 -> false
        }

        if (useArrows) {
            if (segLen >= 36f) {
                // Стрелки внутри отрезка: острия касаются выносных линий, направлены друг к другу
                drawGostArrow(drawScope, apex = sScreen, direction = unitDir, color = color)
                drawGostArrow(drawScope, apex = eScreen, direction = unitDir * -1f, color = color)
            } else {
                // ГОСТ 2.307 п. 5.19: при недостатке места стрелки наносят с внешней стороны
                drawGostArrow(drawScope, apex = sScreen, direction = unitDir * -1f, color = color)
                drawGostArrow(drawScope, apex = eScreen, direction = unitDir, color = color)

                // Хвостики размерной линии под наружными стрелками
                drawScope.drawLine(
                    color = color,
                    start = sScreen,
                    end = sScreen - unitDir * (ARROW_LENGTH_PX + 3f),
                    strokeWidth = 0.95f
                )
                drawScope.drawLine(
                    color = color,
                    start = eScreen,
                    end = eScreen + unitDir * (ARROW_LENGTH_PX + 3f),
                    strokeWidth = 0.95f
                )
            }
        } else {
            // Архитектурные засечки под 45° по ГОСТ 21.501 (СПДС)
            drawGostTick(drawScope, sScreen, unitDir, normalScreen, color)
            drawGostTick(drawScope, eScreen, unitDir, normalScreen, color)
        }
    }

    /**
     * Отрисовывает острую замкнутую стрелку по ГОСТ 2.307-2011:
     * - Острие (apex) ровно в точке пересечения с выносной линией
     * - Длина L = 14 px (5.5-6s)
     * - Угол при вершине 20°
     */
    private fun drawGostArrow(
        drawScope: DrawScope,
        apex: Offset,
        direction: Offset,
        color: Color,
        arrowLength: Float = ARROW_LENGTH_PX,
        angleDeg: Float = ARROW_APEX_ANGLE_DEG
    ) {
        with(drawScope) {
            val halfAngleRad = Math.toRadians((angleDeg * 0.5f).toDouble()).toFloat()
            val halfWidth = arrowLength * tan(halfAngleRad)

            val perp = Offset(-direction.y, direction.x)
            val baseCenter = apex + direction * arrowLength

            val leftCorner = baseCenter + perp * halfWidth
            val rightCorner = baseCenter - perp * halfWidth

            val path = Path().apply {
                moveTo(apex.x, apex.y)
                lineTo(leftCorner.x, leftCorner.y)
                lineTo(rightCorner.x, rightCorner.y)
                close()
            }

            drawPath(path, color = color, style = Fill)
        }
    }

    /**
     * Отрисовывает классическую архитектурную засечку под 45° по ГОСТ 21.501 (СПДС).
     */
    private fun drawGostTick(
        drawScope: DrawScope,
        pos: Offset,
        unitDir: Offset,
        normalScreen: Offset,
        color: Color,
        tickLength: Float = TICK_LENGTH_PX
    ) {
        with(drawScope) {
            // Вектор с наклоном 45° вправо относительно размерной линии: (unitDir + normalScreen)
            val tickVec = (unitDir + normalScreen) * (tickLength * 0.35f)
            drawLine(
                color = color,
                start = pos - tickVec,
                end = pos + tickVec,
                strokeWidth = 1.8f,
                cap = StrokeCap.Round
            )
        }
    }

    /**
     * Отрисовывает размерное число строго по правилам ГОСТ 2.307-2011:
     * 1. Располагается НАД размерной линией (ближе к середине отрезка) на высоте около 1 мм (3.8 px).
     * 2. Непрерывная размерная линия сохраняется под числом.
     * 3. Ориентация по ГОСТ 2.307 п. 5.31: числа читаются слева направо и снизу вверх.
     * 4. Линейные размеры указываются в миллиметрах без знака единицы измерения.
     */
    private fun drawGostDimensionNumber(
        drawScope: DrawScope,
        text: String,
        midScreen: Offset,
        unitDir: Offset,
        normalScreen: Offset,
        segScreenLen: Float,
        isOpening: Boolean,
        isSelected: Boolean,
        textMeasurer: TextMeasurer
    ) {
        with(drawScope) {
            var angleRad = atan2(unitDir.y, unitDir.x)
            var angleDeg = Math.toDegrees(angleRad.toDouble()).toFloat()

            // Нормализация угла по ГОСТ 2.307: диапазон (-90°, +90°] для удобства чтения снизу и справа
            if (angleDeg > 90f || angleDeg <= -90f) {
                angleDeg += 180f
                if (angleDeg > 180f) angleDeg -= 360f
            }

            val fontColor = when {
                isSelected -> DimensionSelectedColor
                isOpening -> DimensionOpeningColor
                else -> DimensionTextColor
            }

            val measured = textMeasurer.measure(
                text,
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif,
                    color = fontColor
                )
            )

            val textW = measured.size.width.toFloat()
            val textH = measured.size.height.toFloat()

            drawContext.canvas.save()
            drawContext.canvas.translate(midScreen.x, midScreen.y)
            drawContext.canvas.rotate(angleDeg)

            // Проверяем, помещается ли число между выносными линиями
            val isCrowded = segScreenLen < (textW + 6f)

            if (!isCrowded) {
                // Классическое размещение по ГОСТ 2.307:
                // Строго над размерной линией на высоте 1 мм (TEXT_HEIGHT_ABOVE_LINE_PX), центрировано
                val textX = -textW * 0.5f
                val textY = -textH - TEXT_HEIGHT_ABOVE_LINE_PX

                // Тончайшая маскирующая подложка для читаемости поверх фоновой сетки
                drawRect(
                    color = Color.White.copy(alpha = 0.92f),
                    topLeft = Offset(textX - 2f, textY - 1f),
                    size = Size(textW + 4f, textH + 2f)
                )

                drawText(
                    measured,
                    topLeft = Offset(textX, textY)
                )
            } else {
                // ГОСТ 2.307 п. 5.32: при недостатке места над линией — смещение с полочкой-выноской
                val shelfW = textW + 8f
                val shelfY = -TEXT_HEIGHT_ABOVE_LINE_PX - 2f
                val shelfX = 6f

                // Полочка линии-выноски
                drawLine(
                    color = fontColor.copy(alpha = 0.8f),
                    start = Offset(0f, 0f),
                    end = Offset(shelfX, shelfY),
                    strokeWidth = 0.8f
                )
                drawLine(
                    color = fontColor.copy(alpha = 0.8f),
                    start = Offset(shelfX, shelfY),
                    end = Offset(shelfX + shelfW, shelfY),
                    strokeWidth = 0.8f
                )

                drawText(
                    measured,
                    topLeft = Offset(shelfX + 4f, shelfY - textH - 1f)
                )
            }

            drawContext.canvas.restore()
        }
    }

    /**
     * Отрисовывает чистые внутренние размеры между гранями стен помещений (Planner 5D).
     */
    fun drawRoomClearDimensions(
        drawScope: DrawScope,
        clearDims: List<RoomClearDimension>,
        editorState: CadEditorState,
        viewport: CadViewportState,
        textMeasurer: TextMeasurer
    ) {
        with(drawScope) {
            val terminator = editorState.dimensionTerminator

            for (cd in clearDims) {
                val isRoomSelected = cd.roomId == editorState.selectedRoomId
                val color = if (isRoomSelected) DimensionSelectedColor else InteriorDimensionColor

                // Отступ размерной линии внутрь комнаты от чистой грани стены (не менее 10 мм на экране)
                val insetCm = max(25f, MIN_CONTOUR_OFFSET_PX / viewport.zoom)
                val offsetVec = cd.normalIntoRoom * insetCm

                val sWorld = cd.innerStart + offsetVec
                val eWorld = cd.innerEnd + offsetVec

                val sScreen = viewport.worldToScreen(sWorld)
                val eScreen = viewport.worldToScreen(eWorld)

                val dirScreen = eScreen - sScreen
                val lenScreen = dirScreen.getDistance()
                if (lenScreen < 26f) continue

                val unitDir = dirScreen / lenScreen
                val normalScreen = Offset(-unitDir.y, unitDir.x)

                // Выносные линии от углов помещения
                val cornerSScreen = viewport.worldToScreen(cd.innerStart)
                val cornerEScreen = viewport.worldToScreen(cd.innerEnd)

                val witnessOvershoot = normalScreen * EXTENSION_OVERSHOOT_PX
                drawLine(color.copy(alpha = 0.5f), cornerSScreen, sScreen + witnessOvershoot, strokeWidth = 0.8f)
                drawLine(color.copy(alpha = 0.5f), cornerEScreen, eScreen + witnessOvershoot, strokeWidth = 0.8f)

                // Тонкая размерная линия
                drawLine(
                    color = color.copy(alpha = 0.85f),
                    start = sScreen,
                    end = eScreen,
                    strokeWidth = 0.95f
                )

                // Ограничители
                drawSegmentTerminators(
                    drawScope = this,
                    sScreen = sScreen,
                    eScreen = eScreen,
                    unitDir = unitDir,
                    normalScreen = normalScreen,
                    color = color,
                    terminator = terminator,
                    chainLevel = 1
                )

                // Размерное число
                val midScreen = Offset((sScreen.x + eScreen.x) * 0.5f, (sScreen.y + eScreen.y) * 0.5f)
                val textValue = formatGostLength(cd.clearLengthCm, editorState.measurementUnit)

                drawGostDimensionNumber(
                    drawScope = this,
                    text = textValue,
                    midScreen = midScreen,
                    unitDir = unitDir,
                    normalScreen = normalScreen,
                    segScreenLen = lenScreen,
                    isOpening = false,
                    isSelected = isRoomSelected,
                    textMeasurer = textMeasurer
                )
            }
        }
    }

    /**
     * Отрисовывает динамические направляющие расстояний от мебели до стен (Planner 5D).
     */
    fun drawFurnitureDistanceGuides(
        drawScope: DrawScope,
        guides: List<FurnitureDistanceGuide>,
        editorState: CadEditorState,
        viewport: CadViewportState,
        textMeasurer: TextMeasurer
    ) {
        with(drawScope) {
            for (guide in guides) {
                val sScreen = viewport.worldToScreen(guide.itemPoint)
                val eScreen = viewport.worldToScreen(guide.wallPoint)

                val dirScreen = eScreen - sScreen
                val lenScreen = dirScreen.getDistance()
                if (lenScreen < 8f) continue

                // Штриховая направляющая линия
                drawLine(
                    color = FurnitureGuideColor,
                    start = sScreen,
                    end = eScreen,
                    strokeWidth = 1.3f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                )

                // Точки засечек на концах
                drawCircle(FurnitureGuideColor, 3f, sScreen)
                drawCircle(FurnitureGuideColor, 3f, eScreen)

                // Значение расстояния по ГОСТ
                val midScreen = Offset((sScreen.x + eScreen.x) * 0.5f, (sScreen.y + eScreen.y) * 0.5f)
                val textValue = formatGostLength(guide.distanceCm, editorState.measurementUnit)

                val measured = textMeasurer.measure(
                    textValue,
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )

                val badgeW = measured.size.width + 10f
                val badgeH = measured.size.height + 4f
                val badgeTopLeft = Offset(midScreen.x - badgeW * 0.5f, midScreen.y - badgeH * 0.5f)

                drawRoundRect(
                    color = Color(0xEE0284C7),
                    topLeft = badgeTopLeft,
                    size = Size(badgeW, badgeH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                )
                drawText(measured, topLeft = Offset(badgeTopLeft.x + 5f, badgeTopLeft.y + 2f))
            }
        }
    }

    /**
     * Форматирует размер в соответствии со стандартами ГОСТ 2.307-2011:
     * - MILLIMETERS: целые миллиметры без знака единицы измерения ("3200", "1500", "450")
     * - CENTIMETERS: "320 см"
     * - METERS: "3.20 м" (2 знака после запятой)
     */
    fun formatGostLength(lengthCm: Float, unit: MeasurementUnit): String = when (unit) {
        MeasurementUnit.MILLIMETERS -> "${(lengthCm * 10f).roundToInt()}"
        MeasurementUnit.CENTIMETERS -> "${lengthCm.roundToInt()} см"
        MeasurementUnit.METERS -> {
            val meters = lengthCm / 100f
            val rounded = (meters * 100).roundToInt() / 100f
            "$rounded м"
        }
    }
}
