package com.cheezo.ductworks.ui.threed

import com.zakgof.korender.IndexType
import com.zakgof.korender.MeshDeclaration
import com.zakgof.korender.math.*
import com.zakgof.korender.scope.FrameScope
import kotlin.math.*

/**
 * High-Precision Procedural PBR 3D Mesh Factory for Korender.
 * Generates GPU-cached canonical unit meshes with smooth vertex normals and edge chamfers:
 * - Smooth Cylinders (24-gon with smooth side shading)
 * - Tapered Conical Frustums (for Scandinavian legs and lampshades)
 * - Spheres & Hemispheres (for knobs, rosettes, finials)
 * - Torus Rings (for curved spouts, towel rings, pipe elbows)
 * - Beveled CAD Boxes (12 chamfered edges + 8 corner fillets to catch specular PBR highlights)
 * - Organic Puffy Pillows / Cushions (contoured curvature with edge welting seam)
 */
object KorenderMeshFactory {

    class ProceduralMeshCollector {
        val positions = mutableListOf<Vec3>()
        val normals = mutableListOf<Vec3>()
        val texCoords = mutableListOf<Vec2>()
        val indices = mutableListOf<Int>()

        fun addVertex(pos: Vec3, norm: Vec3, uv: Vec2 = Vec2(0f, 0f)): Int {
            val idx = positions.size
            positions.add(pos)
            normals.add(norm)
            texCoords.add(uv)
            return idx
        }

        fun addTriangle(i0: Int, i1: Int, i2: Int) {
            indices.add(i0)
            indices.add(i1)
            indices.add(i2)
        }

        fun addQuad(i0: Int, i1: Int, i2: Int, i3: Int) {
            indices.add(i0)
            indices.add(i1)
            indices.add(i2)
            indices.add(i0)
            indices.add(i2)
            indices.add(i3)
        }
    }

    // =========================================================================
    // 1. Smooth Unit Cylinder (Diameter = 1.0, Height = 1.0, centered at 0,0,0)
    // =========================================================================

    fun FrameScope.cylinder(segments: Int = 24): MeshDeclaration {
        val id = "korender_proc_cylinder_$segments"
        val collector = ProceduralMeshCollector()
        val halfH = 0.5f
        val r = 0.5f

        // Side vertices (smooth normals)
        val sideIndicesTop = IntArray(segments + 1)
        val sideIndicesBot = IntArray(segments + 1)
        for (i in 0..segments) {
            val ang = (i % segments) * (2f * PI.toFloat() / segments)
            val cosA = cos(ang)
            val sinA = sin(ang)
            val norm = Vec3(cosA, 0f, sinA)
            val u = i.toFloat() / segments
            sideIndicesTop[i] = collector.addVertex(Vec3(r * cosA, halfH, r * sinA), norm, Vec2(u, 1f))
            sideIndicesBot[i] = collector.addVertex(Vec3(r * cosA, -halfH, r * sinA), norm, Vec2(u, 0f))
        }

        // Side quads (CCW outward)
        for (i in 0 until segments) {
            val t0 = sideIndicesTop[i]
            val t1 = sideIndicesTop[i + 1]
            val b0 = sideIndicesBot[i]
            val b1 = sideIndicesBot[i + 1]
            collector.addQuad(t0, t1, b1, b0)
        }

        // Top Cap (flat normal 0, 1, 0, CCW outward)
        val topCenter = collector.addVertex(Vec3(0f, halfH, 0f), Vec3(0f, 1f, 0f), Vec2(0.5f, 0.5f))
        val topRim = IntArray(segments + 1)
        for (i in 0..segments) {
            val ang = (i % segments) * (2f * PI.toFloat() / segments)
            val cosA = cos(ang)
            val sinA = sin(ang)
            topRim[i] = collector.addVertex(Vec3(r * cosA, halfH, r * sinA), Vec3(0f, 1f, 0f), Vec2(0.5f + 0.5f * cosA, 0.5f + 0.5f * sinA))
        }
        for (i in 0 until segments) {
            collector.addTriangle(topCenter, topRim[i + 1], topRim[i])
        }

        // Bottom Cap (flat normal 0, -1, 0, CCW outward)
        val botCenter = collector.addVertex(Vec3(0f, -halfH, 0f), Vec3(0f, -1f, 0f), Vec2(0.5f, 0.5f))
        val botRim = IntArray(segments + 1)
        for (i in 0..segments) {
            val ang = (i % segments) * (2f * PI.toFloat() / segments)
            val cosA = cos(ang)
            val sinA = sin(ang)
            botRim[i] = collector.addVertex(Vec3(r * cosA, -halfH, r * sinA), Vec3(0f, -1f, 0f), Vec2(0.5f + 0.5f * cosA, 0.5f + 0.5f * sinA))
        }
        for (i in 0 until segments) {
            collector.addTriangle(botCenter, botRim[i], botRim[i + 1])
        }

        return customMesh(
            id = id,
            vertexCount = collector.positions.size,
            indexCount = collector.indices.size,
            attributes = arrayOf(POS, NORMAL, TEX),
            dynamic = false,
            indexType = IndexType.Int
        ) {
            pos(*collector.positions.toTypedArray())
            normal(*collector.normals.toTypedArray())
            tex(*collector.texCoords.toTypedArray())
            index(*collector.indices.toIntArray())
        }
    }

    // =========================================================================
    // 2. Tapered Conical Frustum (Scandinavian Legs & Lampshades)
    // =========================================================================

    fun FrameScope.frustum(topR: Float, botR: Float, segments: Int = 20): MeshDeclaration {
        val id = "korender_proc_frustum_${(topR * 100).toInt()}_${(botR * 100).toInt()}_$segments"
        val collector = ProceduralMeshCollector()
        val halfH = 0.5f

        // Slope normal calculation
        val slopeY = botR - topR
        val slopeLen = sqrt(1f + slopeY * slopeY)
        val ny = slopeY / slopeLen
        val nr = 1f / slopeLen

        val topIndices = IntArray(segments + 1)
        val botIndices = IntArray(segments + 1)
        for (i in 0..segments) {
            val ang = (i % segments) * (2f * PI.toFloat() / segments)
            val cosA = cos(ang)
            val sinA = sin(ang)
            val norm = Vec3(nr * cosA, ny, nr * sinA).normalize()
            val u = i.toFloat() / segments
            topIndices[i] = collector.addVertex(Vec3(topR * cosA, halfH, topR * sinA), norm, Vec2(u, 1f))
            botIndices[i] = collector.addVertex(Vec3(botR * cosA, -halfH, botR * sinA), norm, Vec2(u, 0f))
        }

        for (i in 0 until segments) {
            collector.addQuad(topIndices[i], topIndices[i + 1], botIndices[i + 1], botIndices[i])
        }

        if (topR > 0.001f) {
            val tc = collector.addVertex(Vec3(0f, halfH, 0f), Vec3(0f, 1f, 0f))
            val tr = IntArray(segments + 1) { i ->
                val ang = (i % segments) * (2f * PI.toFloat() / segments)
                collector.addVertex(Vec3(topR * cos(ang), halfH, topR * sin(ang)), Vec3(0f, 1f, 0f))
            }
            for (i in 0 until segments) collector.addTriangle(tc, tr[i + 1], tr[i])
        }

        if (botR > 0.001f) {
            val bc = collector.addVertex(Vec3(0f, -halfH, 0f), Vec3(0f, -1f, 0f))
            val br = IntArray(segments + 1) { i ->
                val ang = (i % segments) * (2f * PI.toFloat() / segments)
                collector.addVertex(Vec3(botR * cos(ang), -halfH, botR * sin(ang)), Vec3(0f, -1f, 0f))
            }
            for (i in 0 until segments) collector.addTriangle(bc, br[i], br[i + 1])
        }

        return customMesh(
            id = id,
            vertexCount = collector.positions.size,
            indexCount = collector.indices.size,
            attributes = arrayOf(POS, NORMAL, TEX),
            dynamic = false,
            indexType = IndexType.Int
        ) {
            pos(*collector.positions.toTypedArray())
            normal(*collector.normals.toTypedArray())
            tex(*collector.texCoords.toTypedArray())
            index(*collector.indices.toIntArray())
        }
    }

    // =========================================================================
    // 3. Smooth UV Sphere (Diameter = 1.0, Radius = 0.5, centered at 0,0,0)
    // =========================================================================

    fun FrameScope.sphere(latSegments: Int = 14, lonSegments: Int = 20): MeshDeclaration {
        val id = "korender_proc_sphere_${latSegments}_$lonSegments"
        val collector = ProceduralMeshCollector()
        val r = 0.5f

        val grid = Array(latSegments + 1) { IntArray(lonSegments + 1) }

        for (lat in 0..latSegments) {
            val theta = lat * PI.toFloat() / latSegments
            val sinTheta = sin(theta)
            val cosTheta = cos(theta)
            val v = 1f - (lat.toFloat() / latSegments)

            for (lon in 0..lonSegments) {
                val phi = (lon % lonSegments) * (2f * PI.toFloat() / lonSegments)
                val cosPhi = cos(phi)
                val sinPhi = sin(phi)
                val u = lon.toFloat() / lonSegments

                val nx = sinTheta * cosPhi
                val ny = cosTheta
                val nz = sinTheta * sinPhi
                val norm = Vec3(nx, ny, nz).normalize()
                val pos = Vec3(r * nx, r * ny, r * nz)

                grid[lat][lon] = collector.addVertex(pos, norm, Vec2(u, v))
            }
        }

        for (lat in 0 until latSegments) {
            for (lon in 0 until lonSegments) {
                val i0 = grid[lat][lon]
                val i1 = grid[lat + 1][lon]
                val i2 = grid[lat + 1][lon + 1]
                val i3 = grid[lat][lon + 1]

                if (lat == 0) {
                    collector.addTriangle(i0, i2, i1)
                } else if (lat == latSegments - 1) {
                    collector.addTriangle(i1, i0, i3)
                } else {
                    collector.addQuad(i0, i3, i2, i1)
                }
            }
        }

        return customMesh(
            id = id,
            vertexCount = collector.positions.size,
            indexCount = collector.indices.size,
            attributes = arrayOf(POS, NORMAL, TEX),
            dynamic = false,
            indexType = IndexType.Int
        ) {
            pos(*collector.positions.toTypedArray())
            normal(*collector.normals.toTypedArray())
            tex(*collector.texCoords.toTypedArray())
            index(*collector.indices.toIntArray())
        }
    }

    // =========================================================================
    // 4. Smooth Torus Ring (Curved Spouts, Pull Handles, Burners)
    // =========================================================================

    fun FrameScope.torus(majorR: Float = 0.38f, minorR: Float = 0.12f, radialSegments: Int = 20, tubeSegments: Int = 12): MeshDeclaration {
        val id = "korender_proc_torus_${(majorR * 100).toInt()}_${(minorR * 100).toInt()}_${radialSegments}_$tubeSegments"
        val collector = ProceduralMeshCollector()

        val grid = Array(radialSegments + 1) { IntArray(tubeSegments + 1) }

        for (i in 0..radialSegments) {
            val u = i.toFloat() / radialSegments
            val theta = u * (2f * PI.toFloat())
            val cosTheta = cos(theta)
            val sinTheta = sin(theta)

            for (j in 0..tubeSegments) {
                val v = j.toFloat() / tubeSegments
                val phi = v * (2f * PI.toFloat())
                val cosPhi = cos(phi)
                val sinPhi = sin(phi)

                val x = (majorR + minorR * cosPhi) * cosTheta
                val y = minorR * sinPhi
                val z = (majorR + minorR * cosPhi) * sinTheta

                val nx = cosPhi * cosTheta
                val ny = sinPhi
                val nz = cosPhi * sinTheta
                val norm = Vec3(nx, ny, nz).normalize()

                grid[i][j] = collector.addVertex(Vec3(x, y, z), norm, Vec2(u, v))
            }
        }

        for (i in 0 until radialSegments) {
            for (j in 0 until tubeSegments) {
                val i0 = grid[i][j]
                val i1 = grid[i + 1][j]
                val i2 = grid[i + 1][j + 1]
                val i3 = grid[i][j + 1]
                collector.addQuad(i0, i3, i2, i1)
            }
        }

        return customMesh(
            id = id,
            vertexCount = collector.positions.size,
            indexCount = collector.indices.size,
            attributes = arrayOf(POS, NORMAL, TEX),
            dynamic = false,
            indexType = IndexType.Int
        ) {
            pos(*collector.positions.toTypedArray())
            normal(*collector.normals.toTypedArray())
            tex(*collector.texCoords.toTypedArray())
            index(*collector.indices.toIntArray())
        }
    }

    // =========================================================================
    // 5. Beveled Box (Chamfered 12 Edges + 8 Corners for High-End PBR Sheen)
    // =========================================================================

    fun FrameScope.beveledBox(bevel: Float = 0.08f): MeshDeclaration {
        val id = "korender_proc_beveled_box_${(bevel * 1000).toInt()}"
        val collector = ProceduralMeshCollector()
        val c = 0.5f - bevel
        val b = 0.5f

        // 6 Flat Center Face Quads
        // Top (+Y)
        collector.addQuad(
            collector.addVertex(Vec3(-c, b, -c), Vec3(0f, 1f, 0f)),
            collector.addVertex(Vec3(-c, b, c), Vec3(0f, 1f, 0f)),
            collector.addVertex(Vec3(c, b, c), Vec3(0f, 1f, 0f)),
            collector.addVertex(Vec3(c, b, -c), Vec3(0f, 1f, 0f))
        )
        // Bottom (-Y)
        collector.addQuad(
            collector.addVertex(Vec3(-c, -b, c), Vec3(0f, -1f, 0f)),
            collector.addVertex(Vec3(-c, -b, -c), Vec3(0f, -1f, 0f)),
            collector.addVertex(Vec3(c, -b, -c), Vec3(0f, -1f, 0f)),
            collector.addVertex(Vec3(c, -b, c), Vec3(0f, -1f, 0f))
        )
        // Front (+Z)
        collector.addQuad(
            collector.addVertex(Vec3(-c, -c, b), Vec3(0f, 0f, 1f)),
            collector.addVertex(Vec3(c, -c, b), Vec3(0f, 0f, 1f)),
            collector.addVertex(Vec3(c, c, b), Vec3(0f, 0f, 1f)),
            collector.addVertex(Vec3(-c, c, b), Vec3(0f, 0f, 1f))
        )
        // Back (-Z)
        collector.addQuad(
            collector.addVertex(Vec3(c, -c, -b), Vec3(0f, 0f, -1f)),
            collector.addVertex(Vec3(-c, -c, -b), Vec3(0f, 0f, -1f)),
            collector.addVertex(Vec3(-c, c, -b), Vec3(0f, 0f, -1f)),
            collector.addVertex(Vec3(c, c, -b), Vec3(0f, 0f, -1f))
        )
        // Right (+X)
        collector.addQuad(
            collector.addVertex(Vec3(b, -c, -c), Vec3(1f, 0f, 0f)),
            collector.addVertex(Vec3(b, -c, c), Vec3(1f, 0f, 0f)),
            collector.addVertex(Vec3(b, c, c), Vec3(1f, 0f, 0f)),
            collector.addVertex(Vec3(b, c, -c), Vec3(1f, 0f, 0f))
        )
        // Left (-X)
        collector.addQuad(
            collector.addVertex(Vec3(-b, -c, c), Vec3(-1f, 0f, 0f)),
            collector.addVertex(Vec3(-b, -c, -c), Vec3(-1f, 0f, 0f)),
            collector.addVertex(Vec3(-b, c, -c), Vec3(-1f, 0f, 0f)),
            collector.addVertex(Vec3(-b, c, c), Vec3(-1f, 0f, 0f))
        )

        // 12 Chamfered Edge Strips (45° angle)
        fun addChamfer(p0: Vec3, p1: Vec3, p2: Vec3, p3: Vec3, norm: Vec3) {
            collector.addQuad(
                collector.addVertex(p0, norm),
                collector.addVertex(p1, norm),
                collector.addVertex(p2, norm),
                collector.addVertex(p3, norm)
            )
        }

        val sq2 = 0.7071f
        // Top-Front (+Y, +Z)
        addChamfer(Vec3(-c, b, c), Vec3(c, b, c), Vec3(c, c, b), Vec3(-c, c, b), Vec3(0f, sq2, sq2))
        // Top-Back (+Y, -Z)
        addChamfer(Vec3(c, b, -c), Vec3(-c, b, -c), Vec3(-c, c, -b), Vec3(c, c, -b), Vec3(0f, sq2, -sq2))
        // Top-Right (+Y, +X)
        addChamfer(Vec3(c, b, -c), Vec3(c, c, -c), Vec3(c, c, c), Vec3(c, b, c), Vec3(sq2, sq2, 0f))
        // Top-Left (+Y, -X)
        addChamfer(Vec3(-c, b, c), Vec3(-c, c, c), Vec3(-c, c, -c), Vec3(-c, b, -c), Vec3(-sq2, sq2, 0f))

        // Bottom-Front (-Y, +Z)
        addChamfer(Vec3(c, -b, c), Vec3(-c, -b, c), Vec3(-c, -c, b), Vec3(c, -c, b), Vec3(0f, -sq2, sq2))
        // Bottom-Back (-Y, -Z)
        addChamfer(Vec3(-c, -b, -c), Vec3(c, -b, -c), Vec3(c, -c, -b), Vec3(-c, -c, -b), Vec3(0f, -sq2, -sq2))
        // Bottom-Right (-Y, +X)
        addChamfer(Vec3(c, -b, c), Vec3(c, -c, c), Vec3(c, -c, -c), Vec3(c, -b, -c), Vec3(sq2, -sq2, 0f))
        // Bottom-Left (-Y, -X)
        addChamfer(Vec3(-c, -b, -c), Vec3(-c, -c, -c), Vec3(-c, -c, c), Vec3(-c, -b, c), Vec3(-sq2, -sq2, 0f))

        // Vertical Edges
        // Front-Right (+X, +Z)
        addChamfer(Vec3(b, -c, c), Vec3(c, -c, b), Vec3(c, c, b), Vec3(b, c, c), Vec3(sq2, 0f, sq2))
        // Front-Left (-X, +Z)
        addChamfer(Vec3(-c, -c, b), Vec3(-b, -c, c), Vec3(-b, c, c), Vec3(-c, c, b), Vec3(-sq2, 0f, sq2))
        // Back-Right (+X, -Z)
        addChamfer(Vec3(c, -c, -b), Vec3(b, -c, -c), Vec3(b, c, -c), Vec3(c, c, -b), Vec3(sq2, 0f, -sq2))
        // Back-Left (-X, -Z)
        addChamfer(Vec3(-b, -c, -c), Vec3(-c, -c, -b), Vec3(-c, c, -b), Vec3(-b, c, -c), Vec3(-sq2, 0f, -sq2))

        // 8 Corner Chamfer Triangles
        fun addCorner(p0: Vec3, p1: Vec3, p2: Vec3, norm: Vec3) {
            collector.addTriangle(
                collector.addVertex(p0, norm),
                collector.addVertex(p1, norm),
                collector.addVertex(p2, norm)
            )
        }
        val sq3 = 0.5773f
        addCorner(Vec3(c, b, c), Vec3(b, c, c), Vec3(c, c, b), Vec3(sq3, sq3, sq3))
        addCorner(Vec3(-c, b, c), Vec3(-c, c, b), Vec3(-b, c, c), Vec3(-sq3, sq3, sq3))
        addCorner(Vec3(c, b, -c), Vec3(c, c, -b), Vec3(b, c, -c), Vec3(sq3, sq3, -sq3))
        addCorner(Vec3(-c, b, -c), Vec3(-b, c, -c), Vec3(-c, c, -b), Vec3(-sq3, sq3, -sq3))

        addCorner(Vec3(c, -b, c), Vec3(c, -c, b), Vec3(b, -c, c), Vec3(sq3, -sq3, sq3))
        addCorner(Vec3(-c, -b, c), Vec3(-b, -c, c), Vec3(-c, -c, b), Vec3(-sq3, -sq3, sq3))
        addCorner(Vec3(c, -b, -c), Vec3(b, -c, -c), Vec3(c, -c, -b), Vec3(sq3, -sq3, -sq3))
        addCorner(Vec3(-c, -b, -c), Vec3(-c, -c, -b), Vec3(-b, -c, -c), Vec3(-sq3, -sq3, -sq3))

        return customMesh(
            id = id,
            vertexCount = collector.positions.size,
            indexCount = collector.indices.size,
            attributes = arrayOf(POS, NORMAL, TEX),
            dynamic = false,
            indexType = IndexType.Int
        ) {
            pos(*collector.positions.toTypedArray())
            normal(*collector.normals.toTypedArray())
            tex(*collector.texCoords.toTypedArray())
            index(*collector.indices.toIntArray())
        }
    }

    // =========================================================================
    // 6. Organic Puffy Pillow / Soft Cushion (Sinusoidal Curvature)
    // =========================================================================

    fun FrameScope.pillow(subdiv: Int = 10): MeshDeclaration {
        val id = "korender_proc_pillow_$subdiv"
        val collector = ProceduralMeshCollector()

        val topGrid = Array(subdiv + 1) { IntArray(subdiv + 1) }
        val botGrid = Array(subdiv + 1) { IntArray(subdiv + 1) }

        // Top dome
        for (i in 0..subdiv) {
            val u = i.toFloat() / subdiv
            val x = (u - 0.5f)
            val sinU = sin(u * PI.toFloat())

            for (j in 0..subdiv) {
                val v = j.toFloat() / subdiv
                val z = (v - 0.5f)
                val sinV = sin(v * PI.toFloat())

                // Bulge amplitude
                val bulge = sinU * sinV
                val yTop = (bulge.pow(0.65f)) * 0.45f
                val yBot = -yTop

                // Gradient normals
                val dx = if (sinU > 0.001f) cos(u * PI.toFloat()) * sinV else 0f
                val dz = if (sinV > 0.001f) sinU * cos(v * PI.toFloat()) else 0f
                val normTop = Vec3(-dx * 0.8f, 1f, -dz * 0.8f).normalize()
                val normBot = Vec3(-dx * 0.8f, -1f, -dz * 0.8f).normalize()

                topGrid[i][j] = collector.addVertex(Vec3(x, yTop, z), normTop, Vec2(u, v))
                botGrid[i][j] = collector.addVertex(Vec3(x, yBot, z), normBot, Vec2(u, v))
            }
        }

        // Top surface quads (CCW outward)
        for (i in 0 until subdiv) {
            for (j in 0 until subdiv) {
                collector.addQuad(
                    topGrid[i][j],
                    topGrid[i][j + 1],
                    topGrid[i + 1][j + 1],
                    topGrid[i + 1][j]
                )
            }
        }

        // Bottom surface quads (CCW outward)
        for (i in 0 until subdiv) {
            for (j in 0 until subdiv) {
                collector.addQuad(
                    botGrid[i][j],
                    botGrid[i + 1][j],
                    botGrid[i + 1][j + 1],
                    botGrid[i][j + 1]
                )
            }
        }

        // Perimeter edge piping seam (CCW outward)
        for (i in 0 until subdiv) {
            // South edge (j = 0, facing -Z)
            collector.addQuad(topGrid[i][0], topGrid[i + 1][0], botGrid[i + 1][0], botGrid[i][0])
            // North edge (j = subdiv, facing +Z)
            collector.addQuad(topGrid[i + 1][subdiv], topGrid[i][subdiv], botGrid[i][subdiv], botGrid[i + 1][subdiv])
            // West edge (i = 0, facing -X)
            collector.addQuad(topGrid[0][i + 1], topGrid[0][i], botGrid[0][i], botGrid[0][i + 1])
            // East edge (i = subdiv, facing +X)
            collector.addQuad(topGrid[subdiv][i], topGrid[subdiv][i + 1], botGrid[subdiv][i + 1], botGrid[subdiv][i])
        }

        return customMesh(
            id = id,
            vertexCount = collector.positions.size,
            indexCount = collector.indices.size,
            attributes = arrayOf(POS, NORMAL, TEX),
            dynamic = false,
            indexType = IndexType.Int
        ) {
            pos(*collector.positions.toTypedArray())
            normal(*collector.normals.toTypedArray())
            tex(*collector.texCoords.toTypedArray())
            index(*collector.indices.toIntArray())
        }
    }
}
