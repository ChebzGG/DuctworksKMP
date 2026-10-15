package com.cheezo.ductworks

import kotlin.math.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KorenderMeshFactoryTest {

    data class Vec3(val x: Float, val y: Float, val z: Float) {
        operator fun minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
        operator fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
        fun dot(other: Vec3) = x * other.x + y * other.y + z * other.z
        fun cross(other: Vec3) = Vec3(
            y * other.z - z * other.y,
            z * other.x - x * other.z,
            x * other.y - y * other.x
        )
        fun length() = sqrt(x * x + y * y + z * z)
        fun normalize(): Vec3 {
            val l = length()
            return if (l > 1e-6f) Vec3(x / l, y / l, z / l) else this
        }
    }

    class MeshCollector {
        val positions = mutableListOf<Vec3>()
        val normals = mutableListOf<Vec3>()
        val indices = mutableListOf<Int>()

        fun addVertex(p: Vec3, n: Vec3): Int {
            val idx = positions.size
            positions.add(p)
            normals.add(n)
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

    private fun countInvertedTriangles(collector: MeshCollector): Int {
        var inverted = 0
        val idx = collector.indices
        for (i in 0 until idx.size step 3) {
            val p0 = collector.positions[idx[i]]
            val p1 = collector.positions[idx[i + 1]]
            val p2 = collector.positions[idx[i + 2]]
            val edge1 = p1 - p0
            val edge2 = p2 - p0
            val geomNormal = edge1.cross(edge2)
            val vNormal = collector.normals[idx[i]]
            val dot = geomNormal.dot(vNormal)
            if (dot < -1e-5f) {
                inverted++
            }
        }
        return inverted
    }

    @Test
    fun testCylinderWinding() {
        val collector = MeshCollector()
        val segments = 24
        val halfH = 0.5f
        val r = 0.5f

        val sideTop = IntArray(segments + 1)
        val sideBot = IntArray(segments + 1)
        for (i in 0..segments) {
            val ang = (i % segments) * (2f * PI.toFloat() / segments)
            val c = cos(ang)
            val s = sin(ang)
            val norm = Vec3(c, 0f, s)
            sideTop[i] = collector.addVertex(Vec3(r * c, halfH, r * s), norm)
            sideBot[i] = collector.addVertex(Vec3(r * c, -halfH, r * s), norm)
        }
        for (i in 0 until segments) {
            val t0 = sideTop[i]
            val t1 = sideTop[i + 1]
            val b0 = sideBot[i]
            val b1 = sideBot[i + 1]
            collector.addQuad(t0, t1, b1, b0)
        }

        val tc = collector.addVertex(Vec3(0f, halfH, 0f), Vec3(0f, 1f, 0f))
        val tr = IntArray(segments + 1) { i ->
            val ang = (i % segments) * (2f * PI.toFloat() / segments)
            collector.addVertex(Vec3(r * cos(ang), halfH, r * sin(ang)), Vec3(0f, 1f, 0f))
        }
        for (i in 0 until segments) {
            collector.addTriangle(tc, tr[i + 1], tr[i])
        }

        val bc = collector.addVertex(Vec3(0f, -halfH, 0f), Vec3(0f, -1f, 0f))
        val br = IntArray(segments + 1) { i ->
            val ang = (i % segments) * (2f * PI.toFloat() / segments)
            collector.addVertex(Vec3(r * cos(ang), -halfH, r * sin(ang)), Vec3(0f, -1f, 0f))
        }
        for (i in 0 until segments) {
            collector.addTriangle(bc, br[i], br[i + 1])
        }

        val inverted = countInvertedTriangles(collector)
        assertEquals(0, inverted, "Cylinder must have 0 inverted triangles")
    }

    @Test
    fun testFrustumWinding() {
        val collector = MeshCollector()
        val segments = 20
        val topR = 0.3f
        val botR = 0.5f
        val halfH = 0.5f

        val slopeY = botR - topR
        val slopeLen = sqrt(1f + slopeY * slopeY)
        val ny = slopeY / slopeLen
        val nr = 1f / slopeLen

        val topIndices = IntArray(segments + 1)
        val botIndices = IntArray(segments + 1)
        for (i in 0..segments) {
            val ang = (i % segments) * (2f * PI.toFloat() / segments)
            val c = cos(ang)
            val s = sin(ang)
            val norm = Vec3(nr * c, ny, nr * s).normalize()
            topIndices[i] = collector.addVertex(Vec3(topR * c, halfH, topR * s), norm)
            botIndices[i] = collector.addVertex(Vec3(botR * c, -halfH, botR * s), norm)
        }

        for (i in 0 until segments) {
            collector.addQuad(topIndices[i], topIndices[i + 1], botIndices[i + 1], botIndices[i])
        }

        val tc = collector.addVertex(Vec3(0f, halfH, 0f), Vec3(0f, 1f, 0f))
        val tr = IntArray(segments + 1) { i ->
            val ang = (i % segments) * (2f * PI.toFloat() / segments)
            collector.addVertex(Vec3(topR * cos(ang), halfH, topR * sin(ang)), Vec3(0f, 1f, 0f))
        }
        for (i in 0 until segments) collector.addTriangle(tc, tr[i + 1], tr[i])

        val bc = collector.addVertex(Vec3(0f, -halfH, 0f), Vec3(0f, -1f, 0f))
        val br = IntArray(segments + 1) { i ->
            val ang = (i % segments) * (2f * PI.toFloat() / segments)
            collector.addVertex(Vec3(botR * cos(ang), -halfH, botR * sin(ang)), Vec3(0f, -1f, 0f))
        }
        for (i in 0 until segments) collector.addTriangle(bc, br[i], br[i + 1])

        val inverted = countInvertedTriangles(collector)
        assertEquals(0, inverted, "Frustum must have 0 inverted triangles")
    }

    @Test
    fun testSphereWinding() {
        val collector = MeshCollector()
        val latSegments = 14
        val lonSegments = 20
        val r = 0.5f

        val grid = Array(latSegments + 1) { IntArray(lonSegments + 1) }

        for (lat in 0..latSegments) {
            val theta = lat * PI.toFloat() / latSegments
            val sinTheta = sin(theta)
            val cosTheta = cos(theta)

            for (lon in 0..lonSegments) {
                val phi = (lon % lonSegments) * (2f * PI.toFloat() / lonSegments)
                val cosPhi = cos(phi)
                val sinPhi = sin(phi)

                val nx = sinTheta * cosPhi
                val ny = cosTheta
                val nz = sinTheta * sinPhi
                val norm = Vec3(nx, ny, nz).normalize()
                val pos = Vec3(r * nx, r * ny, r * nz)

                grid[lat][lon] = collector.addVertex(pos, norm)
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

        var inverted = 0
        val idx = collector.indices
        for (i in 0 until idx.size step 3) {
            val p0 = collector.positions[idx[i]]
            val p1 = collector.positions[idx[i + 1]]
            val p2 = collector.positions[idx[i + 2]]
            val edge1 = p1 - p0
            val edge2 = p2 - p0
            val geomNormal = edge1.cross(edge2)
            val vNormal = collector.normals[idx[i]]
            val dot = geomNormal.dot(vNormal)
            if (dot < -1e-5f) {
                println("Sphere inverted at tri $i: p0=$p0, p1=$p1, p2=$p2, geomNorm=$geomNormal, vNorm=$vNormal, dot=$dot")
                inverted++
            }
        }
        assertEquals(0, inverted, "Sphere must have 0 inverted triangles, found $inverted")
    }

    @Test
    fun testTorusWinding() {
        val collector = MeshCollector()
        val majorR = 0.38f
        val minorR = 0.12f
        val radialSegments = 20
        val tubeSegments = 12

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

                grid[i][j] = collector.addVertex(Vec3(x, y, z), norm)
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

        val inverted = countInvertedTriangles(collector)
        assertEquals(0, inverted, "Torus must have 0 inverted triangles")
    }

    @Test
    fun testPillowWinding() {
        val collector = MeshCollector()
        val subdiv = 10

        val topGrid = Array(subdiv + 1) { IntArray(subdiv + 1) }
        val botGrid = Array(subdiv + 1) { IntArray(subdiv + 1) }

        for (i in 0..subdiv) {
            val u = i.toFloat() / subdiv
            val x = (u - 0.5f)
            val sinU = sin(u * PI.toFloat())

            for (j in 0..subdiv) {
                val v = j.toFloat() / subdiv
                val z = (v - 0.5f)
                val sinV = sin(v * PI.toFloat())

                val bulge = sinU * sinV
                val yTop = (bulge.pow(0.65f)) * 0.45f
                val yBot = -yTop

                val dx = if (sinU > 0.001f) cos(u * PI.toFloat()) * sinV else 0f
                val dz = if (sinV > 0.001f) sinU * cos(v * PI.toFloat()) else 0f
                val normTop = Vec3(-dx * 0.8f, 1f, -dz * 0.8f).normalize()
                val normBot = Vec3(-dx * 0.8f, -1f, -dz * 0.8f).normalize()

                topGrid[i][j] = collector.addVertex(Vec3(x, yTop, z), normTop)
                botGrid[i][j] = collector.addVertex(Vec3(x, yBot, z), normBot)
            }
        }

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

        val inverted = countInvertedTriangles(collector)
        assertEquals(0, inverted, "Pillow top & bottom must have 0 inverted triangles")
    }
}
