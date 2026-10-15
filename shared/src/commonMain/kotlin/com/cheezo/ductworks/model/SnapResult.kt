package com.cheezo.ductworks.model

import kotlin.math.abs
import kotlin.math.round

enum class SnapType {
    NONE,
    GRID,
    VERTEX,
    WALL_EDGE,
    ORTHO_AXIS,
    ALIGNMENT_GUIDE
}

data class GuideLine(
    val start: Point2D,
    val end: Point2D,
    val isHorizontal: Boolean
)

data class SnapResult(
    val point: Point2D,
    val type: SnapType,
    val snappedVertexId: String? = null,
    val snappedWallId: String? = null,
    val guideLines: List<GuideLine> = emptyList()
)

object CadSnapper {

    /**
     * Snaps a value to a grid step.
     */
    fun snapToGrid(value: Float, step: Float): Float {
        if (step <= 0f) return value
        return round(value / step) * step
    }

    fun snapPointToGrid(point: Point2D, step: Float): Point2D =
        Point2D(snapToGrid(point.x, step), snapToGrid(point.y, step))

    /**
     * Resolves snapping against vertices, wall edges, ortho angles, and grid according to AutoCAD standards.
     * Rules:
     * 1. When Ortho is ON (F8) and an originPoint is provided (drawing/dragging):
     *    - Direct hover over an existing vertex (Endpoint OSNAP, excluding originPoint) snaps to that vertex
     *      to allow closing room loops and connecting to corners.
     *    - Movement in space is strictly locked to 0°/90°/180°/270° axes (horizontal or vertical).
     *    - Along the ortho axis, if it intersects an existing wall, snaps perpendicularly to that wall.
     *    - Along the ortho axis, if it aligns with an existing vertex's X or Y (O-Track), snaps and shows alignment guides.
     *    - Along the ortho axis, snaps to grid step if enabled, or allows continuous float coordinate.
     *    - Never snaps to arbitrary non-perpendicular wall edges that would bend the ortho angle.
     * 2. When Ortho is OFF:
     *    - Movement is free in 360 degrees.
     *    - If Snapping is ON: snaps to nearby vertices, wall edges, alignment guides, and grid.
     *    - If Snapping is OFF: raw continuous float coordinates.
     * 3. When originPoint is null (hovering):
     *    - If Snapping is ON: snaps to vertex, wall edge, or grid.
     *    - If Snapping is OFF: raw continuous float coordinates.
     */
    fun resolveSnap(
        rawPoint: Point2D,
        originPoint: Point2D?,
        vertices: Collection<WallVertex>,
        walls: Collection<WallSegment>,
        vertexMap: Map<String, WallVertex>,
        gridStepCm: Float,
        isGridSnapEnabled: Boolean,
        isOrthoEnabled: Boolean,
        vertexSnapRadiusCm: Float = 25f,
        wallSnapRadiusCm: Float = 15f
    ): SnapResult {
        // =========================================================================
        // CASE A: ORTHO MODE ACTIVE FROM BASE POINT (Drawing wall or ortho dragging)
        // =========================================================================
        if (originPoint != null && isOrthoEnabled) {
            // A1. Direct Endpoint / Vertex Snap Override (AutoCAD standard: hover over corner)
            if (isGridSnapEnabled) {
                var nearestVertex: WallVertex? = null
                var minVertexDist = Float.MAX_VALUE
                for (v in vertices) {
                    // Do not snap back onto the starting point itself
                    if (v.position.distanceTo(originPoint) < 1f) continue
                    val d = rawPoint.distanceTo(v.position)
                    if (d < vertexSnapRadiusCm && d < minVertexDist) {
                        minVertexDist = d
                        nearestVertex = v
                    }
                }
                if (nearestVertex != null) {
                    return SnapResult(
                        point = nearestVertex.position,
                        type = SnapType.VERTEX,
                        snappedVertexId = nearestVertex.id
                    )
                }
            }

            // A2. Calculate strictly orthogonal axis (0°, 90°, 180°, 270°)
            val dx = rawPoint.x - originPoint.x
            val dy = rawPoint.y - originPoint.y
            val isHoriz = abs(dx) >= abs(dy)

            val orthoGuide = if (isHoriz) {
                GuideLine(
                    start = Point2D(originPoint.x - 4000f, originPoint.y),
                    end = Point2D(originPoint.x + 4000f, originPoint.y),
                    isHorizontal = true
                )
            } else {
                GuideLine(
                    start = Point2D(originPoint.x, originPoint.y - 4000f),
                    end = Point2D(originPoint.x, originPoint.y + 4000f),
                    isHorizontal = false
                )
            }

            if (isGridSnapEnabled) {
                // A3. Perpendicular snap to an intersecting wall along the ortho axis
                if (isHoriz) {
                    var nearestWallSnap: Pair<WallSegment, Float>? = null
                    var minWallDist = Float.MAX_VALUE

                    for (w in walls) {
                        val seg = w.toLineSegment(vertexMap) ?: continue
                        val minY = minOf(seg.start.y, seg.end.y)
                        val maxY = maxOf(seg.start.y, seg.end.y)
                        if (originPoint.y in minY..maxY && abs(seg.start.y - seg.end.y) > 0.001f) {
                            val t = (originPoint.y - seg.start.y) / (seg.end.y - seg.start.y)
                            val xInt = seg.start.x + t * (seg.end.x - seg.start.x)
                            val dist = abs(rawPoint.x - xInt)
                            val isForward = (dx == 0f) || ((xInt - originPoint.x) * dx >= -1f)
                            if (isForward && dist < wallSnapRadiusCm && dist < minWallDist) {
                                minWallDist = dist
                                nearestWallSnap = Pair(w, xInt)
                            }
                        }
                    }
                    if (nearestWallSnap != null) {
                        return SnapResult(
                            point = Point2D(nearestWallSnap.second, originPoint.y),
                            type = SnapType.WALL_EDGE,
                            snappedWallId = nearestWallSnap.first.id,
                            guideLines = listOf(orthoGuide)
                        )
                    }
                } else {
                    var nearestWallSnap: Pair<WallSegment, Float>? = null
                    var minWallDist = Float.MAX_VALUE

                    for (w in walls) {
                        val seg = w.toLineSegment(vertexMap) ?: continue
                        val minX = minOf(seg.start.x, seg.end.x)
                        val maxX = maxOf(seg.start.x, seg.end.x)
                        if (originPoint.x in minX..maxX && abs(seg.start.x - seg.end.x) > 0.001f) {
                            val t = (originPoint.x - seg.start.x) / (seg.end.x - seg.start.x)
                            val yInt = seg.start.y + t * (seg.end.y - seg.start.y)
                            val dist = abs(rawPoint.y - yInt)
                            val isForward = (dy == 0f) || ((yInt - originPoint.y) * dy >= -1f)
                            if (isForward && dist < wallSnapRadiusCm && dist < minWallDist) {
                                minWallDist = dist
                                nearestWallSnap = Pair(w, yInt)
                            }
                        }
                    }
                    if (nearestWallSnap != null) {
                        return SnapResult(
                            point = Point2D(originPoint.x, nearestWallSnap.second),
                            type = SnapType.WALL_EDGE,
                            snappedWallId = nearestWallSnap.first.id,
                            guideLines = listOf(orthoGuide)
                        )
                    }
                }

                // A4. O-Track (Object Snap Tracking): align coordinate with existing vertices
                if (isHoriz) {
                    var bestX: Float? = null
                    var bestDist = Float.MAX_VALUE
                    var alignVertexPos: Point2D? = null

                    for (v in vertices) {
                        if (v.position.distanceTo(originPoint) < 1f) continue
                        val dist = abs(rawPoint.x - v.position.x)
                        val isForward = (dx == 0f) || ((v.position.x - originPoint.x) * dx >= -1f)
                        if (isForward && dist < 18f && dist < bestDist) {
                            bestDist = dist
                            bestX = v.position.x
                            alignVertexPos = v.position
                        }
                    }
                    if (bestX != null && alignVertexPos != null) {
                        val snappedPt = Point2D(bestX, originPoint.y)
                        val trackGuide = GuideLine(start = alignVertexPos, end = snappedPt, isHorizontal = false)
                        return SnapResult(
                            point = snappedPt,
                            type = SnapType.ALIGNMENT_GUIDE,
                            guideLines = listOf(orthoGuide, trackGuide)
                        )
                    }
                } else {
                    var bestY: Float? = null
                    var bestDist = Float.MAX_VALUE
                    var alignVertexPos: Point2D? = null

                    for (v in vertices) {
                        if (v.position.distanceTo(originPoint) < 1f) continue
                        val dist = abs(rawPoint.y - v.position.y)
                        val isForward = (dy == 0f) || ((v.position.y - originPoint.y) * dy >= -1f)
                        if (isForward && dist < 18f && dist < bestDist) {
                            bestDist = dist
                            bestY = v.position.y
                            alignVertexPos = v.position
                        }
                    }
                    if (bestY != null && alignVertexPos != null) {
                        val snappedPt = Point2D(originPoint.x, bestY)
                        val trackGuide = GuideLine(start = alignVertexPos, end = snappedPt, isHorizontal = true)
                        return SnapResult(
                            point = snappedPt,
                            type = SnapType.ALIGNMENT_GUIDE,
                            guideLines = listOf(orthoGuide, trackGuide)
                        )
                    }
                }

                // A5. Grid snap along the ortho axis
                val snappedPt = if (isHoriz) {
                    Point2D(snapToGrid(rawPoint.x, gridStepCm), originPoint.y)
                } else {
                    Point2D(originPoint.x, snapToGrid(rawPoint.y, gridStepCm))
                }
                return SnapResult(
                    point = snappedPt,
                    type = SnapType.ORTHO_AXIS,
                    guideLines = listOf(orthoGuide)
                )
            } else {
                // Snapping disabled: strict continuous Ortho
                val orthoPt = if (isHoriz) {
                    Point2D(rawPoint.x, originPoint.y)
                } else {
                    Point2D(originPoint.x, rawPoint.y)
                }
                return SnapResult(
                    point = orthoPt,
                    type = SnapType.ORTHO_AXIS,
                    guideLines = listOf(orthoGuide)
                )
            }
        }

        // =========================================================================
        // CASE B: FREE ANGLE MODE (Ortho is OFF) OR CURSOR HOVERING (originPoint is null)
        // =========================================================================
        if (isGridSnapEnabled) {
            // B1. Snap to existing vertex (Endpoint OSNAP)
            var nearestVertex: WallVertex? = null
            var minVertexDist = Float.MAX_VALUE
            for (v in vertices) {
                if (originPoint != null && v.position.distanceTo(originPoint) < 1f) continue
                val d = rawPoint.distanceTo(v.position)
                if (d < vertexSnapRadiusCm && d < minVertexDist) {
                    minVertexDist = d
                    nearestVertex = v
                }
            }
            if (nearestVertex != null) {
                return SnapResult(
                    point = nearestVertex.position,
                    type = SnapType.VERTEX,
                    snappedVertexId = nearestVertex.id
                )
            }

            // B2. Snap to nearest wall edge
            var nearestWall: WallSegment? = null
            var nearestProjection: Point2D? = null
            var minWallDist = Float.MAX_VALUE

            for (w in walls) {
                val seg = w.toLineSegment(vertexMap) ?: continue
                val proj = seg.closestPointTo(rawPoint)
                val d = rawPoint.distanceTo(proj)
                if (d < wallSnapRadiusCm && d < minWallDist) {
                    minWallDist = d
                    nearestWall = w
                    nearestProjection = proj
                }
            }
            if (nearestWall != null && nearestProjection != null) {
                return SnapResult(
                    point = nearestProjection,
                    type = SnapType.WALL_EDGE,
                    snappedWallId = nearestWall.id
                )
            }

            // B3. Alignment guides with other vertices (when moving in 2D)
            val guides = mutableListOf<GuideLine>()
            var alignedX = rawPoint.x
            var alignedY = rawPoint.y
            var isAligned = false

            for (v in vertices) {
                if (originPoint != null && v.position.distanceTo(originPoint) < 1f) continue
                if (abs(rawPoint.x - v.position.x) < 15f && guides.size < 2) {
                    alignedX = v.position.x
                    isAligned = true
                    guides.add(GuideLine(Point2D(v.position.x, minOf(rawPoint.y, v.position.y) - 50f), Point2D(v.position.x, maxOf(rawPoint.y, v.position.y) + 50f), false))
                }
                if (abs(rawPoint.y - v.position.y) < 15f && guides.size < 2) {
                    alignedY = v.position.y
                    isAligned = true
                    guides.add(GuideLine(Point2D(minOf(rawPoint.x, v.position.x) - 50f, v.position.y), Point2D(maxOf(rawPoint.x, v.position.x) + 50f, v.position.y), true))
                }
            }
            if (isAligned) {
                return SnapResult(
                    point = Point2D(alignedX, alignedY),
                    type = SnapType.ALIGNMENT_GUIDE,
                    guideLines = guides
                )
            }

            // B4. Grid snap (F9)
            return SnapResult(
                point = snapPointToGrid(rawPoint, gridStepCm),
                type = SnapType.GRID
            )
        }

        // B5. Completely free continuous float point (Snapping and Ortho disabled)
        return SnapResult(point = rawPoint, type = SnapType.NONE)
    }
}
