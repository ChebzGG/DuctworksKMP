package com.cheezo.ductworks.engine

import com.cheezo.ductworks.model.*
import kotlin.math.abs

/**
 * Handles CAD dragging and stretching of walls, vertices, and rooms (Planner 5D style).
 * Uses baseline-relative calculations to eliminate numerical drift and support infinite drag range.
 */
object WallDraggingEngine {

    /**
     * Calculates the new positions of vertices when a vertex is dragged.
     */
    fun moveVertex(
        vertexId: String,
        targetPos: Point2D,
        baselineVertices: Map<String, WallVertex>
    ): Map<String, WallVertex> {
        val v = baselineVertices[vertexId] ?: return baselineVertices
        val newVertices = baselineVertices.toMutableMap()
        newVertices[vertexId] = v.copy(position = targetPos)
        return newVertices
    }

    /**
     * Calculates the new positions of vertices when a wall is dragged perpendicularly.
     * Computes the intersection of the shifted wall line with the infinite lines of adjoining perpendicular walls.
     * This allows expanding and shrinking rooms smoothly with no limits.
     */
    fun dragWall(
        wallId: String,
        totalDelta: Point2D,
        baselineVertices: Map<String, WallVertex>,
        currentWalls: Map<String, WallSegment>
    ): Map<String, WallVertex> {
        val wall = currentWalls[wallId] ?: return baselineVertices
        val vStart = baselineVertices[wall.startVertexId] ?: return baselineVertices
        val vEnd = baselineVertices[wall.endVertexId] ?: return baselineVertices

        // Full 2D translation: moves wall endpoints along both X and Y axes
        val newStartPos = vStart.position + totalDelta
        val newEndPos = vEnd.position + totalDelta

        val newVertices = baselineVertices.toMutableMap()
        newVertices[wall.startVertexId] = vStart.copy(position = newStartPos)
        newVertices[wall.endVertexId] = vEnd.copy(position = newEndPos)
        return newVertices
    }

    /**
     * Translates an entire room (all its vertices) by [totalDelta] relative to [baselineVertices].
     */
    fun translateRoom(
        room: Room,
        totalDelta: Point2D,
        baselineVertices: Map<String, WallVertex>
    ): Map<String, WallVertex> {
        val newVertices = baselineVertices.toMutableMap()
        for (vId in room.vertexIds) {
            val v = baselineVertices[vId] ?: continue
            newVertices[vId] = v.copy(position = v.position + totalDelta)
        }
        return newVertices
    }

    /**
     * Resizes a wall to [newLengthCm] while keeping [anchorVertexId] fixed.
     */
    fun resizeWallLength(
        wallId: String,
        newLengthCm: Float,
        anchorVertexId: String?,
        baselineVertices: Map<String, WallVertex>,
        currentWalls: Map<String, WallSegment>
    ): Map<String, WallVertex> {
        val wall = currentWalls[wallId] ?: return baselineVertices
        val line = wall.toLineSegment(baselineVertices) ?: return baselineVertices
        if (newLengthCm < 10f) return baselineVertices

        val anchorId = anchorVertexId ?: wall.startVertexId
        val moveId = if (anchorId == wall.startVertexId) wall.endVertexId else wall.startVertexId

        val anchorPos = baselineVertices[anchorId]?.position ?: return baselineVertices
        val moveVertex = baselineVertices[moveId] ?: return baselineVertices

        val dir = if (anchorId == wall.startVertexId) line.direction else line.direction * -1f
        val newMovePos = anchorPos + dir * newLengthCm

        val newVertices = baselineVertices.toMutableMap()
        newVertices[moveId] = moveVertex.copy(position = newMovePos)
        return newVertices
    }
}
