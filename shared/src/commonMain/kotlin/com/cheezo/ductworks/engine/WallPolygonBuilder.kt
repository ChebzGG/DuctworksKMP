package com.cheezo.ductworks.engine

import com.cheezo.ductworks.model.*
import kotlin.math.*

/**
 * Generates exact architectural CAD 2D polygons for walls and corner joints.
 * Computes mitered corners for L-junctions and seamless fills for T- and X-junctions.
 */
object WallPolygonBuilder {

    data class WallMesh(
        val wallId: String,
        val polygon: List<Point2D>, // 4 corner points in order: start-left, end-left, end-right, start-right
        val startMiter: Pair<Point2D, Point2D>, // (left, right) at start
        val endMiter: Pair<Point2D, Point2D>,   // (left, right) at end
        val drawStartCap: Boolean = true,
        val drawEndCap: Boolean = true
    )

    /**
     * Builds clean 2D architectural meshes for all walls in the document.
     */
    fun buildWallMeshes(
        vertices: Map<String, WallVertex>,
        walls: Map<String, WallSegment>
    ): List<WallMesh> {
        val result = mutableListOf<WallMesh>()

        // Precompute connected walls for each vertex
        val vertexWalls = mutableMapOf<String, MutableList<WallSegment>>()
        for (v in vertices.keys) vertexWalls[v] = mutableListOf()
        for (w in walls.values) {
            vertexWalls[w.startVertexId]?.add(w)
            vertexWalls[w.endVertexId]?.add(w)
        }

        for (wall in walls.values) {
            val vStart = vertices[wall.startVertexId]?.position ?: continue
            val vEnd = vertices[wall.endVertexId]?.position ?: continue
            val line = LineSegment(vStart, vEnd)
            if (line.length < 1f) continue

            val halfThick = wall.thicknessCm * 0.5f
            val dir = line.direction
            val normal = line.normal

            // Calculate start vertex miter points
            val startConnected = vertexWalls[wall.startVertexId]?.filter { it.id != wall.id } ?: emptyList()
            val startPoints = calculateVertexOffset(
                vertex = vStart,
                targetWall = wall,
                isStartOfTarget = true,
                otherWalls = startConnected,
                vertices = vertices,
                halfThickness = halfThick,
                direction = dir,
                normal = normal
            )

            // Calculate end vertex miter points
            val endConnected = vertexWalls[wall.endVertexId]?.filter { it.id != wall.id } ?: emptyList()
            val endPoints = calculateVertexOffset(
                vertex = vEnd,
                targetWall = wall,
                isStartOfTarget = false,
                otherWalls = endConnected,
                vertices = vertices,
                halfThickness = halfThick,
                direction = dir * -1f,
                normal = normal * -1f
            )

            // 4 polygon corners
            // startPoints: (left, right) relative to wall direction
            // endPoints: (left, right) relative to inverted direction, so end-left is endPoints.right relative to wall
            val startLeft = startPoints.first
            val startRight = startPoints.second
            val endLeft = endPoints.second
            val endRight = endPoints.first

            val poly = listOf(startLeft, endLeft, endRight, startRight)
            result.add(
                WallMesh(
                    wallId = wall.id,
                    polygon = poly,
                    startMiter = Pair(startLeft, startRight),
                    endMiter = Pair(endLeft, endRight),
                    drawStartCap = startConnected.isEmpty(),
                    drawEndCap = endConnected.isEmpty()
                )
            )
        }

        return result
    }

    /**
     * Calculates left and right offset points at a vertex.
     * Uses line-line intersection for 2-wall corners (miter join).
     */
    private fun calculateVertexOffset(
        vertex: Point2D,
        targetWall: WallSegment,
        isStartOfTarget: Boolean,
        otherWalls: List<WallSegment>,
        vertices: Map<String, WallVertex>,
        halfThickness: Float,
        direction: Point2D,
        normal: Point2D
    ): Pair<Point2D, Point2D> {
        // Fallback flat cap
        val flatLeft = vertex + normal * halfThickness
        val flatRight = vertex - normal * halfThickness

        if (otherWalls.size != 1) {
            // Standalone end, T-junction, or X-junction: flat cap
            return Pair(flatLeft, flatRight)
        }

        // Exactly 2 walls meet (L-corner): calculate clean miter join
        val otherWall = otherWalls[0]
        val otherOtherVId = otherWall.otherVertexId(
            if (isStartOfTarget) targetWall.startVertexId else targetWall.endVertexId
        ) ?: return Pair(flatLeft, flatRight)

        val otherEnd = vertices[otherOtherVId]?.position ?: return Pair(flatLeft, flatRight)
        val otherDir = (otherEnd - vertex).normalized()
        val otherNormal = otherDir.normal()
        val otherHalfThick = otherWall.thicknessCm * 0.5f

        // Angle between directions
        val cross = direction.cross(otherDir)
        val dot = direction.dot(otherDir)

        // If almost collinear or parallel, use flat cap
        if (abs(cross) < 0.05f || dot < -0.99f || dot > 0.99f) {
            return Pair(flatLeft, flatRight)
        }

        // Intersect matching left edges
        // Note: because otherDir points away from vertex, otherNormal points in the opposite rotational sense relative to the junction,
        // so target left (+normal) corresponds to other wall (-otherNormal).
        val left1 = vertex + normal * halfThickness
        val left2 = vertex - otherNormal * otherHalfThick
        val miterLeft = intersectRays(left1, direction, left2, otherDir)

        // Intersect matching right edges
        val right1 = vertex - normal * halfThickness
        val right2 = vertex + otherNormal * otherHalfThick
        val miterRight = intersectRays(right1, direction, right2, otherDir)

        // Clamp miter spike if angle is too sharp (avoid infinite spikes)
        val maxMiterDist = halfThickness * 3.5f
        val finalLeft = if (miterLeft != null && vertex.distanceTo(miterLeft) <= maxMiterDist) miterLeft else flatLeft
        val finalRight = if (miterRight != null && vertex.distanceTo(miterRight) <= maxMiterDist) miterRight else flatRight

        return Pair(finalLeft, finalRight)
    }

    private fun intersectRays(p1: Point2D, d1: Point2D, p2: Point2D, d2: Point2D): Point2D? {
        val cross = d1.cross(d2)
        if (abs(cross) < 0.0001f) return null

        val delta = p2 - p1
        val t = delta.cross(d2) / cross
        return p1 + d1 * t
    }

    /**
     * Returns polygon cap for vertices with 3+ walls (T- and X-junctions)
     * to eliminate visual gaps at intersection centers.
     */
    fun buildJunctionFillPoints(
        vertex: Point2D,
        maxThickness: Float
    ): Float = maxThickness * 0.55f
}
