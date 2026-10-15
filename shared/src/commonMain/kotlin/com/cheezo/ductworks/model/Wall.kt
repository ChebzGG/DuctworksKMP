package com.cheezo.ductworks.model

import kotlin.random.Random

/**
 * A vertex (corner or junction) shared by one or more walls.
 * Position is stored in real-world centimeters (cm).
 */
data class WallVertex(
    val id: String = generateId("v"),
    val position: Point2D
) {
    companion object {
        fun generateId(prefix: String = "v"): String {
            val timestamp = Random.nextInt(100000, 999999)
            return "$prefix-$timestamp"
        }
    }
}

enum class WallType(val titleRu: String, val defaultThicknessCm: Float) {
    EXTERIOR("Несущая стена", 25f),
    INTERIOR("Межкомнатная стена", 15f),
    PARTITION("Перегородка", 10f)
}

/**
 * An architectural wall segment connecting two vertices.
 */
data class WallSegment(
    val id: String = WallVertex.generateId("w"),
    val startVertexId: String,
    val endVertexId: String,
    val thicknessCm: Float = 20f,
    val heightCm: Float = 270f,
    val wallType: WallType = WallType.INTERIOR
) {
    fun hasVertex(vertexId: String): Boolean =
        startVertexId == vertexId || endVertexId == vertexId

    fun otherVertexId(vertexId: String): String? = when (vertexId) {
        startVertexId -> endVertexId
        endVertexId -> startVertexId
        else -> null
    }

    /**
     * Resolves the line segment geometry using the vertex map.
     */
    fun toLineSegment(vertexMap: Map<String, WallVertex>): LineSegment? {
        val vStart = vertexMap[startVertexId]?.position ?: return null
        val vEnd = vertexMap[endVertexId]?.position ?: return null
        return LineSegment(vStart, vEnd)
    }

    /**
     * Returns the 4 corner points of the wall rectangle taking into account thickness.
     */
    fun getWallPolygon(vertexMap: Map<String, WallVertex>): List<Point2D>? {
        val line = toLineSegment(vertexMap) ?: return null
        val normal = line.normal * (thicknessCm * 0.5f)
        return listOf(
            line.start + normal,
            line.end + normal,
            line.end - normal,
            line.start - normal
        )
    }
}
