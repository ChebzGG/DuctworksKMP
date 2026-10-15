package com.cheezo.ductworks.engine.threed

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min

enum class MaterialType {
    FLOOR_PARQUET,
    FLOOR_TILES,
    FLOOR_CONCRETE,
    WALL_EXTERIOR,
    WALL_INTERIOR,
    WALL_TOP,
    WINDOW_FRAME,
    WINDOW_GLASS,
    DOOR_WOOD,
    FURNITURE_WOOD,
    FURNITURE_FABRIC,
    FURNITURE_LEATHER,
    SANITARY_CERAMIC,
    HVAC_METAL,
    HVAC_DIFFUSER_BLUE,
    HVAC_GRILLE_ORANGE
}

data class Face3D(
    val vertices: List<Vector3D>,
    val baseColor: Color,
    val material: MaterialType = MaterialType.WALL_INTERIOR,
    val alpha: Float = 1.0f,
    val normal: Vector3D = computeNormal(vertices),
    val objectId: String? = null, // For selection & hit-testing
    val drawOutline: Boolean = false, // Whether to stroke polygon perimeter
    val outlineColor: Color? = null,
    val isDoubleSided: Boolean = false,
    val isTransparent: Boolean = (alpha < 0.99f),
    val depthOffset: Float = 0f // Small bias for sorting coplanar faces
) {
    val centroid: Vector3D by lazy {
        var sx = 0f
        var sy = 0f
        var sz = 0f
        for (v in vertices) {
            sx += v.x
            sy += v.y
            sz += v.z
        }
        val count = vertices.size.toFloat().coerceAtLeast(1f)
        Vector3D(sx / count, sy / count, sz / count)
    }

    /**
     * Calculates rich architectural studio lighting with warm sun, cool sky fill, and ambient room bounce.
     */
    fun computeArchitecturalColor(
        sunDir: Vector3D,
        normalToUse: Vector3D = normal
    ): Color {
        val n = normalToUse.normalized()

        // 1. Warm Sun direct illumination
        val sunDot = max(0f, n.dot(sunDir))

        // 2. Cool Sky diffuse fill from opposite quarter
        val skyDir = Vector3D(-sunDir.x * 0.6f, 0.75f, -sunDir.z * 0.6f).normalized()
        val skyDot = max(0f, n.dot(skyDir)) * 0.32f

        // 3. Ambient bounce from floor/ceiling
        val upBounce = max(0f, n.y) * 0.18f

        // Total illumination factor
        val totalLight = (0.50f + sunDot * 0.42f + skyDot + upBounce).coerceIn(0.42f, 1.15f)

        val r = (baseColor.red * totalLight * 1.02f).coerceIn(0f, 1f)
        val g = (baseColor.green * totalLight * 1.01f).coerceIn(0f, 1f)
        val b = (baseColor.blue * totalLight * 0.98f).coerceIn(0f, 1f)

        return Color(red = r, green = g, blue = b, alpha = alpha)
    }

    fun computeShadedColor(
        lightDir: Vector3D,
        normalToUse: Vector3D = normal,
        ambient: Float = 0.40f,
        diffuse: Float = 0.60f
    ): Color = computeArchitecturalColor(lightDir, normalToUse)

    companion object {
        fun computeNormal(verts: List<Vector3D>): Vector3D {
            if (verts.size < 3) return Vector3D.Up
            val v0 = verts[0]
            val v1 = verts[1]
            val v2 = verts[2]
            val edge1 = v1 - v0
            val edge2 = v2 - v0
            val cross = edge1.cross(edge2)
            return cross.normalized()
        }
    }
}

/**
 * 3D Mesh container for an architectural entity.
 */
data class Mesh3D(
    val id: String,
    val faces: List<Face3D>
)
