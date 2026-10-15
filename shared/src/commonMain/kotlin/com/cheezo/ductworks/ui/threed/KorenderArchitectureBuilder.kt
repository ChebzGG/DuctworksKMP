package com.cheezo.ductworks.ui.threed

import com.cheezo.ductworks.engine.threed.WallHeightMode3D
import com.cheezo.ductworks.model.*
import com.zakgof.korender.*
import com.zakgof.korender.math.*
import com.zakgof.korender.scope.FrameScope
import kotlin.math.*

/**
 * High-End Procedural 3D Architecture & Furniture Builder for Korender.
 * Transforms CAD PlanDocument into GPU-accelerated PBR geometry (in METERS):
 * - Studio CAD Ground Platform & Floor slabs
 * - Parametric solid walls with physical door/window cutouts & reveals
 * - Designer Scandinavian sofa (facing coffee table), coffee table with shelf, and full catalog
 * - Realistic doors open at 35° and architectural windows with transparent glass
 */
object KorenderArchitectureBuilder {

    // --- Architectural Studio Palette ---
    val WallInteriorColor = ColorRGBA(0.97f, 0.97f, 0.96f, 1f) // Warm Architectural Off-White Plaster
    val WallExteriorColor = ColorRGBA(0.88f, 0.90f, 0.93f, 1f) // Facade Gray
    val WallSelectedColor = ColorRGBA(0.15f, 0.39f, 0.92f, 1f)
    val ParquetWoodColor = ColorRGBA(0.88f, 0.77f, 0.63f, 1f)  // Warm Scandinavian Parquet Oak

    // Openings & Glass
    val GlassColor = ColorRGBA(0.50f, 0.78f, 0.98f, 0.35f)     // Clear Sky Tint Glass
    val WindowFrameColor = ColorRGBA(0.20f, 0.23f, 0.28f, 1f)  // Modern Anthracite Aluminum
    val WindowSillColor = ColorRGBA(0.98f, 0.98f, 0.98f, 1f)   // Polished Stone Sill
    val WindowLedgeColor = ColorRGBA(0.65f, 0.70f, 0.78f, 1f)

    val DoorFrameColor = ColorRGBA(0.22f, 0.25f, 0.30f, 1f)
    val DoorWoodColor = ColorRGBA(0.68f, 0.48f, 0.28f, 1f)     // Natural Warm Oak Door Leaf
    val ChromeColor = ColorRGBA(0.92f, 0.94f, 0.97f, 1f)

    // Furniture: Designer Scandinavian
    val FabricSofaColor = ColorRGBA(0.35f, 0.42f, 0.50f, 1f)   // Nordic Slate Blue
    val CushionColor = ColorRGBA(0.44f, 0.51f, 0.59f, 1f)
    val AccentPillow1Color = ColorRGBA(0.90f, 0.58f, 0.12f, 1f) // Mustard Ochre
    val AccentPillow2Color = ColorRGBA(0.08f, 0.55f, 0.68f, 1f) // Ocean Teal
    val WoodLegColor = ColorRGBA(0.35f, 0.18f, 0.06f, 1f)       // Dark Walnut
    val BrassAccentColor = ColorRGBA(0.95f, 0.78f, 0.20f, 1f)   // Polished Brass

    val CoffeeTableTopColor = ColorRGBA(0.86f, 0.72f, 0.55f, 1f) // Solid Oak Tabletop
    val CoffeeTableShelfColor = ColorRGBA(0.80f, 0.66f, 0.49f, 1f)
    val CoffeeTableLegColor = ColorRGBA(0.14f, 0.17f, 0.22f, 1f) // Matte Black Steel

    val CeramicWhiteColor = ColorRGBA(0.98f, 0.98f, 0.99f, 1f)
    val WaterColor = ColorRGBA(0.55f, 0.82f, 0.95f, 0.65f)
    val StainlessSteelColor = ColorRGBA(0.82f, 0.85f, 0.89f, 1f)
    val CharcoalMetalColor = ColorRGBA(0.18f, 0.20f, 0.24f, 1f)

    fun FrameScope.renderScene(
        document: PlanDocument,
        heightMode: WallHeightMode3D,
        selectedId: String? = null
    ) {
        val bounds = document.getBoundingBox()
        val cx = bounds.center.x / 100f
        val cz = bounds.center.y / 100f

        // 1. Studio Architectural Podium Plate (subtle bevel step catches contact shadows)
        val extentX = max(32f, (bounds.width / 100f) + 10f)
        val extentZ = max(32f, (bounds.height / 100f) + 10f)
        renderBox(
            centerX = cx, centerY = -0.02f, centerZ = cz,
            width = extentX + 0.4f, height = 0.025f, depth = extentZ + 0.4f,
            color = ColorRGBA(0.88f, 0.90f, 0.93f, 1f),
            roughness = 0.55f
        )
        renderBox(
            centerX = cx, centerY = -0.005f, centerZ = cz,
            width = extentX, height = 0.010f, depth = extentZ,
            color = ColorRGBA(0.94f, 0.95f, 0.97f, 1f),
            roughness = 0.45f
        )

        // 2. Parquet Floor Slabs
        renderFloorSlabs(document, selectedId)

        // 3. Extruded Solid Walls with Door & Window Cutouts
        renderWalls(document, heightMode, selectedId)

        // 4. Placed Furniture Items, Openings & Fixtures
        for (item in document.items.values) {
            renderItem(item, isSelected = (item.id == selectedId), heightMode = heightMode)
        }
    }

    // --- Helper: Render Oriented Box with exact scale -> rotate -> translate matrix order ---
    fun FrameScope.renderBox(
        centerX: Float,
        centerY: Float,
        centerZ: Float,
        width: Float,
        height: Float,
        depth: Float,
        rotationDeg: Float = 0f,
        color: ColorRGBA,
        transparent: Boolean = false,
        roughness: Float = 0.4f,
        metallic: Float = 0.05f
    ) {
        if (width <= 0.001f || height <= 0.001f || depth <= 0.001f) return
        val rotRad = -rotationDeg * (PI.toFloat() / 180f)

        // Matrix order in Korender: scale * rotate * translate creates canonical T * R * S
        val t = Transform.scale(width, height, depth)
            .rotate(Vec3(0f, 1f, 0f), rotRad)
            .translate(centerX, centerY, centerZ)

        Renderable(
            material = base {
                this.color = color
                this.roughnessFactor = roughness
                this.metallicFactor = metallic
            },
            mesh = cube(0.5f),
            transform = t,
            transparent = transparent
        )
    }

    // --- Helper: Render Oriented Cylinder in world space ---
    fun FrameScope.renderCylinder(
        centerX: Float,
        centerY: Float,
        centerZ: Float,
        diameter: Float,
        height: Float,
        rotationDeg: Float = 0f,
        color: ColorRGBA,
        transparent: Boolean = false,
        roughness: Float = 0.4f,
        metallic: Float = 0.05f,
        segments: Int = 24
    ) {
        if (diameter <= 0.001f || height <= 0.001f) return
        val rotRad = -rotationDeg * (PI.toFloat() / 180f)
        val t = Transform.scale(diameter, height, diameter)
            .rotate(Vec3(0f, 1f, 0f), rotRad)
            .translate(centerX, centerY, centerZ)

        with(KorenderMeshFactory) {
            Renderable(
                material = base {
                    this.color = color
                    this.roughnessFactor = roughness
                    this.metallicFactor = metallic
                },
                mesh = cylinder(segments),
                transform = t,
                transparent = transparent
            )
        }
    }

    // --- Helper: Render Local Box relative to parent item ---
    fun FrameScope.renderLocalBox(
        parentCenter: Point2D,
        parentRotationDeg: Float,
        localOffsetX: Float, // in meters
        localOffsetZ: Float, // in meters
        width: Float,        // in meters
        height: Float,       // in meters
        depth: Float,        // in meters
        yMin: Float,         // in meters
        color: ColorRGBA,
        extraRotationDeg: Float = 0f,
        transparent: Boolean = false,
        roughness: Float = 0.4f,
        metallic: Float = 0.05f
    ) {
        val rad = -parentRotationDeg * (PI.toFloat() / 180f)
        val cosA = cos(rad)
        val sinA = sin(rad)
        val wx = (parentCenter.x / 100f) + (localOffsetX * cosA + localOffsetZ * sinA)
        val wz = (parentCenter.y / 100f) + (-localOffsetX * sinA + localOffsetZ * cosA)
        val wy = yMin + height * 0.5f

        renderBox(
            centerX = wx, centerY = wy, centerZ = wz,
            width = width, height = height, depth = depth,
            rotationDeg = parentRotationDeg + extraRotationDeg,
            color = color, transparent = transparent,
            roughness = roughness, metallic = metallic
        )
    }

    // --- Helper: Render Procedural Mesh relative to parent item ---
    fun FrameScope.renderLocalMesh(
        parentCenter: Point2D,
        parentRotationDeg: Float,
        localOffsetX: Float,
        localOffsetZ: Float,
        scaleX: Float,
        scaleY: Float,
        scaleZ: Float,
        yMin: Float,
        mesh: MeshDeclaration,
        color: ColorRGBA,
        extraRotationDeg: Float = 0f,
        transparent: Boolean = false,
        roughness: Float = 0.4f,
        metallic: Float = 0.05f
    ) {
        if (scaleX <= 0.0001f || scaleY <= 0.0001f || scaleZ <= 0.0001f) return
        val rad = -parentRotationDeg * (PI.toFloat() / 180f)
        val cosA = cos(rad)
        val sinA = sin(rad)
        val wx = (parentCenter.x / 100f) + (localOffsetX * cosA + localOffsetZ * sinA)
        val wz = (parentCenter.y / 100f) + (-localOffsetX * sinA + localOffsetZ * cosA)
        val wy = yMin + scaleY * 0.5f

        val rotRad = -(parentRotationDeg + extraRotationDeg) * (PI.toFloat() / 180f)
        val t = Transform.scale(scaleX, scaleY, scaleZ)
            .rotate(Vec3(0f, 1f, 0f), rotRad)
            .translate(wx, wy, wz)

        Renderable(
            material = base {
                this.color = color
                this.roughnessFactor = roughness
                this.metallicFactor = metallic
            },
            mesh = mesh,
            transform = t,
            transparent = transparent
        )
    }

    // --- Helper: Render Smooth 24-gon Cylinder ---
    fun FrameScope.renderLocalCylinder(
        parentCenter: Point2D,
        parentRotationDeg: Float,
        localOffsetX: Float,
        localOffsetZ: Float,
        diameter: Float,
        height: Float,
        yMin: Float,
        color: ColorRGBA,
        extraRotationDeg: Float = 0f,
        transparent: Boolean = false,
        roughness: Float = 0.35f,
        metallic: Float = 0.05f,
        segments: Int = 24
    ) {
        with(KorenderMeshFactory) {
            renderLocalMesh(
                parentCenter = parentCenter,
                parentRotationDeg = parentRotationDeg,
                localOffsetX = localOffsetX,
                localOffsetZ = localOffsetZ,
                scaleX = diameter,
                scaleY = height,
                scaleZ = diameter,
                yMin = yMin,
                mesh = cylinder(segments),
                color = color,
                extraRotationDeg = extraRotationDeg,
                transparent = transparent,
                roughness = roughness,
                metallic = metallic
            )
        }
    }

    // Backwards-compatible alias: upgrades all previous octagon cylinders to smooth 24-gons
    fun FrameScope.renderOctagonCylinder(
        parentCenter: Point2D,
        parentRotationDeg: Float,
        localOffsetX: Float,
        localOffsetZ: Float,
        diameter: Float,
        height: Float,
        yMin: Float,
        color: ColorRGBA,
        transparent: Boolean = false,
        roughness: Float = 0.4f,
        metallic: Float = 0.05f
    ) {
        renderLocalCylinder(
            parentCenter = parentCenter,
            parentRotationDeg = parentRotationDeg,
            localOffsetX = localOffsetX,
            localOffsetZ = localOffsetZ,
            diameter = diameter,
            height = height,
            yMin = yMin,
            color = color,
            transparent = transparent,
            roughness = roughness,
            metallic = metallic,
            segments = 24
        )
    }

    // --- Helper: Render Smooth UV Sphere ---
    fun FrameScope.renderLocalSphere(
        parentCenter: Point2D,
        parentRotationDeg: Float,
        localOffsetX: Float,
        localOffsetZ: Float,
        diameter: Float,
        yMin: Float,
        color: ColorRGBA,
        roughness: Float = 0.2f,
        metallic: Float = 0.8f
    ) {
        with(KorenderMeshFactory) {
            renderLocalMesh(
                parentCenter = parentCenter,
                parentRotationDeg = parentRotationDeg,
                localOffsetX = localOffsetX,
                localOffsetZ = localOffsetZ,
                scaleX = diameter,
                scaleY = diameter,
                scaleZ = diameter,
                yMin = yMin,
                mesh = sphere(),
                color = color,
                roughness = roughness,
                metallic = metallic
            )
        }
    }

    // --- Helper: Render Tapered Conical Frustum (Scandinavian legs & lampshades) ---
    fun FrameScope.renderLocalFrustum(
        parentCenter: Point2D,
        parentRotationDeg: Float,
        localOffsetX: Float,
        localOffsetZ: Float,
        topDiameter: Float,
        bottomDiameter: Float,
        height: Float,
        yMin: Float,
        color: ColorRGBA,
        extraRotationDeg: Float = 0f,
        roughness: Float = 0.4f,
        metallic: Float = 0.05f
    ) {
        val topR = topDiameter * 0.5f
        val botR = bottomDiameter * 0.5f
        with(KorenderMeshFactory) {
            renderLocalMesh(
                parentCenter = parentCenter,
                parentRotationDeg = parentRotationDeg,
                localOffsetX = localOffsetX,
                localOffsetZ = localOffsetZ,
                scaleX = 1f,
                scaleY = height,
                scaleZ = 1f,
                yMin = yMin,
                mesh = frustum(topR, botR),
                color = color,
                extraRotationDeg = extraRotationDeg,
                roughness = roughness,
                metallic = metallic
            )
        }
    }

    // --- Helper: Render Clean CAD Box (exact planar faces with orthogonal PBR normals) ---
    fun FrameScope.renderLocalBeveledBox(
        parentCenter: Point2D,
        parentRotationDeg: Float,
        localOffsetX: Float,
        localOffsetZ: Float,
        width: Float,
        height: Float,
        depth: Float,
        yMin: Float,
        color: ColorRGBA,
        extraRotationDeg: Float = 0f,
        transparent: Boolean = false,
        roughness: Float = 0.35f,
        metallic: Float = 0.05f
    ) {
        renderLocalBox(
            parentCenter = parentCenter,
            parentRotationDeg = parentRotationDeg,
            localOffsetX = localOffsetX,
            localOffsetZ = localOffsetZ,
            width = width,
            height = height,
            depth = depth,
            yMin = yMin,
            color = color,
            extraRotationDeg = extraRotationDeg,
            transparent = transparent,
            roughness = roughness,
            metallic = metallic
        )
    }

    // --- Helper: Render Locally Tilted Box (tilted around local Z axis, e.g. leaning books) ---
    fun FrameScope.renderLocalTiltedBox(
        parentCenter: Point2D,
        parentRotationDeg: Float,
        localOffsetX: Float,
        localOffsetZ: Float,
        width: Float,
        height: Float,
        depth: Float,
        yMin: Float,
        tiltZDeg: Float,
        color: ColorRGBA,
        roughness: Float = 0.4f,
        metallic: Float = 0.05f
    ) {
        if (width <= 0.001f || height <= 0.001f || depth <= 0.001f) return
        val rad = -parentRotationDeg * (PI.toFloat() / 180f)
        val tiltRad = -tiltZDeg * (PI.toFloat() / 180f)
        val cosA = cos(rad)
        val sinA = sin(rad)
        val wx = (parentCenter.x / 100f) + (localOffsetX * cosA + localOffsetZ * sinA)
        val wz = (parentCenter.y / 100f) + (-localOffsetX * sinA + localOffsetZ * cosA)
        val wy = yMin + height * 0.5f

        val t = Transform.scale(width, height, depth)
            .rotate(Vec3(0f, 0f, 1f), tiltRad)
            .rotate(Vec3(0f, 1f, 0f), rad)
            .translate(wx, wy, wz)

        Renderable(
            material = base {
                this.color = color
                this.roughnessFactor = roughness
                this.metallicFactor = metallic
            },
            mesh = cube(0.5f),
            transform = t,
            transparent = false
        )
    }

    // --- Helper: Render Organic Puffy Pillow / Soft Cushion ---
    fun FrameScope.renderLocalPillow(
        parentCenter: Point2D,
        parentRotationDeg: Float,
        localOffsetX: Float,
        localOffsetZ: Float,
        width: Float,
        height: Float,
        depth: Float,
        yMin: Float,
        color: ColorRGBA,
        extraRotationDeg: Float = 0f,
        roughness: Float = 0.65f
    ) {
        with(KorenderMeshFactory) {
            renderLocalMesh(
                parentCenter = parentCenter,
                parentRotationDeg = parentRotationDeg,
                localOffsetX = localOffsetX,
                localOffsetZ = localOffsetZ,
                scaleX = width,
                scaleY = height,
                scaleZ = depth,
                yMin = yMin,
                mesh = pillow(),
                color = color,
                extraRotationDeg = extraRotationDeg,
                roughness = roughness,
                metallic = 0.02f
            )
        }
    }

    // --- Helper: Render Smooth Torus Ring ---
    fun FrameScope.renderLocalTorus(
        parentCenter: Point2D,
        parentRotationDeg: Float,
        localOffsetX: Float,
        localOffsetZ: Float,
        outerDiameter: Float,
        tubeThickness: Float,
        yMin: Float,
        color: ColorRGBA,
        extraRotationDeg: Float = 0f,
        roughness: Float = 0.2f,
        metallic: Float = 0.9f
    ) {
        val majorR = (outerDiameter - tubeThickness) * 0.5f
        val minorR = tubeThickness * 0.5f
        with(KorenderMeshFactory) {
            renderLocalMesh(
                parentCenter = parentCenter,
                parentRotationDeg = parentRotationDeg,
                localOffsetX = localOffsetX,
                localOffsetZ = localOffsetZ,
                scaleX = 1f,
                scaleY = 1f,
                scaleZ = 1f,
                yMin = yMin,
                mesh = torus(majorR, minorR),
                color = color,
                extraRotationDeg = extraRotationDeg,
                roughness = roughness,
                metallic = metallic
            )
        }
    }

    // =========================================================================
    // 1. Room Floor Slabs (Exact polygon geometry matching 2D walls)
    // =========================================================================

    private fun FrameScope.renderFloorSlabs(
        document: PlanDocument,
        selectedId: String?
    ) {
        val vertices = document.vertices
        for (room in document.rooms) {
            val poly2D = room.getPolygonPoints(vertices)
            if (poly2D.size < 3) continue

            val isSelected = room.id == selectedId
            val floorColor = if (isSelected) ColorRGBA(0.75f, 0.86f, 0.99f, 1f) else ParquetWoodColor

            val polyMeters = poly2D.map { Point2D(it.x / 100f, it.y / 100f) }
            val triangles = PolygonTriangulator.triangulate(polyMeters)
            if (triangles.isEmpty()) continue

            val collector = MeshCollector()
            val n = polyMeters.size
            val topBase = collector.positions.size

            // 1. Top floor walking surface
            for (pt in polyMeters) {
                collector.positions.add(Vec3(pt.x, 0.015f, pt.y))
                collector.normals.add(Vec3(0f, 1f, 0f))
                collector.texCoords.add(Vec2(pt.x, pt.y))
            }
            for (i in triangles.indices step 3) {
                val i0 = topBase + triangles[i]
                val i1 = topBase + triangles[i + 1]
                val i2 = topBase + triangles[i + 2]
                collector.indices.add(i0)
                collector.indices.add(i1)
                collector.indices.add(i2)
                collector.indices.add(i0)
                collector.indices.add(i2)
                collector.indices.add(i1)
            }

            // 2. Solid edge perimeter skirt (down to y = -0.005m)
            for (i in 0 until n) {
                val j = (i + 1) % n
                val pi = polyMeters[i]
                val pj = polyMeters[j]
                val dir = (pj - pi).normalized()
                val norm = Vec3(-dir.y, 0f, dir.x)

                val ti = Vec3(pi.x, 0.015f, pi.y)
                val tj = Vec3(pj.x, 0.015f, pj.y)
                val bj = Vec3(pj.x, -0.005f, pj.y)
                val bi = Vec3(pi.x, -0.005f, pi.y)

                collector.addQuad(ti, bi, bj, tj, norm)
            }

            val meshDecl = customMesh(
                id = "floor_${room.id}_${poly2D.hashCode()}",
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

            Renderable(
                material = base {
                    this.color = floorColor
                    this.roughnessFactor = 0.45f
                    this.metallicFactor = 0.02f
                },
                mesh = meshDecl,
                transform = Transform(),
                transparent = false
            )
        }
    }

    // =========================================================================
    // 2. Extruded Solid Walls with Cutouts (True 2D Miter Joins)
    // =========================================================================

    private data class OpeningMeters(
        val item: PlacedItem,
        val s0: Float,
        val s1: Float,
        val ySill: Float,
        val yHeader: Float
    )

    private fun FrameScope.renderWalls(
        document: PlanDocument,
        heightMode: WallHeightMode3D,
        selectedId: String?
    ) {
        val vertices = document.vertices
        val walls = document.walls

        val isXRay = heightMode == WallHeightMode3D.XRAY_TRANSPARENT
        val wallAlpha = if (isXRay) 0.35f else 1.0f

        // Precompute connected walls for corner pillars
        val vertexWalls = mutableMapOf<String, MutableList<WallSegment>>()
        for (v in vertices.keys) vertexWalls[v] = mutableListOf()
        for (w in walls.values) {
            vertexWalls[w.startVertexId]?.add(w)
            vertexWalls[w.endVertexId]?.add(w)
        }

        for (wall in walls.values) {
            val vStartPt = vertices[wall.startVertexId]?.position ?: continue
            val vEndPt = vertices[wall.endVertexId]?.position ?: continue

            val v0 = Point2D(vStartPt.x / 100f, vStartPt.y / 100f)
            val v1 = Point2D(vEndPt.x / 100f, vEndPt.y / 100f)

            val wallVec = v1 - v0
            val wallLen = wallVec.length()
            if (wallLen < 0.02f) continue
            val wallDir = wallVec.normalized()

            val wallAngleDeg = Math.toDegrees(atan2(wallVec.y.toDouble(), wallVec.x.toDouble())).toFloat()

            val wallThick = wall.thicknessCm / 100f

            val baseWallH = if (wall.heightCm > 10f) wall.heightCm / 100f else 2.80f
            val targetWallH = when (heightMode) {
                WallHeightMode3D.CUTAWAY_120CM -> 1.10f
                WallHeightMode3D.FULL_HEIGHT -> baseWallH
                WallHeightMode3D.XRAY_TRANSPARENT -> baseWallH
            }

            val isSelected = wall.id == selectedId
            val wallColor = when {
                isSelected -> WallSelectedColor
                wall.wallType == WallType.EXTERIOR -> WallExteriorColor
                else -> WallInteriorColor
            }.let { ColorRGBA(it.r, it.g, it.b, wallAlpha) }

            // Find hosted openings along this wall
            val openings = mutableListOf<OpeningMeters>()
            for (item in document.items.values) {
                if (!item.itemType.isOpening) continue

                val isAttached = item.wallId == wall.id ||
                        (item.wallId == null && isPointNearWall(item.position, vStartPt, vEndPt, wall.thicknessCm))
                if (!isAttached) continue

                val itemPosM = Point2D(item.position.x / 100f, item.position.y / 100f)
                val toItem = itemPosM - v0
                val distAlong = toItem.dot(wallDir)
                val halfW = (item.widthCm / 100f) * 0.5f

                val s0 = (distAlong - halfW).coerceIn(0f, wallLen)
                val s1 = (distAlong + halfW).coerceIn(0f, wallLen)
                if (s1 - s0 < 0.02f) continue

                val isWindow = item.itemType in listOf(
                    ItemType.WINDOW_SINGLE, ItemType.WINDOW_DOUBLE, ItemType.WINDOW_TRIPLE,
                    ItemType.WINDOW_PANORAMIC, ItemType.WINDOW_CORNER, ItemType.WINDOW_CASHIER,
                    ItemType.OPENING_TECH_HATCH
                ) || item.elevationCm > 0f
                val elevM = item.elevationCm / 100f
                val hM = item.heightCm / 100f

                val ySill = if (isWindow) min(elevM, targetWallH) else 0f
                val rawHeader = if (isWindow) elevM + hM else hM
                val yHeader = min(rawHeader, targetWallH)

                openings.add(OpeningMeters(item, s0, s1, ySill, yHeader))
            }

            openings.sortBy { it.s0 }

            fun renderWallPart(sFrom: Float, sTo: Float, yBottom: Float, yTop: Float) {
                val partLen = sTo - sFrom
                if (partLen < 0.005f) return
                val partH = yTop - yBottom
                if (partH < 0.005f) return

                val sMid = (sFrom + sTo) * 0.5f
                val midPt = v0 + wallDir * sMid
                val midY = yBottom + partH * 0.5f

                renderBox(
                    centerX = midPt.x,
                    centerY = midY,
                    centerZ = midPt.y,
                    width = partLen,
                    height = partH,
                    depth = wallThick,
                    rotationDeg = wallAngleDeg,
                    color = wallColor,
                    transparent = isXRay,
                    roughness = 0.4f,
                    metallic = 0.05f
                )
            }

            if (openings.isEmpty()) {
                renderWallPart(0f, wallLen, 0f, targetWallH)
            } else {
                var currentS = 0f
                for (op in openings) {
                    if (op.s0 > currentS + 0.005f) {
                        renderWallPart(currentS, op.s0, 0f, targetWallH)
                    }
                    if (op.ySill > 0.02f) {
                        renderWallPart(op.s0, op.s1, 0f, op.ySill)
                    }
                    if (targetWallH > op.yHeader + 0.02f) {
                        renderWallPart(op.s0, op.s1, op.yHeader, targetWallH)
                    }
                    currentS = max(currentS, op.s1)
                }
                if (currentS < wallLen - 0.005f) {
                    renderWallPart(currentS, wallLen, 0f, targetWallH)
                }
            }
        }

        // Corner junction blocks at vertices where walls meet (fills corner gaps with straight rectangular geometry)
        for ((vId, vWalls) in vertexWalls) {
            if (vWalls.size < 2) continue
            val vPt = vertices[vId]?.position ?: continue
            val vx = vPt.x / 100f
            val vz = vPt.y / 100f
            val maxThick = vWalls.maxOf { it.thicknessCm } / 100f
            val maxH = vWalls.maxOf { if (it.heightCm > 10f) it.heightCm / 100f else 2.80f }
            val targetH = when (heightMode) {
                WallHeightMode3D.CUTAWAY_120CM -> 1.10f
                WallHeightMode3D.FULL_HEIGHT -> maxH
                WallHeightMode3D.XRAY_TRANSPARENT -> maxH
            }
            val hasSelected = vWalls.any { it.id == selectedId }
            val colColor = when {
                hasSelected -> WallSelectedColor
                vWalls.any { it.wallType == WallType.EXTERIOR } -> WallExteriorColor
                else -> WallInteriorColor
            }.let { ColorRGBA(it.r, it.g, it.b, wallAlpha) }

            renderBox(
                centerX = vx, centerY = targetH * 0.5f, centerZ = vz,
                width = maxThick, height = targetH, depth = maxThick,
                color = colColor, transparent = isXRay,
                roughness = 0.4f, metallic = 0.05f
            )
        }
    }

    // --- Mesh Collector for exact 3D extruded prisms and polygons ---
    private class MeshCollector {
        val positions = mutableListOf<Vec3>()
        val normals = mutableListOf<Vec3>()
        val texCoords = mutableListOf<Vec2>()
        val indices = mutableListOf<Int>()

        fun addQuad(
            v0: Vec3, v1: Vec3, v2: Vec3, v3: Vec3,
            normal: Vec3,
            uv0: Vec2 = Vec2(0f, 0f),
            uv1: Vec2 = Vec2(1f, 0f),
            uv2: Vec2 = Vec2(1f, 1f),
            uv3: Vec2 = Vec2(0f, 1f)
        ) {
            val baseIndex = positions.size
            positions.add(v0); positions.add(v1); positions.add(v2); positions.add(v3)
            normals.add(normal); normals.add(normal); normals.add(normal); normals.add(normal)
            texCoords.add(uv0); texCoords.add(uv1); texCoords.add(uv2); texCoords.add(uv3)
            indices.add(baseIndex)
            indices.add(baseIndex + 1)
            indices.add(baseIndex + 2)
            indices.add(baseIndex)
            indices.add(baseIndex + 2)
            indices.add(baseIndex + 3)
        }
    }

    // --- Ear-clipping Polygon Triangulator for arbitrary 2D room floors ---
    private object PolygonTriangulator {
        fun triangulate(polygon: List<Point2D>): List<Int> {
            val n = polygon.size
            if (n < 3) return emptyList()
            if (n == 3) return listOf(0, 1, 2)

            var area = 0.0
            for (i in 0 until n) {
                val j = (i + 1) % n
                area += polygon[i].x * polygon[j].y - polygon[j].x * polygon[i].y
            }
            val isCcw = area > 0.0

            val indices = MutableList(n) { it }
            if (!isCcw) {
                indices.reverse()
            }

            val result = mutableListOf<Int>()
            var count = indices.size
            var attempts = 0
            val maxAttempts = count * count

            var i = 0
            while (count > 2 && attempts < maxAttempts) {
                attempts++
                val prevIdx = indices[(i + count - 1) % count]
                val currIdx = indices[i % count]
                val nextIdx = indices[(i + 1) % count]

                val a = polygon[prevIdx]
                val b = polygon[currIdx]
                val c = polygon[nextIdx]

                val cross = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
                val isConvex = if (isCcw) cross > 0.00001f else cross < -0.00001f

                if (isConvex) {
                    var hasPointInside = false
                    for (k in 0 until count) {
                        val pIdx = indices[k]
                        if (pIdx == prevIdx || pIdx == currIdx || pIdx == nextIdx) continue
                        if (isPointInTriangle(polygon[pIdx], a, b, c)) {
                            hasPointInside = true
                            break
                        }
                    }

                    if (!hasPointInside) {
                        result.add(prevIdx)
                        result.add(currIdx)
                        result.add(nextIdx)
                        indices.removeAt(i % count)
                        count--
                        i = 0
                        continue
                    }
                }
                i = (i + 1) % count
            }

            if (result.size == (n - 2) * 3) {
                return result
            }

            // Fallback fan triangulation
            result.clear()
            for (k in 1 until n - 1) {
                result.add(0)
                result.add(k)
                result.add(k + 1)
            }
            return result
        }

        private fun isPointInTriangle(p: Point2D, a: Point2D, b: Point2D, c: Point2D): Boolean {
            val d1 = sign(p, a, b)
            val d2 = sign(p, b, c)
            val d3 = sign(p, c, a)

            val hasNeg = (d1 < -0.00001f) || (d2 < -0.00001f) || (d3 < -0.00001f)
            val hasPos = (d1 > 0.00001f) || (d2 > 0.00001f) || (d3 > 0.00001f)

            return !(hasNeg && hasPos)
        }

        private fun sign(p1: Point2D, p2: Point2D, p3: Point2D): Float {
            return (p1.x - p3.x) * (p2.y - p3.y) - (p2.x - p3.x) * (p1.y - p3.y)
        }
    }

    private fun isPointNearWall(pt: Point2D, vStart: Point2D, vEnd: Point2D, thickCm: Float): Boolean {
        val seg = LineSegment(vStart, vEnd)
        return pt.distanceTo(seg.closestPointTo(pt)) <= thickCm * 1.5f
    }

    // =========================================================================
    // 3. Placed Items: Furniture, Doors, Windows
    // =========================================================================

    private fun FrameScope.renderItem(
        item: PlacedItem,
        isSelected: Boolean,
        heightMode: WallHeightMode3D = WallHeightMode3D.FULL_HEIGHT
    ) {
        when (item.itemType) {
            ItemType.DOOR_SINGLE -> {
                renderDoor(item, isSelected, heightMode)
            }
            ItemType.DOOR_DOUBLE, ItemType.DOOR_TECH_DOUBLE -> {
                renderDoubleDoor(item, isSelected, heightMode)
            }
            ItemType.DOOR_SLIDING -> {
                renderSlidingDoor(item, isSelected, heightMode)
            }
            ItemType.DOOR_POCKET -> {
                renderPocketDoor(item, isSelected, heightMode)
            }
            ItemType.DOOR_ENTRANCE, ItemType.DOOR_SERVER_RACK, ItemType.DOOR_FIREPROOF -> {
                renderEntranceDoor(item, isSelected, heightMode)
            }
            ItemType.OPENING_ARCH, ItemType.OPENING_TECH_HATCH -> {
                renderArchOpening(item, isSelected, heightMode)
            }
            ItemType.WINDOW_SINGLE, ItemType.WINDOW_DOUBLE, ItemType.WINDOW_TRIPLE,
            ItemType.WINDOW_PANORAMIC, ItemType.WINDOW_CORNER, ItemType.WINDOW_CASHIER -> {
                renderWindow(item, isSelected, heightMode)
            }
            ItemType.SOFA_STRAIGHT -> {
                renderSofa(item, isSelected)
            }
            ItemType.SOFA_CORNER -> {
                renderCornerSofa(item, isSelected)
            }
            ItemType.SOFA_U_SHAPED -> {
                renderUShapedSofa(item, isSelected)
            }
            ItemType.ARMCHAIR -> {
                renderArmchair(item, isSelected)
            }
            ItemType.COFFEE_TABLE, ItemType.COFFEE_TABLE_ROUND -> {
                renderCoffeeTable(item, isSelected)
            }
            ItemType.TV_STAND -> {
                renderTvStand(item, isSelected)
            }
            ItemType.BOOKSHELF -> {
                renderBookshelf(item, isSelected)
            }
            ItemType.FLOOR_LAMP -> {
                renderFloorLamp(item, isSelected)
            }
            ItemType.CARPET_RECT -> {
                renderCarpet(item, isSelected)
            }
            ItemType.DINING_TABLE_4, ItemType.DINING_TABLE_6, ItemType.DINING_TABLE_ROUND -> {
                renderDiningSet(item, isSelected)
            }
            ItemType.BED_DOUBLE, ItemType.BED_SINGLE -> {
                renderBed(item, isSelected)
            }
            ItemType.BED_BUNK -> {
                renderBunkBed(item, isSelected)
            }
            ItemType.WARDROBE -> {
                renderWardrobe(item, isSelected)
            }
            ItemType.WARDROBE_CORNER -> {
                renderCornerWardrobe(item, isSelected)
            }
            ItemType.DRESSER -> {
                renderDresser(item, isSelected)
            }
            ItemType.NIGHTSTAND -> {
                renderNightstand(item, isSelected)
            }
            ItemType.KITCHEN_COUNTER -> {
                renderKitchenCounter(item, isSelected)
            }
            ItemType.KITCHEN_ISLAND -> {
                renderKitchenIsland(item, isSelected)
            }
            ItemType.COOKING_STOVE -> {
                renderCookingStove(item, isSelected)
            }
            ItemType.REFRIGERATOR, ItemType.REFRIGERATOR_SIDE_BY_SIDE -> {
                renderRefrigerator(item, isSelected)
            }
            ItemType.BATHTUB -> {
                renderBathtub(item, isSelected)
            }
            ItemType.BATHTUB_FREESTANDING -> {
                renderFreestandingBathtub(item, isSelected)
            }
            ItemType.SHOWER_CABIN -> {
                renderShowerCabin(item, isSelected)
            }
            ItemType.TOILET -> {
                renderToilet(item, isSelected)
            }
            ItemType.BIDET -> {
                renderBidet(item, isSelected)
            }
            ItemType.WASHBASIN, ItemType.WASHBASIN_DOUBLE -> {
                renderWashbasin(item, isSelected)
            }
            ItemType.WASHING_MACHINE -> {
                renderWashingMachine(item, isSelected)
            }
            ItemType.DESK_OFFICE -> {
                renderOfficeDesk(item, isSelected)
            }
            ItemType.DESK_CORNER -> {
                renderCornerDesk(item, isSelected)
            }
            ItemType.OFFICE_CHAIR -> {
                renderOfficeChair(item, isSelected)
            }
            ItemType.FILING_CABINET -> {
                renderFilingCabinet(item, isSelected)
            }
            ItemType.SERVER_RACK_42U, ItemType.SERVER_CABINET_DOUBLE -> {
                renderServerRack(item, isSelected)
            }
            ItemType.PC_WORKSTATION -> {
                renderPcWorkstation(item, isSelected)
            }
            ItemType.DISPATCH_CONSOLE -> {
                renderDispatchConsole(item, isSelected)
            }
            ItemType.CONFERENCE_TABLE -> {
                renderConferenceTable(item, isSelected)
            }
            ItemType.METAL_RACK_STORAGE -> {
                renderMetalRackStorage(item, isSelected)
            }
            ItemType.RECEPTION_DESK -> {
                renderReceptionDesk(item, isSelected)
            }
            ItemType.UPS_INDUSTRIAL, ItemType.DATACENTER_COOLING -> {
                renderIndustrialEquipment(item, isSelected)
            }
            ItemType.HVAC_DIFFUSER_SUPPLY -> {
                renderHvacDiffuser(item, isSelected)
            }
            ItemType.HVAC_DIFFUSER_ROUND -> {
                renderHvacRoundDiffuser(item, isSelected)
            }
            ItemType.HVAC_GRILLE_EXHAUST -> {
                renderHvacGrille(item, isSelected)
            }
            ItemType.HVAC_AIR_UNIT -> {
                renderHvacAirUnit(item, isSelected)
            }
            ItemType.HVAC_FAN -> {
                renderHvacFan(item, isSelected)
            }
            ItemType.HVAC_DUCT_RECT -> {
                renderHvacDuct(item, isSelected)
            }
            ItemType.HVAC_AC_INDOOR_SPLIT -> {
                renderHvacAcIndoor(item, isSelected)
            }
            ItemType.HVAC_AC_OUTDOOR -> {
                renderHvacAcOutdoor(item, isSelected)
            }
        }
    }

    // =========================================================================
    // 4. Designer Scandinavian Sofa (Диван)
    // =========================================================================

    private fun FrameScope.renderSofa(item: PlacedItem, isSelected: Boolean) {
        val baseColor = if (isSelected) WallSelectedColor else FabricSofaColor
        val cushionCol = if (isSelected) WallSelectedColor else CushionColor

        val w = item.widthCm / 100f
        val d = item.depthCm / 100f
        val halfW = w * 0.5f

        // Local orientation:
        // Local -Z is FRONT (facing forward towards coffee table)
        // Local +Z is BACK (backrest against wall)

        // 1. Four Walnut Turned Tapered Legs with Polished Brass Ferrules
        val legH = 0.14f
        val ferruleH = 0.035f
        val legX = halfW - 0.10f
        val legZ = d * 0.5f - 0.10f
        for (dx in listOf(-legX, legX)) {
            for (dz in listOf(-legZ, legZ)) {
                // Tapered conical leg
                renderLocalFrustum(
                    parentCenter = item.position,
                    parentRotationDeg = item.rotationDegrees,
                    localOffsetX = dx,
                    localOffsetZ = dz,
                    topDiameter = 0.052f,
                    bottomDiameter = 0.028f,
                    height = legH - ferruleH,
                    yMin = ferruleH,
                    color = WoodLegColor,
                    roughness = 0.35f
                )
                // Polished brass shoe ferrule
                renderLocalCylinder(
                    parentCenter = item.position,
                    parentRotationDeg = item.rotationDegrees,
                    localOffsetX = dx,
                    localOffsetZ = dz,
                    diameter = 0.034f,
                    height = ferruleH,
                    yMin = 0f,
                    color = BrassAccentColor,
                    metallic = 0.95f,
                    roughness = 0.15f
                )
            }
        }

        // 2. Base Platform Deck with Beveled Edge
        val deckH = 0.12f
        renderLocalBeveledBox(
            parentCenter = item.position,
            parentRotationDeg = item.rotationDegrees,
            localOffsetX = 0f,
            localOffsetZ = 0f,
            width = w,
            height = deckH,
            depth = d,
            yMin = legH,
            color = baseColor,
            roughness = 0.45f
        )

        // 3. Three Tailored Scandinavian Box Cushions with Piping Welts (Blender-quality)
        val armW = 0.13f
        val seatAreaW = w - armW * 2f
        val cushionW = (seatAreaW - 0.04f) / 3f
        val cushionDepth = d - 0.18f
        val seatY0 = legH + deckH
        val cushionH = 0.14f
        val pipingCol = ColorRGBA(cushionCol.r * 0.82f, cushionCol.g * 0.82f, cushionCol.b * 0.82f, 1f)

        for (i in 0..2) {
            val cx = -seatAreaW * 0.5f + cushionW * 0.5f + i * (cushionW + 0.02f)
            val cz = -0.04f

            // Main Tailored Box Cushion Block with Chamfered Edges
            renderLocalBeveledBox(
                parentCenter = item.position,
                parentRotationDeg = item.rotationDegrees,
                localOffsetX = cx,
                localOffsetZ = cz,
                width = cushionW - 0.012f,
                height = cushionH,
                depth = cushionDepth - 0.012f,
                yMin = seatY0,
                color = cushionCol,
                roughness = 0.52f
            )

            // Subtle Crowned Top Loft (gentle doming, not a balloon)
            renderLocalBeveledBox(
                parentCenter = item.position,
                parentRotationDeg = item.rotationDegrees,
                localOffsetX = cx,
                localOffsetZ = cz,
                width = cushionW - 0.045f,
                height = 0.025f,
                depth = cushionDepth - 0.045f,
                yMin = seatY0 + cushionH - 0.005f,
                color = cushionCol,
                roughness = 0.58f
            )

            // Tailored Front & Rear Piping Welts (Кант)
            val weltThick = 0.009f
            renderLocalBox(
                parentCenter = item.position,
                parentRotationDeg = item.rotationDegrees,
                localOffsetX = cx,
                localOffsetZ = cz - cushionDepth * 0.5f + 0.008f,
                width = cushionW - 0.012f,
                height = weltThick,
                depth = weltThick,
                yMin = seatY0 + cushionH - 0.004f,
                color = pipingCol
            )
            renderLocalBox(
                parentCenter = item.position,
                parentRotationDeg = item.rotationDegrees,
                localOffsetX = cx,
                localOffsetZ = cz + cushionDepth * 0.5f - 0.008f,
                width = cushionW - 0.012f,
                height = weltThick,
                depth = weltThick,
                yMin = seatY0 + cushionH - 0.004f,
                color = pipingCol
            )
        }

        // 4. Ergonomic Backrest Frame (+Z rear) with Beveled Edge
        val backrestThick = 0.18f
        val backrestZ = d * 0.5f - backrestThick * 0.5f
        val backrestH = 0.82f - seatY0
        renderLocalBeveledBox(
            parentCenter = item.position,
            parentRotationDeg = item.rotationDegrees,
            localOffsetX = 0f,
            localOffsetZ = backrestZ,
            width = w,
            height = backrestH,
            depth = backrestThick,
            yMin = seatY0,
            color = baseColor,
            roughness = 0.45f
        )

        // 5. Three Tailored Backrest Cushions with Soft Recline
        val backCushionH = 0.36f
        val backCushionThick = 0.13f
        val pillowZ = backrestZ - backrestThick * 0.5f - backCushionThick * 0.5f + 0.015f
        for (i in 0..2) {
            val cx = -seatAreaW * 0.5f + cushionW * 0.5f + i * (cushionW + 0.02f)
            renderLocalBeveledBox(
                parentCenter = item.position,
                parentRotationDeg = item.rotationDegrees,
                localOffsetX = cx,
                localOffsetZ = pillowZ,
                width = cushionW - 0.016f,
                height = backCushionH,
                depth = backCushionThick,
                yMin = seatY0 + cushionH,
                color = cushionCol,
                roughness = 0.55f
            )
            // Top crown on back cushion
            renderLocalBeveledBox(
                parentCenter = item.position,
                parentRotationDeg = item.rotationDegrees,
                localOffsetX = cx,
                localOffsetZ = pillowZ,
                width = cushionW - 0.045f,
                height = 0.022f,
                depth = backCushionThick - 0.035f,
                yMin = seatY0 + cushionH + backCushionH - 0.005f,
                color = cushionCol,
                roughness = 0.58f
            )
        }

        // 6. Left & Right Armrests with Soft Chamfers & Top Caps
        val armH = 0.62f - legH
        val armOffset = halfW - armW * 0.5f
        renderLocalBeveledBox(
            parentCenter = item.position,
            parentRotationDeg = item.rotationDegrees,
            localOffsetX = -armOffset,
            localOffsetZ = 0f,
            width = armW,
            height = armH,
            depth = d,
            yMin = legH,
            color = baseColor,
            roughness = 0.45f
        )
        renderLocalBeveledBox(
            parentCenter = item.position,
            parentRotationDeg = item.rotationDegrees,
            localOffsetX = armOffset,
            localOffsetZ = 0f,
            width = armW,
            height = armH,
            depth = d,
            yMin = legH,
            color = baseColor,
            roughness = 0.45f
        )
        // Soft tailored armrest pads
        for (ax in listOf(-armOffset, armOffset)) {
            renderLocalBeveledBox(
                parentCenter = item.position,
                parentRotationDeg = item.rotationDegrees,
                localOffsetX = ax,
                localOffsetZ = 0f,
                width = armW + 0.012f,
                height = 0.035f,
                depth = d - 0.02f,
                yMin = legH + armH - 0.015f,
                color = baseColor,
                roughness = 0.50f
            )
        }

        // 7. Designer Accent Throw Pillows with Angle Tilt
        renderLocalBeveledBox(
            parentCenter = item.position,
            parentRotationDeg = item.rotationDegrees,
            localOffsetX = -halfW + armW + 0.14f,
            localOffsetZ = backrestZ - 0.16f,
            width = 0.32f,
            height = 0.30f,
            depth = 0.10f,
            yMin = seatY0 + cushionH,
            color = AccentPillow1Color,
            extraRotationDeg = 20f,
            roughness = 0.55f
        )
        renderLocalBeveledBox(
            parentCenter = item.position,
            parentRotationDeg = item.rotationDegrees,
            localOffsetX = halfW - armW - 0.14f,
            localOffsetZ = backrestZ - 0.16f,
            width = 0.30f,
            height = 0.28f,
            depth = 0.09f,
            yMin = seatY0 + cushionH,
            color = AccentPillow2Color,
            extraRotationDeg = -22f,
            roughness = 0.55f
        )
    }

    // 4b. Designer Scandinavian Corner L-Sofa (Угловой L-диван)
    private fun FrameScope.renderCornerSofa(item: PlacedItem, isSelected: Boolean) {
        val baseColor = if (isSelected) WallSelectedColor else FabricSofaColor
        val cushionCol = if (isSelected) WallSelectedColor else CushionColor

        val w = item.widthCm / 100f
        val d = item.depthCm / 100f
        val halfW = w * 0.5f
        val halfD = d * 0.5f

        val cutW = w * 0.45f
        val cutH = d * 0.45f
        val mainSeatD = d - cutH
        val chaiseW = w - cutW
        val legH = 0.14f
        val ferruleH = 0.035f
        val deckH = 0.12f
        val seatY0 = legH + deckH
        val cushionH = 0.16f

        // 1. Turned tapered legs with brass ferrules
        val legPositions = listOf(
            Pair(-halfW + 0.08f, halfD - 0.08f),
            Pair(halfW - 0.08f, halfD - 0.08f),
            Pair(halfW - 0.08f, -halfD + cutH + 0.08f),
            Pair(halfW - cutW + 0.08f, -halfD + cutH + 0.08f),
            Pair(halfW - cutW - 0.08f, -halfD + 0.08f),
            Pair(-halfW + 0.08f, -halfD + 0.08f)
        )
        for (pos in legPositions) {
            renderLocalFrustum(item.position, item.rotationDegrees, pos.first, pos.second, 0.052f, 0.028f, legH - ferruleH, ferruleH, WoodLegColor, roughness = 0.35f)
            renderLocalCylinder(item.position, item.rotationDegrees, pos.first, pos.second, 0.034f, ferruleH, 0f, BrassAccentColor, metallic = 0.95f, roughness = 0.15f)
        }

        // 2. Base platform deck with beveled edges (two joined sections forming L)
        val rearDeckCenterZ = halfD - mainSeatD * 0.5f
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, rearDeckCenterZ, w, deckH, mainSeatD, legH, baseColor, roughness = 0.45f)
        val chaiseDeckCenterX = -halfW + chaiseW * 0.5f
        val chaiseDeckCenterZ = -halfD + cutH * 0.5f
        renderLocalBeveledBox(item.position, item.rotationDegrees, chaiseDeckCenterX, chaiseDeckCenterZ, chaiseW, deckH, cutH, legH, baseColor, roughness = 0.45f)

        // 3. Continuous L-shaped backrest with beveled edges
        val backThick = 0.18f
        val backH = 0.82f - seatY0
        // Rear backrest (+Z)
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, halfD - backThick * 0.5f, w, backH, backThick, seatY0, baseColor, roughness = 0.45f)
        // Left side backrest/armrest (-X)
        renderLocalBeveledBox(item.position, item.rotationDegrees, -halfW + backThick * 0.5f, 0f, backThick, backH, d, seatY0, baseColor, roughness = 0.45f)
        // Right outer armrest
        val armW = 0.12f
        renderLocalBeveledBox(item.position, item.rotationDegrees, halfW - armW * 0.5f, rearDeckCenterZ, armW, 0.62f - legH, mainSeatD, legH, baseColor, roughness = 0.45f)

        // 4. Tailored Scandinavian Box Cushions
        // Main seat cushions (right side)
        val mainCushionW = (w - chaiseW - armW - 0.04f) * 0.5f
        val mainCushionD = mainSeatD - backThick - 0.04f
        val mainCushionZ = halfD - backThick - mainCushionD * 0.5f
        for (i in 0..1) {
            val cx = halfW - armW - mainCushionW * 0.5f - i * (mainCushionW + 0.02f)
            renderLocalBeveledBox(item.position, item.rotationDegrees, cx, mainCushionZ, mainCushionW - 0.012f, cushionH, mainCushionD - 0.012f, seatY0, cushionCol, roughness = 0.52f)
            renderLocalBeveledBox(item.position, item.rotationDegrees, cx, mainCushionZ, mainCushionW - 0.045f, 0.022f, mainCushionD - 0.045f, seatY0 + cushionH - 0.005f, cushionCol, roughness = 0.58f)
        }
        // Chaise cushion (left side)
        val chaiseCushionW = chaiseW - backThick - 0.04f
        val chaiseCushionD = d - backThick - 0.06f
        val chaiseCushionX = -halfW + backThick + chaiseCushionW * 0.5f
        renderLocalBeveledBox(item.position, item.rotationDegrees, chaiseCushionX, 0f, chaiseCushionW - 0.012f, cushionH, chaiseCushionD - 0.012f, seatY0, cushionCol, roughness = 0.52f)
        renderLocalBeveledBox(item.position, item.rotationDegrees, chaiseCushionX, 0f, chaiseCushionW - 0.05f, 0.022f, chaiseCushionD - 0.05f, seatY0 + cushionH - 0.005f, cushionCol, roughness = 0.58f)

        // 5. Backrest Cushions & Throw Pillows
        val pillowH = 0.36f
        val pillowThick = 0.13f
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0.10f, halfD - backThick - pillowThick * 0.5f, w * 0.5f, pillowH, pillowThick, seatY0 + cushionH, cushionCol, roughness = 0.55f)
        // Corner decorative accent pillows
        renderLocalBeveledBox(item.position, item.rotationDegrees, -halfW + backThick + 0.16f, halfD - backThick - 0.16f, 0.30f, 0.30f, 0.10f, seatY0 + cushionH, AccentPillow1Color, extraRotationDeg = 25f, roughness = 0.55f)
        renderLocalBeveledBox(item.position, item.rotationDegrees, halfW - armW - 0.16f, halfD - backThick - 0.14f, 0.28f, 0.28f, 0.10f, seatY0 + cushionH, AccentPillow2Color, extraRotationDeg = -15f, roughness = 0.55f)
    }

    // 4c. Modular Scandinavian U-Shaped Sofa (П-образный модульный диван)
    private fun FrameScope.renderUShapedSofa(item: PlacedItem, isSelected: Boolean) {
        val baseColor = if (isSelected) WallSelectedColor else FabricSofaColor
        val cushionCol = if (isSelected) WallSelectedColor else CushionColor

        val w = item.widthCm / 100f
        val d = item.depthCm / 100f
        val halfW = w * 0.5f
        val halfD = d * 0.5f

        val cutW = w * 0.42f
        val cutH = d * 0.55f
        val wingW = (w - cutW) * 0.5f
        val rearD = d - cutH
        val legH = 0.14f
        val ferruleH = 0.035f
        val deckH = 0.12f
        val seatY0 = legH + deckH
        val cushionH = 0.16f

        // 1. Turned tapered legs with brass ferrules at 8 perimeter corners
        val legPositions = listOf(
            Pair(-halfW + 0.08f, halfD - 0.08f),
            Pair(halfW - 0.08f, halfD - 0.08f),
            Pair(halfW - 0.08f, -halfD + 0.08f),
            Pair(halfW - wingW + 0.08f, -halfD + 0.08f),
            Pair(halfW - wingW + 0.08f, halfD - rearD + 0.08f),
            Pair(-halfW + wingW - 0.08f, halfD - rearD + 0.08f),
            Pair(-halfW + wingW - 0.08f, -halfD + 0.08f),
            Pair(-halfW + 0.08f, -halfD + 0.08f)
        )
        for (pos in legPositions) {
            renderLocalFrustum(item.position, item.rotationDegrees, pos.first, pos.second, 0.052f, 0.028f, legH - ferruleH, ferruleH, WoodLegColor, roughness = 0.35f)
            renderLocalCylinder(item.position, item.rotationDegrees, pos.first, pos.second, 0.034f, ferruleH, 0f, BrassAccentColor, metallic = 0.95f, roughness = 0.15f)
        }

        // 2. Base platform deck in U-shape with beveled edges
        val rearZ = halfD - rearD * 0.5f
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, rearZ, w, deckH, rearD, legH, baseColor, roughness = 0.45f)
        val leftX = -halfW + wingW * 0.5f
        val rightX = halfW - wingW * 0.5f
        val wingZ = -halfD + cutH * 0.5f
        renderLocalBeveledBox(item.position, item.rotationDegrees, leftX, wingZ, wingW, deckH, cutH, legH, baseColor, roughness = 0.45f)
        renderLocalBeveledBox(item.position, item.rotationDegrees, rightX, wingZ, wingW, deckH, cutH, legH, baseColor, roughness = 0.45f)

        // 3. Beveled Backrests
        val backThick = 0.18f
        val backH = 0.82f - seatY0
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, halfD - backThick * 0.5f, w, backH, backThick, seatY0, baseColor, roughness = 0.45f)
        renderLocalBeveledBox(item.position, item.rotationDegrees, -halfW + backThick * 0.5f, 0f, backThick, backH, d, seatY0, baseColor, roughness = 0.45f)
        renderLocalBeveledBox(item.position, item.rotationDegrees, halfW - backThick * 0.5f, 0f, backThick, backH, d, seatY0, baseColor, roughness = 0.45f)

        // 4. Tailored Cushions
        val centerCushionW = cutW * 0.5f - 0.02f
        val centerCushionD = rearD - backThick - 0.04f
        val centerCushionZ = halfD - backThick - centerCushionD * 0.5f
        renderLocalBeveledBox(item.position, item.rotationDegrees, -centerCushionW * 0.55f, centerCushionZ, centerCushionW - 0.012f, cushionH, centerCushionD - 0.012f, seatY0, cushionCol, roughness = 0.52f)
        renderLocalBeveledBox(item.position, item.rotationDegrees, centerCushionW * 0.55f, centerCushionZ, centerCushionW - 0.012f, cushionH, centerCushionD - 0.012f, seatY0, cushionCol, roughness = 0.52f)

        val wingCushionW = wingW - backThick - 0.04f
        val wingCushionD = d - backThick - 0.06f
        renderLocalBeveledBox(item.position, item.rotationDegrees, -halfW + backThick + wingCushionW * 0.5f, 0f, wingCushionW - 0.012f, cushionH, wingCushionD - 0.012f, seatY0, cushionCol, roughness = 0.52f)
        renderLocalBeveledBox(item.position, item.rotationDegrees, halfW - backThick - wingCushionW * 0.5f, 0f, wingCushionW - 0.012f, cushionH, wingCushionD - 0.012f, seatY0, cushionCol, roughness = 0.52f)

        // 5. Corner throw pillows
        renderLocalBeveledBox(item.position, item.rotationDegrees, -halfW + backThick + 0.16f, halfD - backThick - 0.16f, 0.30f, 0.30f, 0.10f, seatY0 + cushionH, AccentPillow1Color, extraRotationDeg = 25f, roughness = 0.55f)
        renderLocalBeveledBox(item.position, item.rotationDegrees, halfW - backThick - 0.16f, halfD - backThick - 0.16f, 0.30f, 0.30f, 0.10f, seatY0 + cushionH, AccentPillow2Color, extraRotationDeg = -25f, roughness = 0.55f)
    }

    // =========================================================================
    // 5. Designer Coffee Table (Журнальный столик)
    // =========================================================================

    private fun FrameScope.renderCoffeeTable(item: PlacedItem, isSelected: Boolean) {
        val topColor = if (isSelected) WallSelectedColor else CoffeeTableTopColor
        val shelfColor = if (isSelected) WallSelectedColor else CoffeeTableShelfColor

        val w = item.widthCm / 100f
        val d = item.depthCm / 100f
        val tableH = 0.45f

        if (item.itemType == ItemType.COFFEE_TABLE_ROUND) {
            val dia = min(w, d)
            // 1. Smooth 24-gon round tabletop slab with chamfered edge profile
            renderLocalCylinder(item.position, item.rotationDegrees, 0f, 0f, dia, 0.035f, tableH - 0.035f, topColor, roughness = 0.28f, segments = 28)
            // 2. Lower round shelf at 14cm
            val shelfDia = dia - 0.16f
            renderLocalCylinder(item.position, item.rotationDegrees, 0f, 0f, shelfDia, 0.022f, 0.14f, shelfColor, roughness = 0.32f, segments = 28)
            // 3. Four Tapered Turned Legs with Polished Brass Shoes
            val legR = dia * 0.5f - 0.06f
            for (i in 0 until 4) {
                val ang = (i * 90f + 45f) * (PI.toFloat() / 180f)
                val lx = cos(ang) * legR
                val lz = sin(ang) * legR
                renderLocalFrustum(item.position, item.rotationDegrees, lx, lz, 0.036f, 0.022f, tableH - 0.06f, 0.025f, CoffeeTableLegColor, roughness = 0.3f, metallic = 0.6f)
                renderLocalCylinder(item.position, item.rotationDegrees, lx, lz, 0.026f, 0.025f, 0f, BrassAccentColor, roughness = 0.15f, metallic = 0.95f)
            }
            return
        }

        // 1. Natural Solid Oak Tabletop Slab with CAD Bevels (catches light reflections)
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w, 0.035f, d, tableH - 0.035f, topColor, roughness = 0.28f)

        // 2. Lower Storage Shelf at 14cm with Bevels
        val shelfW = w - 0.14f
        val shelfD = d - 0.12f
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, shelfW, 0.022f, shelfD, 0.14f, shelfColor, roughness = 0.32f)

        // 3. Four Tapered Steel Legs with Polished Brass Shoes
        val legX = w * 0.5f - 0.05f
        val legZ = d * 0.5f - 0.05f
        for (dx in listOf(-legX, legX)) {
            for (dz in listOf(-legZ, legZ)) {
                renderLocalFrustum(item.position, item.rotationDegrees, dx, dz, 0.038f, 0.024f, tableH - 0.06f, 0.025f, CoffeeTableLegColor, roughness = 0.3f, metallic = 0.7f)
                renderLocalCylinder(item.position, item.rotationDegrees, dx, dz, 0.028f, 0.025f, 0f, BrassAccentColor, roughness = 0.15f, metallic = 0.95f)
            }
        }
    }

    // =========================================================================
    // 6. Architectural Doors & Windows
    // =========================================================================

    private fun FrameScope.renderDoor(
        item: PlacedItem,
        isSelected: Boolean,
        heightMode: WallHeightMode3D = WallHeightMode3D.FULL_HEIGHT
    ) {
        val frameColor = if (isSelected) WallSelectedColor else DoorFrameColor
        val leafColor = if (isSelected) WallSelectedColor else DoorWoodColor

        val w = item.widthCm / 100f
        val rawH = item.heightCm / 100f
        val h = if (heightMode == WallHeightMode3D.CUTAWAY_120CM) min(rawH, 1.10f) else rawH
        val y0 = item.elevationCm / 100f
        val halfW = w * 0.5f
        val frameThick = 0.065f
        val frameDepth = max(item.depthCm / 100f, 0.15f)

        // 1. Profiled Architrave Casing (наличники с фасками)
        renderLocalBeveledBox(item.position, item.rotationDegrees, -halfW + frameThick * 0.5f, 0f, frameThick, h, frameDepth, y0, frameColor, roughness = 0.3f)
        renderLocalBeveledBox(item.position, item.rotationDegrees, halfW - frameThick * 0.5f, 0f, frameThick, h, frameDepth, y0, frameColor, roughness = 0.3f)
        if (heightMode != WallHeightMode3D.CUTAWAY_120CM) {
            renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w + 0.04f, frameThick, frameDepth, y0 + h - frameThick, frameColor, roughness = 0.3f)
            renderLocalBox(item.position, item.rotationDegrees, 0f, 0f, w - frameThick * 2f, 0.012f, 0.015f, y0 + h - frameThick - 0.012f, CharcoalMetalColor)
        }

        // Brass / Anodized aluminum floor threshold strip
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w, 0.015f, frameDepth * 0.85f, y0, BrassAccentColor, metallic = 0.85f, roughness = 0.2f)

        if (item.itemType == ItemType.OPENING_ARCH) return

        // 2. Door Leaf Open at 35°
        val leafW = w - frameThick * 2f
        val leafH = h - frameThick - 0.015f
        val leafThick = 0.044f

        val hingeSideX = if (item.flipHorizontal) halfW - frameThick else -halfW + frameThick
        val openAngleDeg = if (item.flipHorizontal) -35f else 35f
        val openRad = openAngleDeg * (PI.toFloat() / 180f)

        // Center of swung door leaf
        val leafMidLocalX = hingeSideX + (leafW * 0.5f) * cos(openRad) * (if (item.flipHorizontal) -1f else 1f)
        val leafMidLocalZ = (leafW * 0.5f) * sin(openRad) * (if (item.flipHorizontal) -1f else 1f)

        // Main Door Leaf with beveled edges
        renderLocalBeveledBox(
            parentCenter = item.position,
            parentRotationDeg = item.rotationDegrees,
            localOffsetX = leafMidLocalX,
            localOffsetZ = leafMidLocalZ,
            width = leafW, height = leafH, depth = leafThick,
            yMin = y0 + 0.015f,
            color = leafColor,
            extraRotationDeg = openAngleDeg,
            roughness = 0.32f
        )

        // Classical / Shaker Molded Panels (филёнки) on front and rear faces
        val panelW = leafW * 0.68f
        val panelH1 = leafH * 0.44f // Upper panel
        val panelH2 = leafH * 0.32f // Lower panel
        val panelThick = 0.008f
        for (sideZ in listOf(-leafThick * 0.5f - 0.002f, leafThick * 0.5f + 0.002f)) {
            val pMidX = hingeSideX + (leafW * 0.5f) * cos(openRad) * (if (item.flipHorizontal) -1f else 1f) - sideZ * sin(openRad) * (if (item.flipHorizontal) -1f else 1f)
            val pMidZ = (leafW * 0.5f) * sin(openRad) * (if (item.flipHorizontal) -1f else 1f) + sideZ * cos(openRad)

            // Lower recessed molded panel
            renderLocalBeveledBox(item.position, item.rotationDegrees, pMidX, pMidZ, panelW, panelH2, panelThick, y0 + 0.12f, ColorRGBA(leafColor.r * 0.92f, leafColor.g * 0.92f, leafColor.b * 0.92f, 1f), extraRotationDeg = openAngleDeg, roughness = 0.35f)
            // Upper recessed molded panel
            renderLocalBeveledBox(item.position, item.rotationDegrees, pMidX, pMidZ, panelW, panelH1, panelThick, y0 + 0.18f + panelH2, ColorRGBA(leafColor.r * 0.92f, leafColor.g * 0.92f, leafColor.b * 0.92f, 1f), extraRotationDeg = openAngleDeg, roughness = 0.35f)
        }

        // 3. Three Heavy Architectural Butt Hinges along hinge jamb
        for (hy in listOf(y0 + 0.25f, y0 + h * 0.5f, y0 + h - 0.28f)) {
            renderLocalCylinder(item.position, item.rotationDegrees, hingeSideX, 0f, 0.022f, 0.09f, hy, ChromeColor, metallic = 0.95f, roughness = 0.15f)
            renderLocalSphere(item.position, item.rotationDegrees, hingeSideX, 0f, 0.024f, hy + 0.09f, ChromeColor, metallic = 0.95f)
            renderLocalSphere(item.position, item.rotationDegrees, hingeSideX, 0f, 0.024f, hy - 0.012f, ChromeColor, metallic = 0.95f)
        }

        // 4. Designer Italian Lever Handle & Rosette Set
        val latchDist = leafW - 0.08f
        val latchLocalX = hingeSideX + latchDist * cos(openRad) * (if (item.flipHorizontal) -1f else 1f)
        val latchLocalZ = latchDist * sin(openRad) * (if (item.flipHorizontal) -1f else 1f)

        // Circular beveled handle rosettes on both sides
        renderLocalCylinder(item.position, item.rotationDegrees, latchLocalX, latchLocalZ, 0.055f, leafThick + 0.018f, y0 + 0.95f, ChromeColor, metallic = 0.95f, roughness = 0.12f)
        // Cast curved lever handle
        renderLocalCylinder(item.position, item.rotationDegrees, latchLocalX, latchLocalZ - 0.035f, 0.020f, 0.035f, y0 + 0.965f, ChromeColor, metallic = 0.95f, roughness = 0.12f)
        renderLocalBeveledBox(item.position, item.rotationDegrees, latchLocalX - 0.055f, latchLocalZ - 0.045f, 0.13f, 0.022f, 0.020f, y0 + 0.975f, ChromeColor, extraRotationDeg = openAngleDeg, metallic = 0.95f, roughness = 0.12f)

        // Keyhole escutcheon rosette below handle
        renderLocalCylinder(item.position, item.rotationDegrees, latchLocalX, latchLocalZ, 0.045f, leafThick + 0.014f, y0 + 0.86f, ChromeColor, metallic = 0.95f, roughness = 0.15f)
        renderLocalBox(item.position, item.rotationDegrees, latchLocalX, latchLocalZ - 0.015f, 0.008f, 0.018f, 0.008f, y0 + 0.87f, CharcoalMetalColor)

        // Protruding chrome latch bolt tongue on door edge
        val edgeLocalX = hingeSideX + leafW * cos(openRad) * (if (item.flipHorizontal) -1f else 1f)
        val edgeLocalZ = leafW * sin(openRad) * (if (item.flipHorizontal) -1f else 1f)
        renderLocalBox(item.position, item.rotationDegrees, edgeLocalX, edgeLocalZ, 0.015f, 0.025f, 0.022f, y0 + 0.965f, ChromeColor, extraRotationDeg = openAngleDeg, metallic = 0.95f)
    }

    // 6b. Modern Sliding Door (Раздвижная дверь-купе)
    private fun FrameScope.renderSlidingDoor(
        item: PlacedItem,
        isSelected: Boolean,
        heightMode: WallHeightMode3D = WallHeightMode3D.FULL_HEIGHT
    ) {
        val frameColor = if (isSelected) WallSelectedColor else DoorFrameColor
        val leafColor = if (isSelected) WallSelectedColor else DoorWoodColor

        val w = item.widthCm / 100f
        val rawH = item.heightCm / 100f
        val h = if (heightMode == WallHeightMode3D.CUTAWAY_120CM) min(rawH, 1.10f) else rawH
        val y0 = item.elevationCm / 100f
        val halfW = w * 0.5f
        val frameThick = 0.055f
        val frameDepth = max(item.depthCm / 100f, 0.15f)

        // 1. Frame casing jambs with beveled profile
        renderLocalBeveledBox(item.position, item.rotationDegrees, -halfW + frameThick * 0.5f, 0f, frameThick, h, frameDepth, y0, frameColor, roughness = 0.3f)
        renderLocalBeveledBox(item.position, item.rotationDegrees, halfW - frameThick * 0.5f, 0f, frameThick, h, frameDepth, y0, frameColor, roughness = 0.3f)

        // 2. Heavy top overhead sliding rail track box
        val trackH = if (heightMode == WallHeightMode3D.CUTAWAY_120CM) 0f else 0.075f
        if (heightMode != WallHeightMode3D.CUTAWAY_120CM) {
            renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w, trackH, frameDepth, y0 + h - trackH, frameColor, roughness = 0.3f)
            // Brushed aluminum sliding track trim with dual roller channels
            renderLocalBox(item.position, item.rotationDegrees, 0f, 0f, w - frameThick * 2f, 0.015f, 0.08f, y0 + h - trackH, ChromeColor, metallic = 0.9f, roughness = 0.2f)
        }

        // 3. Bottom concealed guide rail
        val bottomRailH = 0.015f
        renderLocalBox(item.position, item.rotationDegrees, 0f, 0f, w, bottomRailH, 0.08f, y0, frameColor)

        // 4. Two bypass sliding panels (створки купе)
        val innerW = w - frameThick * 2f
        val panelOverlap = 0.06f
        val panelW = (innerW + panelOverlap) * 0.5f
        val panelH = h - trackH - bottomRailH - 0.01f
        val panelThick = 0.038f

        // Panel 1: Rear track (Z = -0.024m), closed on the left side
        val p1LocalX = -innerW * 0.5f + panelW * 0.5f
        val p1LocalZ = -0.024f
        renderLocalBeveledBox(item.position, item.rotationDegrees, p1LocalX, p1LocalZ, panelW, panelH, panelThick, y0 + bottomRailH, leafColor, roughness = 0.32f)
        // Panel 1 aluminum frame edges
        renderLocalBox(item.position, item.rotationDegrees, p1LocalX - panelW * 0.5f + 0.018f, p1LocalZ, 0.035f, panelH, panelThick + 0.004f, y0 + bottomRailH, frameColor)
        renderLocalBox(item.position, item.rotationDegrees, p1LocalX + panelW * 0.5f - 0.018f, p1LocalZ, 0.035f, panelH, panelThick + 0.004f, y0 + bottomRailH, frameColor)
        // Panel 1 flush vertical sliding cup handle
        renderLocalBox(item.position, item.rotationDegrees, p1LocalX + panelW * 0.5f - 0.045f, p1LocalZ, 0.022f, 0.18f, panelThick + 0.008f, y0 + 0.92f, ChromeColor, metallic = 0.95f, roughness = 0.15f)

        // Panel 2: Front track (Z = +0.024m), slightly slid open by 0.18m for realism
        val slideOffset = min(panelW * 0.35f, 0.22f)
        val p2LocalX = innerW * 0.5f - panelW * 0.5f - slideOffset
        val p2LocalZ = 0.024f
        renderLocalBeveledBox(item.position, item.rotationDegrees, p2LocalX, p2LocalZ, panelW, panelH, panelThick, y0 + bottomRailH, leafColor, roughness = 0.32f)
        // Panel 2 aluminum frame edges
        renderLocalBox(item.position, item.rotationDegrees, p2LocalX - panelW * 0.5f + 0.018f, p2LocalZ, 0.035f, panelH, panelThick + 0.004f, y0 + bottomRailH, frameColor)
        renderLocalBox(item.position, item.rotationDegrees, p2LocalX + panelW * 0.5f - 0.018f, p2LocalZ, 0.035f, panelH, panelThick + 0.004f, y0 + bottomRailH, frameColor)
        // Panel 2 flush vertical sliding cup handle
        renderLocalBox(item.position, item.rotationDegrees, p2LocalX - panelW * 0.5f + 0.045f, p2LocalZ, 0.022f, 0.18f, panelThick + 0.008f, y0 + 0.92f, ChromeColor, metallic = 0.95f, roughness = 0.15f)
    }

    // 6c. French Double Swing Door (Двустворчатая дверь)
    private fun FrameScope.renderDoubleDoor(
        item: PlacedItem,
        isSelected: Boolean,
        heightMode: WallHeightMode3D = WallHeightMode3D.FULL_HEIGHT
    ) {
        val frameColor = if (isSelected) WallSelectedColor else DoorFrameColor
        val leafColor = DoorWoodColor // Preserve realistic warm oak wood texture even when selected

        val w = item.widthCm / 100f
        val rawH = item.heightCm / 100f
        val h = if (heightMode == WallHeightMode3D.CUTAWAY_120CM) min(rawH, 1.10f) else rawH
        val y0 = item.elevationCm / 100f
        val halfW = w * 0.5f
        val frameThick = 0.065f
        val frameDepth = max(item.depthCm / 100f, 0.15f)

        // 1. Frame casing with beveled architraves
        renderLocalBeveledBox(item.position, item.rotationDegrees, -halfW + frameThick * 0.5f, 0f, frameThick, h, frameDepth, y0, frameColor, roughness = 0.3f)
        renderLocalBeveledBox(item.position, item.rotationDegrees, halfW - frameThick * 0.5f, 0f, frameThick, h, frameDepth, y0, frameColor, roughness = 0.3f)
        if (heightMode != WallHeightMode3D.CUTAWAY_120CM) {
            renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w + 0.04f, frameThick, frameDepth, y0 + h - frameThick, frameColor, roughness = 0.3f)
        }
        // Polished brass threshold (elevated slightly above 15mm parquet slab to eliminate z-fighting)
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w, 0.012f, frameDepth * 0.85f, y0 + 0.016f, BrassAccentColor, metallic = 0.85f)

        val innerW = w - frameThick * 2f
        val leafW = innerW * 0.5f - 0.005f
        val leafH = h - frameThick - 0.015f
        val leafThick = 0.044f

        // Swing open angle into the room (+Z)
        val openAngle = 30f
        val openRad = openAngle * (PI.toFloat() / 180f)

        // 2. Left Leaf (Hinged on left jamb, opens forward into +Z)
        val leftHingeX = -halfW + frameThick
        val leftLeafMidX = leftHingeX + (leafW * 0.5f) * cos(openRad)
        val leftLeafMidZ = (leafW * 0.5f) * sin(openRad)
        renderLocalBeveledBox(item.position, item.rotationDegrees, leftLeafMidX, leftLeafMidZ, leafW, leafH, leafThick, y0 + 0.016f, leafColor, extraRotationDeg = openAngle, roughness = 0.32f)

        // Left molded panels (classic French door styling)
        val panelW = leafW * 0.65f
        val panelH = leafH * 0.38f
        for (sideZ in listOf(-leafThick * 0.5f - 0.002f, leafThick * 0.5f + 0.002f)) {
            val pMidX = leftLeafMidX - sideZ * sin(openRad)
            val pMidZ = leftLeafMidZ + sideZ * cos(openRad)
            renderLocalBeveledBox(item.position, item.rotationDegrees, pMidX, pMidZ, panelW, panelH, 0.008f, y0 + 0.15f, ColorRGBA(leafColor.r * 0.90f, leafColor.g * 0.90f, leafColor.b * 0.90f, 1f), extraRotationDeg = openAngle, roughness = 0.35f)
            renderLocalBeveledBox(item.position, item.rotationDegrees, pMidX, pMidZ, panelW, panelH, 0.008f, y0 + 0.25f + panelH, ColorRGBA(leafColor.r * 0.90f, leafColor.g * 0.90f, leafColor.b * 0.90f, 1f), extraRotationDeg = openAngle, roughness = 0.35f)
        }

        // Left chrome lever handle & rosette
        val latchDist = leafW - 0.06f
        val leftLatchX = leftHingeX + latchDist * cos(openRad)
        val leftLatchZ = latchDist * sin(openRad)
        renderLocalCylinder(item.position, item.rotationDegrees, leftLatchX, leftLatchZ, 0.055f, leafThick + 0.018f, y0 + 0.95f, ChromeColor, metallic = 0.95f, roughness = 0.12f)
        val leftLeverX = leftLatchX - 0.055f * cos(openRad)
        val leftLeverZ = leftLatchZ - 0.055f * sin(openRad) + 0.035f
        renderLocalBeveledBox(item.position, item.rotationDegrees, leftLeverX, leftLeverZ, 0.13f, 0.022f, 0.020f, y0 + 0.975f, ChromeColor, extraRotationDeg = openAngle, metallic = 0.95f, roughness = 0.12f)

        // 3. Right Leaf (Hinged on right jamb, opens forward into +Z)
        val rightHingeX = halfW - frameThick
        val rightLeafMidX = rightHingeX - (leafW * 0.5f) * cos(openRad)
        val rightLeafMidZ = (leafW * 0.5f) * sin(openRad)
        renderLocalBeveledBox(item.position, item.rotationDegrees, rightLeafMidX, rightLeafMidZ, leafW, leafH, leafThick, y0 + 0.016f, leafColor, extraRotationDeg = 180f - openAngle, roughness = 0.32f)

        // Right molded panels
        for (sideZ in listOf(-leafThick * 0.5f - 0.002f, leafThick * 0.5f + 0.002f)) {
            val pMidX = rightLeafMidX - sideZ * sin(openRad)
            val pMidZ = rightLeafMidZ - sideZ * cos(openRad)
            renderLocalBeveledBox(item.position, item.rotationDegrees, pMidX, pMidZ, panelW, panelH, 0.008f, y0 + 0.15f, ColorRGBA(leafColor.r * 0.90f, leafColor.g * 0.90f, leafColor.b * 0.90f, 1f), extraRotationDeg = 180f - openAngle, roughness = 0.35f)
            renderLocalBeveledBox(item.position, item.rotationDegrees, pMidX, pMidZ, panelW, panelH, 0.008f, y0 + 0.25f + panelH, ColorRGBA(leafColor.r * 0.90f, leafColor.g * 0.90f, leafColor.b * 0.90f, 1f), extraRotationDeg = 180f - openAngle, roughness = 0.35f)
        }

        // Right chrome lever handle & rosette
        val rightLatchX = rightHingeX - latchDist * cos(openRad)
        val rightLatchZ = latchDist * sin(openRad)
        renderLocalCylinder(item.position, item.rotationDegrees, rightLatchX, rightLatchZ, 0.055f, leafThick + 0.018f, y0 + 0.95f, ChromeColor, metallic = 0.95f, roughness = 0.12f)
        val rightLeverX = rightLatchX + 0.055f * cos(openRad)
        val rightLeverZ = rightLatchZ - 0.055f * sin(openRad) + 0.035f
        renderLocalBeveledBox(item.position, item.rotationDegrees, rightLeverX, rightLeverZ, 0.13f, 0.022f, 0.020f, y0 + 0.975f, ChromeColor, extraRotationDeg = 180f - openAngle, metallic = 0.95f, roughness = 0.12f)

        // Symmetrical hinges on both side jambs
        for (hy in listOf(y0 + 0.25f, y0 + h - 0.28f)) {
            renderLocalCylinder(item.position, item.rotationDegrees, leftHingeX, 0f, 0.022f, 0.08f, hy, ChromeColor, metallic = 0.95f)
            renderLocalCylinder(item.position, item.rotationDegrees, rightHingeX, 0f, 0.022f, 0.08f, hy, ChromeColor, metallic = 0.95f)
        }
    }

    // 6d. Recessed Pocket Door (Дверь скрытая в пенал)
    private fun FrameScope.renderPocketDoor(
        item: PlacedItem,
        isSelected: Boolean,
        heightMode: WallHeightMode3D = WallHeightMode3D.FULL_HEIGHT
    ) {
        val frameColor = if (isSelected) WallSelectedColor else DoorFrameColor
        val leafColor = if (isSelected) WallSelectedColor else DoorWoodColor

        val w = item.widthCm / 100f
        val rawH = item.heightCm / 100f
        val h = if (heightMode == WallHeightMode3D.CUTAWAY_120CM) min(rawH, 1.10f) else rawH
        val y0 = item.elevationCm / 100f
        val halfW = w * 0.5f
        val frameThick = 0.06f
        val frameDepth = max(item.depthCm / 100f, 0.15f)

        // Frame jambs & header
        renderLocalBeveledBox(item.position, item.rotationDegrees, -halfW + frameThick * 0.5f, 0f, frameThick, h, frameDepth, y0, frameColor, roughness = 0.3f)
        renderLocalBeveledBox(item.position, item.rotationDegrees, halfW - frameThick * 0.5f, 0f, frameThick, h, frameDepth, y0, frameColor, roughness = 0.3f)
        if (heightMode != WallHeightMode3D.CUTAWAY_120CM) {
            renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w, frameThick, frameDepth, y0 + h - frameThick, frameColor, roughness = 0.3f)
        }

        // Pocket door leaf sliding into wall recess (half visible)
        val leafW = w - frameThick * 2f
        val leafH = h - frameThick - 0.01f
        val leafThick = 0.040f
        val visibleW = leafW * 0.55f
        val leafCenterX = -halfW + frameThick + visibleW * 0.5f

        renderLocalBeveledBox(item.position, item.rotationDegrees, leafCenterX, 0f, visibleW, leafH, leafThick, y0 + 0.01f, leafColor, roughness = 0.32f)

        // Flush circular pocket cup pull ring handle
        val cupX = leafCenterX + visibleW * 0.5f - 0.05f
        renderLocalCylinder(item.position, item.rotationDegrees, cupX, 0f, 0.055f, leafThick + 0.006f, y0 + 0.95f, ChromeColor, metallic = 0.95f, roughness = 0.12f)
        renderLocalTorus(item.position, item.rotationDegrees, cupX, 0f, 0.042f, 0.008f, y0 + 0.95f, ChromeColor)
    }

    // 6e. Reinforced Entrance Security Door (Входная дверь)
    private fun FrameScope.renderEntranceDoor(
        item: PlacedItem,
        isSelected: Boolean,
        heightMode: WallHeightMode3D = WallHeightMode3D.FULL_HEIGHT
    ) {
        val frameColor = if (isSelected) WallSelectedColor else ColorRGBA(0.12f, 0.15f, 0.18f, 1f)
        val leafColor = if (isSelected) WallSelectedColor else ColorRGBA(0.18f, 0.22f, 0.26f, 1f)

        val w = item.widthCm / 100f
        val rawH = item.heightCm / 100f
        val h = if (heightMode == WallHeightMode3D.CUTAWAY_120CM) min(rawH, 1.10f) else rawH
        val y0 = item.elevationCm / 100f
        val halfW = w * 0.5f
        val frameThick = 0.075f
        val frameDepth = max(item.depthCm / 100f, 0.18f)

        // 1. Reinforced steel frame jambs, header & threshold
        renderLocalBeveledBox(item.position, item.rotationDegrees, -halfW + frameThick * 0.5f, 0f, frameThick, h, frameDepth, y0, frameColor, metallic = 0.65f, roughness = 0.25f)
        renderLocalBeveledBox(item.position, item.rotationDegrees, halfW - frameThick * 0.5f, 0f, frameThick, h, frameDepth, y0, frameColor, metallic = 0.65f, roughness = 0.25f)
        if (heightMode != WallHeightMode3D.CUTAWAY_120CM) {
            renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w + 0.04f, frameThick, frameDepth, y0 + h - frameThick, frameColor, metallic = 0.65f, roughness = 0.25f)
        }
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w, 0.028f, frameDepth, y0, frameColor, metallic = 0.75f, roughness = 0.2f)

        // 2. Heavy armored steel leaf open at 35°
        val leafW = w - frameThick * 2f
        val leafH = h - frameThick - 0.03f
        val leafThick = 0.065f

        val hingeSideX = if (item.flipHorizontal) halfW - frameThick else -halfW + frameThick
        val openAngleDeg = if (item.flipHorizontal) -35f else 35f
        val openRad = openAngleDeg * (PI.toFloat() / 180f)

        val leafMidLocalX = hingeSideX + (leafW * 0.5f) * cos(openRad) * (if (item.flipHorizontal) -1f else 1f)
        val leafMidLocalZ = (leafW * 0.5f) * sin(openRad) * (if (item.flipHorizontal) -1f else 1f)

        renderLocalBeveledBox(
            item.position, item.rotationDegrees, leafMidLocalX, leafMidLocalZ, leafW, leafH, leafThick, y0 + 0.028f,
            leafColor, extraRotationDeg = openAngleDeg, roughness = 0.22f, metallic = 0.45f
        )

        // Horizontal architectural V-groove fluting inserts
        for (g in 0..5) {
            val gy = y0 + 0.30f + g * 0.26f
            renderLocalBeveledBox(
                item.position, item.rotationDegrees, leafMidLocalX, leafMidLocalZ - (leafThick * 0.5f + 0.003f) * cos(openRad),
                leafW * 0.85f, 0.018f, 0.008f, gy,
                StainlessSteelColor, extraRotationDeg = openAngleDeg, metallic = 0.85f, roughness = 0.2f
            )
        }

        // 3. Wide-Angle Brass Optical Peephole with optic lens ring at 1.50m
        val peepDist = leafW * 0.5f
        val peepX = hingeSideX + peepDist * cos(openRad) * (if (item.flipHorizontal) -1f else 1f)
        val peepZ = peepDist * sin(openRad) * (if (item.flipHorizontal) -1f else 1f)
        renderLocalCylinder(item.position, item.rotationDegrees, peepX, peepZ, 0.032f, leafThick + 0.016f, y0 + 1.50f, BrassAccentColor, extraRotationDeg = openAngleDeg, metallic = 0.95f)
        renderLocalTorus(item.position, item.rotationDegrees, peepX, peepZ - (leafThick * 0.5f + 0.008f) * cos(openRad), 0.038f, 0.006f, y0 + 1.50f, BrassAccentColor)

        // 4. Modern Luxury 1.20m Stainless Tubular Bar Handle on standoffs
        val latchDist = leafW - 0.09f
        val latchX = hingeSideX + latchDist * cos(openRad) * (if (item.flipHorizontal) -1f else 1f)
        val latchZ = latchDist * sin(openRad) * (if (item.flipHorizontal) -1f else 1f)
        val barH = 1.20f
        val barY = y0 + 0.55f

        // Standoff pillars
        for (sy in listOf(barY + 0.15f, barY + barH - 0.15f)) {
            renderLocalCylinder(item.position, item.rotationDegrees, latchX, latchZ, 0.028f, 0.05f, sy, StainlessSteelColor, metallic = 0.95f)
        }
        // Main tubular bar
        val barOffsetZ = latchZ - 0.05f * cos(openRad)
        val barOffsetX = latchX + 0.05f * sin(openRad)
        renderLocalCylinder(item.position, item.rotationDegrees, barOffsetX, barOffsetZ, 0.032f, barH, barY, StainlessSteelColor, extraRotationDeg = openAngleDeg, metallic = 0.95f, roughness = 0.12f)

        // Armored cylinder lock escutcheon plate
        renderLocalBeveledBox(item.position, item.rotationDegrees, latchX, latchZ, 0.045f, 0.12f, leafThick + 0.012f, y0 + 0.88f, StainlessSteelColor, extraRotationDeg = openAngleDeg, metallic = 0.95f)
    }

    // 6f. Architectural Portal Opening (Портал / арочный проем)
    private fun FrameScope.renderArchOpening(
        item: PlacedItem,
        isSelected: Boolean,
        heightMode: WallHeightMode3D = WallHeightMode3D.FULL_HEIGHT
    ) {
        val frameColor = if (isSelected) WallSelectedColor else DoorFrameColor
        val w = item.widthCm / 100f
        val rawH = item.heightCm / 100f
        val h = if (heightMode == WallHeightMode3D.CUTAWAY_120CM) min(rawH, 1.10f) else rawH
        val y0 = item.elevationCm / 100f
        val halfW = w * 0.5f
        val frameThick = 0.065f
        val frameDepth = max(item.depthCm / 100f, 0.16f)

        // Portal casing with beveled architraves
        renderLocalBeveledBox(item.position, item.rotationDegrees, -halfW + frameThick * 0.5f, 0f, frameThick, h, frameDepth, y0, frameColor, roughness = 0.35f)
        renderLocalBeveledBox(item.position, item.rotationDegrees, halfW - frameThick * 0.5f, 0f, frameThick, h, frameDepth, y0, frameColor, roughness = 0.35f)
        if (heightMode != WallHeightMode3D.CUTAWAY_120CM) {
            renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w + 0.04f, frameThick, frameDepth, y0 + h - frameThick, frameColor, roughness = 0.35f)
        }
    }

    private fun FrameScope.renderWindow(
        item: PlacedItem,
        isSelected: Boolean,
        heightMode: WallHeightMode3D = WallHeightMode3D.FULL_HEIGHT
    ) {
        val frameColor = if (isSelected) WallSelectedColor else WindowFrameColor

        val w = item.widthCm / 100f
        val isPanoramic = (item.itemType == ItemType.WINDOW_PANORAMIC)
        val y0 = if (isPanoramic) 0f else (item.elevationCm / 100f)

        if (heightMode == WallHeightMode3D.CUTAWAY_120CM && y0 >= 1.05f) {
            // Cutaway mode slices walls at 1.10m - window is above cut plane
            return
        }

        val fullH = item.heightCm / 100f
        val h = if (heightMode == WallHeightMode3D.CUTAWAY_120CM) min(fullH, max(0.20f, 1.10f - y0)) else fullH
        if (h <= 0.08f) return

        val halfW = w * 0.5f
        val frameThick = if (isPanoramic) 0.045f else 0.065f
        val frameDepth = max(item.depthCm / 100f, 0.15f)

        // 1. Profiled Window Frame Jambs, Header & Sill with Bevels
        renderLocalBeveledBox(item.position, item.rotationDegrees, -halfW + frameThick * 0.5f, 0f, frameThick, h, frameDepth, y0, frameColor, roughness = 0.3f)
        renderLocalBeveledBox(item.position, item.rotationDegrees, halfW - frameThick * 0.5f, 0f, frameThick, h, frameDepth, y0, frameColor, roughness = 0.3f)
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w, frameThick, frameDepth, y0, frameColor, roughness = 0.3f)
        if (heightMode != WallHeightMode3D.CUTAWAY_120CM) {
            renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w, frameThick, frameDepth, y0 + h - frameThick, frameColor, roughness = 0.3f)
        }

        val glassY0 = y0 + frameThick
        val glassH = if (heightMode == WallHeightMode3D.CUTAWAY_120CM) max(0.05f, h - frameThick) else max(0.05f, h - frameThick * 2f)
        val glassThick = 0.024f

        when (item.itemType) {
            ItemType.WINDOW_DOUBLE -> {
                // Center mullion divider with chamfers
                renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, frameThick, glassH, frameDepth * 0.85f, glassY0, frameColor, roughness = 0.3f)
                val paneW = (w - frameThick * 3f) * 0.5f
                renderLocalBox(item.position, item.rotationDegrees, -paneW * 0.5f - frameThick * 0.5f, 0f, paneW, glassH, glassThick, glassY0, GlassColor, transparent = true, roughness = 0.02f)
                renderLocalBox(item.position, item.rotationDegrees, paneW * 0.5f + frameThick * 0.5f, 0f, paneW, glassH, glassThick, glassY0, GlassColor, transparent = true, roughness = 0.02f)

                // Window handle on opening right sash
                val handleX = frameThick * 0.8f
                val handleY = glassY0 + glassH * 0.5f
                renderLocalCylinder(item.position, item.rotationDegrees, handleX, -0.02f, 0.038f, 0.012f, handleY - 0.019f, ChromeColor, metallic = 0.95f)
                renderLocalBeveledBox(item.position, item.rotationDegrees, handleX, -0.045f, 0.020f, 0.12f, 0.018f, handleY - 0.08f, ChromeColor, metallic = 0.95f, roughness = 0.12f)
            }
            ItemType.WINDOW_TRIPLE -> {
                val innerW = w - frameThick * 4f
                val paneW = innerW / 3f
                val m1X = -innerW * 0.5f + paneW + frameThick * 0.5f
                val m2X = innerW * 0.5f - paneW - frameThick * 0.5f
                renderLocalBeveledBox(item.position, item.rotationDegrees, m1X, 0f, frameThick, glassH, frameDepth * 0.85f, glassY0, frameColor, roughness = 0.3f)
                renderLocalBeveledBox(item.position, item.rotationDegrees, m2X, 0f, frameThick, glassH, frameDepth * 0.85f, glassY0, frameColor, roughness = 0.3f)

                val p1X = -innerW * 0.5f + paneW * 0.5f
                val p2X = 0f
                val p3X = innerW * 0.5f - paneW * 0.5f
                renderLocalBox(item.position, item.rotationDegrees, p1X, 0f, paneW, glassH, glassThick, glassY0, GlassColor, transparent = true, roughness = 0.02f)
                renderLocalBox(item.position, item.rotationDegrees, p2X, 0f, paneW, glassH, glassThick, glassY0, GlassColor, transparent = true, roughness = 0.02f)
                renderLocalBox(item.position, item.rotationDegrees, p3X, 0f, paneW, glassH, glassThick, glassY0, GlassColor, transparent = true, roughness = 0.02f)

                // Ergonomic handle on center pane
                val handleY = glassY0 + glassH * 0.5f
                renderLocalCylinder(item.position, item.rotationDegrees, paneW * 0.42f, -0.02f, 0.038f, 0.012f, handleY - 0.019f, ChromeColor, metallic = 0.95f)
                renderLocalBeveledBox(item.position, item.rotationDegrees, paneW * 0.42f, -0.045f, 0.020f, 0.12f, 0.018f, handleY - 0.08f, ChromeColor, metallic = 0.95f, roughness = 0.12f)
            }
            ItemType.WINDOW_PANORAMIC -> {
                renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, frameThick, glassH, frameDepth * 0.85f, glassY0, frameColor, roughness = 0.3f)
                val paneW = (w - frameThick * 3f) * 0.5f
                renderLocalBox(item.position, item.rotationDegrees, -paneW * 0.5f - frameThick * 0.5f, 0f, paneW, glassH, glassThick, glassY0, GlassColor, transparent = true, roughness = 0.02f)
                renderLocalBox(item.position, item.rotationDegrees, paneW * 0.5f + frameThick * 0.5f, 0f, paneW, glassH, glassThick, glassY0, GlassColor, transparent = true, roughness = 0.02f)
            }
            else -> {
                val paneW = w - frameThick * 2f
                renderLocalBox(item.position, item.rotationDegrees, 0f, 0f, paneW, glassH, glassThick, glassY0, GlassColor, transparent = true, roughness = 0.02f)
                if (item.itemType == ItemType.WINDOW_SINGLE) {
                    val handleX = halfW - frameThick - 0.04f
                    val handleY = glassY0 + glassH * 0.5f
                    renderLocalCylinder(item.position, item.rotationDegrees, handleX, -0.02f, 0.038f, 0.012f, handleY - 0.019f, ChromeColor, metallic = 0.95f)
                    renderLocalBeveledBox(item.position, item.rotationDegrees, handleX, -0.045f, 0.020f, 0.12f, 0.018f, handleY - 0.08f, ChromeColor, metallic = 0.95f, roughness = 0.12f)
                }
            }
        }

        // 2. Interior Polished Marble Window Sill with Bullnose Overhang (Подоконник)
        if (!isPanoramic) {
            val sillProtrusion = 0.05f
            val sillDepth = frameDepth + sillProtrusion
            val sillW = w + 0.08f // Side ears (ушки)
            val sillH = 0.036f
            val sillY = y0 - sillH

            // Polished stone slab with beveled edges
            renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, -sillProtrusion * 0.5f, sillW, sillH, sillDepth, sillY, WindowSillColor, roughness = 0.15f)

            // Exterior Aluminum Drip Ledge (Отлив)
            val dripDepth = 0.10f
            val dripZ = frameDepth * 0.5f + dripDepth * 0.5f
            renderLocalBox(item.position, item.rotationDegrees, 0f, dripZ, w + 0.04f, 0.018f, dripDepth, y0 - 0.02f, WindowLedgeColor, metallic = 0.85f, roughness = 0.25f)
            // Downward drip nose fold
            renderLocalBox(item.position, item.rotationDegrees, 0f, dripZ + dripDepth * 0.5f, w + 0.04f, 0.022f, 0.005f, y0 - 0.04f, WindowLedgeColor, metallic = 0.85f)
        }
    }

    // =========================================================================
    // 7. Catalog Furniture & Living Helpers
    // =========================================================================

    private fun FrameScope.renderArmchair(item: PlacedItem, isSelected: Boolean) {
        val baseColor = if (isSelected) WallSelectedColor else FabricSofaColor
        val cushionCol = if (isSelected) WallSelectedColor else CushionColor
        val w = item.widthCm / 100f
        val d = item.depthCm / 100f
        val halfW = w * 0.5f
        val legH = 0.16f
        val ferruleH = 0.035f

        // 1. 4 Turned Walnut Legs with Polished Brass Ferrules
        val legX = halfW - 0.08f
        val legZ = d * 0.5f - 0.08f
        for (dx in listOf(-legX, legX)) {
            for (dz in listOf(-legZ, legZ)) {
                renderLocalFrustum(
                    item.position, item.rotationDegrees,
                    dx, dz,
                    0.050f, 0.026f, legH - ferruleH, ferruleH,
                    WoodLegColor, roughness = 0.35f
                )
                renderLocalCylinder(
                    item.position, item.rotationDegrees,
                    dx, dz,
                    0.032f, ferruleH, 0f,
                    BrassAccentColor, metallic = 0.95f, roughness = 0.15f
                )
            }
        }

        // 2. Base platform deck with beveled edges
        val deckH = 0.10f
        renderLocalBeveledBox(
            item.position, item.rotationDegrees,
            0f, 0f, w, deckH, d, legH,
            baseColor, roughness = 0.45f
        )

        // 3. Tailored seat cushion: firm core + soft crowned loft
        val armW = 0.12f
        val seatY0 = legH + deckH
        val cushionH = 0.14f
        val cushionW = w - armW * 2f
        val cushionD = d - 0.16f
        renderLocalBeveledBox(
            item.position, item.rotationDegrees,
            0f, -0.04f, cushionW - 0.012f, cushionH, cushionD - 0.012f, seatY0,
            cushionCol, roughness = 0.52f
        )
        renderLocalBeveledBox(
            item.position, item.rotationDegrees,
            0f, -0.04f, cushionW - 0.04f, 0.022f, cushionD - 0.04f, seatY0 + cushionH - 0.005f,
            cushionCol, roughness = 0.58f
        )

        // 4. High ergonomic backrest with beveled profile (+Z)
        val backThick = 0.15f
        val backZ = d * 0.5f - backThick * 0.5f
        val backH = 0.86f - seatY0
        renderLocalBeveledBox(
            item.position, item.rotationDegrees,
            0f, backZ, w, backH, backThick, seatY0,
            baseColor, roughness = 0.45f
        )
        // Tailored lumbar cushion
        renderLocalBeveledBox(
            item.position, item.rotationDegrees,
            0f, backZ - backThick * 0.5f - 0.04f, cushionW * 0.90f, 0.28f, 0.09f, seatY0 + cushionH - 0.01f,
            cushionCol, roughness = 0.55f
        )

        // 5. Left & right armrests with soft rounded padding
        val armH = 0.60f - legH
        val armOffset = halfW - armW * 0.5f
        renderLocalBeveledBox(
            item.position, item.rotationDegrees,
            -armOffset, 0f, armW, armH, d, legH,
            baseColor, roughness = 0.45f
        )
        renderLocalBeveledBox(
            item.position, item.rotationDegrees,
            armOffset, 0f, armW, armH, d, legH,
            baseColor, roughness = 0.45f
        )
        for (ax in listOf(-armOffset, armOffset)) {
            renderLocalBeveledBox(
                item.position, item.rotationDegrees,
                ax, 0f, armW + 0.012f, 0.035f, d - 0.02f, legH + armH - 0.015f,
                baseColor, roughness = 0.50f
            )
        }

        // 6. Accent throw pillow with stylish angle tilt
        renderLocalBeveledBox(
            item.position, item.rotationDegrees,
            0f, backZ - 0.12f, cushionW * 0.68f, 0.26f, 0.08f, seatY0 + cushionH,
            AccentPillow1Color, extraRotationDeg = 12f, roughness = 0.55f
        )
    }

    private fun FrameScope.renderTvStand(item: PlacedItem, isSelected: Boolean) {
        val bodyColor = if (isSelected) WallSelectedColor else CoffeeTableShelfColor
        val topColor = if (isSelected) WallSelectedColor else CoffeeTableTopColor
        val w = item.widthCm / 100f
        val d = item.depthCm / 100f
        val halfW = w * 0.5f
        val halfD = d * 0.5f
        val legH = 0.12f
        val cabinetH = 0.36f
        val standH = legH + cabinetH

        // 1. 4 Tapered steel legs with polished brass shoes
        for (dx in listOf(-halfW + 0.06f, halfW - 0.06f)) {
            for (dz in listOf(-halfD + 0.06f, halfD - 0.06f)) {
                renderLocalFrustum(
                    item.position, item.rotationDegrees,
                    dx, dz, 0.034f, 0.020f, legH - 0.025f, 0.025f,
                    CoffeeTableLegColor, roughness = 0.3f, metallic = 0.7f
                )
                renderLocalCylinder(
                    item.position, item.rotationDegrees,
                    dx, dz, 0.025f, 0.025f, 0f,
                    BrassAccentColor, metallic = 0.95f, roughness = 0.15f
                )
            }
        }

        // 2. Cabinet body with beveled CAD edges
        renderLocalBeveledBox(
            item.position, item.rotationDegrees,
            0f, 0f, w, cabinetH, d, legH,
            bodyColor, roughness = 0.35f
        )
        // Solid oak top plate with beveled chamfers
        renderLocalBeveledBox(
            item.position, item.rotationDegrees,
            0f, 0f, w + 0.02f, 0.025f, d + 0.02f, standH - 0.025f,
            topColor, roughness = 0.28f
        )

        // 3. Cylindrical brass bar drawer handles on front facade (-Z)
        val handleZ = -halfD - 0.012f
        val handleY = legH + cabinetH * 0.5f
        val segW = w / 3f
        for (hx in listOf(-segW, 0f, segW)) {
            renderLocalCylinder(
                item.position, item.rotationDegrees,
                hx, handleZ, 0.014f, 0.14f, handleY - 0.07f,
                BrassAccentColor, extraRotationDeg = 90f, metallic = 0.95f, roughness = 0.15f
            )
        }

        // 4. Standing 4K OLED TV on top (facing room -Z)
        val tvStandY = standH
        // Brushed metal base plate with beveled edges
        renderLocalBeveledBox(
            item.position, item.rotationDegrees,
            0f, 0.02f, 0.34f, 0.012f, 0.18f, tvStandY,
            ChromeColor, metallic = 0.9f, roughness = 0.18f
        )
        // Cylindrical neck column
        renderLocalCylinder(
            item.position, item.rotationDegrees,
            0f, 0.02f, 0.06f, 0.08f, tvStandY + 0.012f,
            ChromeColor, metallic = 0.9f, roughness = 0.18f
        )

        val tvW = min(w * 0.86f, 1.45f)
        val tvH = tvW * 0.56f
        val tvY0 = tvStandY + 0.08f
        val tvZ = 0.02f
        // Black frame & back casing with beveled profile
        renderLocalBeveledBox(
            item.position, item.rotationDegrees,
            0f, tvZ, tvW, tvH, 0.026f, tvY0,
            ColorRGBA(0.08f, 0.10f, 0.12f, 1f), roughness = 0.25f
        )
        // High-gloss deep dark OLED screen facing -Z
        renderLocalBox(
            item.position, item.rotationDegrees,
            0f, tvZ - 0.01f, tvW - 0.016f, tvH - 0.016f, 0.008f, tvY0 + 0.008f,
            ColorRGBA(0.03f, 0.04f, 0.06f, 1f), roughness = 0.04f
        )
    }

    // --- Curated Book & Library Materials for Blender-Grade Realism ---
    private val BookNavy = ColorRGBA(0.12f, 0.20f, 0.38f, 1f)
    private val BookCrimson = ColorRGBA(0.55f, 0.12f, 0.14f, 1f)
    private val BookForest = ColorRGBA(0.14f, 0.32f, 0.20f, 1f)
    private val BookAmber = ColorRGBA(0.78f, 0.52f, 0.18f, 1f)
    private val BookCharcoal = ColorRGBA(0.18f, 0.20f, 0.23f, 1f)
    private val BookCognac = ColorRGBA(0.48f, 0.26f, 0.14f, 1f)
    private val BookOchre = ColorRGBA(0.72f, 0.44f, 0.16f, 1f)
    private val BookIvoryPages = ColorRGBA(0.96f, 0.94f, 0.89f, 1f)
    private val BookDarkSlate = ColorRGBA(0.24f, 0.28f, 0.34f, 1f)
    private val BookTerracotta = ColorRGBA(0.68f, 0.32f, 0.22f, 1f)

    private fun FrameScope.renderRealisticVerticalBook(
        parentCenter: Point2D,
        parentRot: Float,
        x: Float,
        z: Float,
        y0: Float,
        thick: Float,
        height: Float,
        depth: Float,
        coverCol: ColorRGBA,
        hasGoldRibs: Boolean = true
    ) {
        val boardT = 0.003f
        // Left hardcover board
        renderLocalBox(parentCenter, parentRot, x - thick * 0.5f + boardT * 0.5f, z, boardT, height, depth, y0, coverCol, roughness = 0.40f)
        // Right hardcover board
        renderLocalBox(parentCenter, parentRot, x + thick * 0.5f - boardT * 0.5f, z, boardT, height, depth, y0, coverCol, roughness = 0.40f)
        // Spine facing front (-Z)
        val spineZ = z - depth * 0.5f + boardT * 0.5f
        renderLocalBox(parentCenter, parentRot, x, spineZ, thick, height, boardT, y0, coverCol, roughness = 0.35f)

        // Interior recessed pages core (paper block)
        val pageW = max(0.006f, thick - boardT * 2f)
        val pageH = max(0.010f, height - 0.008f)
        val pageD = max(0.010f, depth - 0.006f)
        renderLocalBox(
            parentCenter, parentRot,
            x, z + 0.002f, pageW, pageH, pageD, y0 + 0.004f,
            BookIvoryPages, roughness = 0.85f
        )

        // Embossed gold/brass ribs across spine
        if (hasGoldRibs) {
            val ribCount = 3
            for (r in 1..ribCount) {
                val ribY = y0 + height * (r / (ribCount + 1f))
                renderLocalBox(
                    parentCenter, parentRot,
                    x, spineZ - 0.001f, thick * 0.95f, 0.0035f, 0.0025f, ribY,
                    BrassAccentColor, metallic = 0.92f, roughness = 0.18f
                )
            }
        }
    }

    private fun FrameScope.renderRealisticFlatBook(
        parentCenter: Point2D,
        parentRot: Float,
        x: Float,
        z: Float,
        y0: Float,
        width: Float,
        height: Float,
        depth: Float,
        coverCol: ColorRGBA
    ) {
        val boardT = 0.003f
        // Bottom board
        renderLocalBox(parentCenter, parentRot, x, z, width, boardT, depth, y0, coverCol, roughness = 0.40f)
        // Top board
        renderLocalBox(parentCenter, parentRot, x, z, width, boardT, depth, y0 + height - boardT, coverCol, roughness = 0.40f)
        // Spine facing front (-Z)
        renderLocalBox(parentCenter, parentRot, x, z - depth * 0.5f + boardT * 0.5f, width, height, boardT, y0, coverCol, roughness = 0.35f)
        // Interior recessed pages
        renderLocalBox(
            parentCenter, parentRot,
            x, z + 0.002f, width - 0.008f, max(0.005f, height - boardT * 2f), depth - 0.006f, y0 + boardT,
            BookIvoryPages, roughness = 0.85f
        )
    }

    private fun FrameScope.renderRealisticLeaningBook(
        parentCenter: Point2D,
        parentRot: Float,
        x: Float,
        z: Float,
        y0: Float,
        thick: Float,
        height: Float,
        depth: Float,
        tiltDeg: Float,
        coverCol: ColorRGBA
    ) {
        val boardT = 0.003f
        renderLocalTiltedBox(parentCenter, parentRot, x, z, thick, height, depth, y0, tiltDeg, coverCol, roughness = 0.40f)
        renderLocalTiltedBox(
            parentCenter, parentRot,
            x, z + 0.002f, max(0.006f, thick - boardT * 2f), max(0.01f, height - 0.008f), max(0.01f, depth - 0.006f),
            y0 + 0.004f, tiltDeg, BookIvoryPages, roughness = 0.85f
        )
    }

    private fun FrameScope.renderBookshelf(item: PlacedItem, isSelected: Boolean) {
        val col = if (isSelected) WallSelectedColor else CoffeeTableShelfColor
        val w = item.widthCm / 100f
        val d = item.depthCm / 100f
        val h = 1.95f
        val halfW = w * 0.5f
        val halfD = d * 0.5f
        val thick = 0.032f

        // 1. Vertical uprights with beveled edges
        renderLocalBeveledBox(item.position, item.rotationDegrees, -halfW + thick * 0.5f, 0f, thick, h, d, 0f, col, roughness = 0.35f)
        renderLocalBeveledBox(item.position, item.rotationDegrees, halfW - thick * 0.5f, 0f, thick, h, d, 0f, col, roughness = 0.35f)

        // 2. Top cover cornice & bottom plinth with beveled profile
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w + 0.02f, thick, d + 0.02f, h - thick, col, roughness = 0.35f)
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w, 0.06f, d, 0f, col, roughness = 0.35f)

        // 3. Back panel against wall (+Z)
        renderLocalBox(item.position, item.rotationDegrees, 0f, halfD - 0.006f, w - thick * 2f, h - 0.06f, 0.012f, 0.06f, CoffeeTableTopColor)

        // 4. 4 interior shelves with beveled front edge
        val innerW = w - thick * 2f
        val shelfCount = 4
        val shelfStep = (h - 0.06f - thick) / (shelfCount + 1f)

        for (s in 1..shelfCount) {
            val shelfY = 0.06f + shelfStep * s
            renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, innerW, thick, d - 0.015f, shelfY, col, roughness = 0.35f)

            // High-End Blender-Grade Realistic Procedural Books & Decor
            when (s) {
                4 -> {
                    // Top shelf: Fine literature row + leaning volume + brass bookend + right book cluster
                    var curX = -innerW * 0.5f + 0.04f
                    val bList = listOf(
                        Triple(0.034f, 0.24f, BookCrimson),
                        Triple(0.026f, 0.22f, BookNavy),
                        Triple(0.040f, 0.26f, BookAmber),
                        Triple(0.028f, 0.23f, BookForest),
                        Triple(0.024f, 0.25f, BookCharcoal),
                        Triple(0.036f, 0.235f, BookCognac)
                    )
                    for ((bt, bh, bcol) in bList) {
                        val bd = 0.18f
                        renderRealisticVerticalBook(
                            item.position, item.rotationDegrees,
                            curX + bt * 0.5f, halfD - 0.025f - bd * 0.5f, shelfY + thick,
                            bt, min(bh, shelfStep * 0.85f), bd, bcol, hasGoldRibs = true
                        )
                        curX += bt + 0.002f
                    }
                    // Leaning volume resting against Book 6
                    val leanT = 0.028f
                    val leanH = min(0.23f, shelfStep * 0.82f)
                    val leanD = 0.18f
                    renderRealisticLeaningBook(
                        item.position, item.rotationDegrees,
                        curX + leanT * 0.5f + 0.015f, halfD - 0.025f - leanD * 0.5f, shelfY + thick,
                        leanT, leanH, leanD, -13f, BookTerracotta
                    )
                    curX += 0.065f

                    // Architectural turned brass bookend prism
                    renderLocalBeveledBox(
                        item.position, item.rotationDegrees,
                        curX + 0.025f, halfD - 0.025f - leanD * 0.5f, 0.05f, 0.08f, 0.10f, shelfY + thick,
                        BrassAccentColor, metallic = 0.95f, roughness = 0.18f
                    )

                    // Right group: 3 books
                    var rX = innerW * 0.5f - 0.04f
                    val rList = listOf(
                        Triple(0.032f, 0.23f, BookNavy),
                        Triple(0.028f, 0.25f, BookForest),
                        Triple(0.035f, 0.22f, BookDarkSlate)
                    )
                    for ((bt, bh, bcol) in rList) {
                        val bd = 0.18f
                        renderRealisticVerticalBook(
                            item.position, item.rotationDegrees,
                            rX - bt * 0.5f, halfD - 0.025f - bd * 0.5f, shelfY + thick,
                            bt, min(bh, shelfStep * 0.85f), bd, bcol, hasGoldRibs = true
                        )
                        rX -= (bt + 0.002f)
                    }
                }
                3 -> {
                    // Upper-mid shelf: Scandinavian styling with central ceramic vase & flanking book rows
                    var curX = -innerW * 0.5f + 0.04f
                    val bList = listOf(
                        Triple(0.026f, 0.22f, BookNavy),
                        Triple(0.032f, 0.24f, BookForest),
                        Triple(0.028f, 0.21f, BookCharcoal),
                        Triple(0.038f, 0.25f, BookAmber),
                        Triple(0.030f, 0.23f, BookCrimson)
                    )
                    for ((bt, bh, bcol) in bList) {
                        val bd = 0.18f
                        renderRealisticVerticalBook(
                            item.position, item.rotationDegrees,
                            curX + bt * 0.5f, halfD - 0.025f - bd * 0.5f, shelfY + thick,
                            bt, min(bh, shelfStep * 0.85f), bd, bcol, hasGoldRibs = true
                        )
                        curX += bt + 0.002f
                    }

                    // Center: Scandinavian Flared Ceramic Vase with Brass Collar
                    val vaseZ = 0f
                    val vaseY = shelfY + thick
                    renderLocalFrustum(
                        item.position, item.rotationDegrees,
                        0f, vaseZ, 0.11f, 0.06f, 0.16f, vaseY,
                        CeramicWhiteColor, roughness = 0.22f
                    )
                    renderLocalCylinder(
                        item.position, item.rotationDegrees,
                        0f, vaseZ, 0.115f, 0.012f, vaseY + 0.15f,
                        BrassAccentColor, metallic = 0.95f, roughness = 0.18f
                    )
                    renderLocalCylinder(
                        item.position, item.rotationDegrees,
                        0f, vaseZ, 0.055f, 0.05f, vaseY + 0.16f,
                        CeramicWhiteColor, roughness = 0.22f
                    )

                    // Right group: 4 books
                    var rX = innerW * 0.5f - 0.04f
                    val rList = listOf(
                        Triple(0.030f, 0.24f, BookCognac),
                        Triple(0.025f, 0.22f, BookTerracotta),
                        Triple(0.036f, 0.25f, BookNavy),
                        Triple(0.028f, 0.23f, BookCharcoal)
                    )
                    for ((bt, bh, bcol) in rList) {
                        val bd = 0.18f
                        renderRealisticVerticalBook(
                            item.position, item.rotationDegrees,
                            rX - bt * 0.5f, halfD - 0.025f - bd * 0.5f, shelfY + thick,
                            bt, min(bh, shelfStep * 0.85f), bd, bcol, hasGoldRibs = true
                        )
                        rX -= (bt + 0.002f)
                    }
                }
                2 -> {
                    // Lower-mid shelf: Flat stack of 3 coffee-table art books with brass sphere + encyclopedia set
                    val stackX = -innerW * 0.5f + 0.16f
                    val stackZ = halfD - 0.025f - 0.11f
                    var stackY = shelfY + thick
                    renderRealisticFlatBook(
                        item.position, item.rotationDegrees,
                        stackX, stackZ, stackY, 0.24f, 0.034f, 0.20f, BookDarkSlate
                    )
                    stackY += 0.034f
                    renderRealisticFlatBook(
                        item.position, item.rotationDegrees,
                        stackX, stackZ, stackY, 0.22f, 0.028f, 0.19f, BookCrimson
                    )
                    stackY += 0.028f
                    renderRealisticFlatBook(
                        item.position, item.rotationDegrees,
                        stackX, stackZ, stackY, 0.20f, 0.024f, 0.18f, BookOchre
                    )
                    stackY += 0.024f
                    renderLocalSphere(
                        item.position, item.rotationDegrees,
                        stackX, stackZ, 0.052f, stackY,
                        BrassAccentColor, metallic = 0.95f, roughness = 0.15f
                    )

                    // Right: 5 uniform collector's editions in Deep Forest Green with bright gold ribs
                    var rX = innerW * 0.5f - 0.04f
                    for (i in 0..4) {
                        val bt = 0.035f
                        val bh = min(0.245f, shelfStep * 0.85f)
                        val bd = 0.185f
                        renderRealisticVerticalBook(
                            item.position, item.rotationDegrees,
                            rX - bt * 0.5f, halfD - 0.025f - bd * 0.5f, shelfY + thick,
                            bt, bh, bd, BookForest, hasGoldRibs = true
                        )
                        rX -= (bt + 0.002f)
                    }
                    renderLocalBeveledBox(
                        item.position, item.rotationDegrees,
                        rX - 0.025f, halfD - 0.025f - 0.09f, 0.05f, 0.10f, 0.10f, shelfY + thick,
                        ColorRGBA(0.18f, 0.20f, 0.22f, 1f), roughness = 0.18f
                    )
                }
                1 -> {
                    // Bottom shelf: heavy thick binders & reference volumes
                    var curX = -innerW * 0.5f + 0.04f
                    val bList = listOf(
                        Triple(0.042f, 0.27f, BookCharcoal),
                        Triple(0.040f, 0.27f, BookNavy),
                        Triple(0.044f, 0.275f, BookNavy),
                        Triple(0.038f, 0.265f, BookCharcoal),
                        Triple(0.042f, 0.27f, BookCognac),
                        Triple(0.040f, 0.27f, BookCrimson)
                    )
                    for ((bt, bh, bcol) in bList) {
                        val bd = 0.20f
                        renderRealisticVerticalBook(
                            item.position, item.rotationDegrees,
                            curX + bt * 0.5f, halfD - 0.025f - bd * 0.5f, shelfY + thick,
                            bt, min(bh, shelfStep * 0.88f), bd, bcol, hasGoldRibs = true
                        )
                        curX += bt + 0.003f
                    }

                    // Right group: 4 thick albums + brushed aluminum bookend
                    var rX = innerW * 0.5f - 0.04f
                    val rList = listOf(
                        Triple(0.038f, 0.26f, BookDarkSlate),
                        Triple(0.035f, 0.25f, BookForest),
                        Triple(0.040f, 0.26f, BookNavy),
                        Triple(0.036f, 0.255f, BookCharcoal)
                    )
                    for ((bt, bh, bcol) in rList) {
                        val bd = 0.20f
                        renderRealisticVerticalBook(
                            item.position, item.rotationDegrees,
                            rX - bt * 0.5f, halfD - 0.025f - bd * 0.5f, shelfY + thick,
                            bt, min(bh, shelfStep * 0.88f), bd, bcol, hasGoldRibs = true
                        )
                        rX -= (bt + 0.003f)
                    }
                    renderLocalBeveledBox(
                        item.position, item.rotationDegrees,
                        rX - 0.022f, halfD - 0.025f - 0.10f, 0.045f, 0.11f, 0.11f, shelfY + thick,
                        StainlessSteelColor, metallic = 0.90f, roughness = 0.25f
                    )
                }
            }
        }
    }

    private fun FrameScope.renderFloorLamp(item: PlacedItem, isSelected: Boolean) {
        val metalCol = if (isSelected) WallSelectedColor else CoffeeTableLegColor

        // 1. Weighted round steel base (smooth 28-gon cylinder)
        renderLocalCylinder(item.position, item.rotationDegrees, 0f, 0f, 0.36f, 0.025f, 0f, metalCol, metallic = 0.75f, roughness = 0.25f, segments = 28)
        // 2. Turned brass collar & bead
        renderLocalCylinder(item.position, item.rotationDegrees, 0f, 0f, 0.06f, 0.035f, 0.025f, BrassAccentColor, metallic = 0.95f, roughness = 0.15f)
        renderLocalSphere(item.position, item.rotationDegrees, 0f, 0f, 0.05f, 0.055f, BrassAccentColor, metallic = 0.95f)
        // 3. Slim vertical cylindrical rod stem
        renderLocalCylinder(item.position, item.rotationDegrees, 0f, 0f, 0.025f, 1.35f, 0.065f, metalCol, metallic = 0.75f, roughness = 0.25f)

        // 4. Scandinavian Flared Conical Lampshade (tapered conical frustum)
        val shadeY = 1.38f
        val shadeH = 0.30f
        renderLocalFrustum(
            item.position, item.rotationDegrees,
            0f, 0f,
            topDiameter = 0.32f,
            bottomDiameter = 0.46f,
            height = shadeH,
            yMin = shadeY,
            color = ColorRGBA(0.98f, 0.94f, 0.82f, 1f),
            roughness = 0.55f
        )
        // Top turned brass finial cap
        renderLocalSphere(item.position, item.rotationDegrees, 0f, 0f, 0.045f, shadeY + shadeH + 0.01f, BrassAccentColor, metallic = 0.95f)
    }

    private fun FrameScope.renderCarpet(item: PlacedItem, isSelected: Boolean) {
        val baseCol = if (isSelected) WallSelectedColor else ColorRGBA(0.86f, 0.85f, 0.82f, 1f)
        val w = item.widthCm / 100f
        val d = item.depthCm / 100f
        val h = 0.008f
        // Must be placed above the room parquet floor slab (which is at y = 0.015f)
        val y0 = 0.016f

        // Flat textile rug pile with soft beveled edge
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w, h, d, y0, baseCol, roughness = 0.85f)
        // Decorative contrast border
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w - 0.16f, h + 0.001f, d - 0.16f, y0, ColorRGBA(0.42f, 0.48f, 0.55f, 1f), roughness = 0.85f)
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w - 0.22f, h + 0.002f, d - 0.22f, y0, baseCol, roughness = 0.85f)
    }

    private fun FrameScope.renderDiningSet(item: PlacedItem, isSelected: Boolean) {
        val woodCol = if (isSelected) WallSelectedColor else ColorRGBA(0.70f, 0.32f, 0.03f, 1f)
        val w = item.widthCm / 100f
        val d = item.depthCm / 100f
        val tableH = 0.75f

        if (item.itemType == ItemType.DINING_TABLE_ROUND) {
            val dia = min(w, d)
            // 1. Smooth 28-gon round tabletop with beveled profile
            renderLocalCylinder(item.position, item.rotationDegrees, 0f, 0f, dia, 0.038f, tableH - 0.038f, woodCol, roughness = 0.28f, segments = 28)
            // 2. Central pedestal flared conical column
            renderLocalFrustum(item.position, item.rotationDegrees, 0f, 0f, 0.12f, 0.22f, tableH - 0.076f, 0.038f, ColorRGBA(0.12f, 0.16f, 0.23f, 1f), roughness = 0.3f, metallic = 0.6f)
            // 3. Heavy circular base disc
            renderLocalCylinder(item.position, item.rotationDegrees, 0f, 0f, dia * 0.55f, 0.038f, 0f, ColorRGBA(0.12f, 0.16f, 0.23f, 1f), metallic = 0.7f, roughness = 0.25f, segments = 28)
            // 4. Four round chairs matching the SVG top/bottom/left/right layout
            val chairDist = dia * 0.5f + 0.18f
            val chairSeatDia = 0.38f
            val chairOffsets = listOf(
                Pair(0f, -chairDist),  // top
                Pair(0f, chairDist),   // bottom
                Pair(-chairDist, 0f),  // left
                Pair(chairDist, 0f)    // right
            )
            val chairRotations = listOf(0f, 180f, -90f, 90f)
            for (idx in 0 until 4) {
                val (cx, cz) = chairOffsets[idx]
                val rot = chairRotations[idx]
                // 4 tapered chair legs
                val lOff = 0.11f
                for (dx in listOf(-lOff, lOff)) {
                    for (dz in listOf(-lOff, lOff)) {
                        renderLocalFrustum(item.position, item.rotationDegrees, cx + dx, cz + dz, 0.030f, 0.018f, 0.44f, 0f, ColorRGBA(0.12f, 0.16f, 0.23f, 1f), roughness = 0.3f)
                    }
                }
                // Round chair cushion (smooth 24-gon) + soft pillow crown
                renderLocalCylinder(item.position, item.rotationDegrees, cx, cz, chairSeatDia, 0.045f, 0.44f, ColorRGBA(0.20f, 0.25f, 0.33f, 1f), roughness = 0.6f)
                // Curved backrest facing away from table
                val backDirZ = if (cz != 0f) cz.sign * 0.15f else 0f
                val backDirX = if (cx != 0f) cx.sign * 0.15f else 0f
                renderLocalBeveledBox(item.position, item.rotationDegrees, cx + backDirX, cz + backDirZ, 0.30f, 0.35f, 0.035f, 0.48f, ColorRGBA(0.20f, 0.25f, 0.33f, 1f), extraRotationDeg = rot, roughness = 0.55f)
            }
            return
        }

        // Rectangular Dining Tables (DINING_TABLE_4 and DINING_TABLE_6)
        val isSixPerson = (item.itemType == ItemType.DINING_TABLE_6 || w >= 1.50f)
        val halfW = w * 0.5f
        val halfD = d * 0.5f

        // Tabletop slab with CAD Bevels
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w, 0.038f, d, tableH - 0.038f, woodCol, roughness = 0.28f)
        // Table perimeter apron with beveled edges
        val apronH = 0.05f
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w - 0.08f, apronH, d - 0.08f, tableH - 0.038f - apronH, woodCol, roughness = 0.32f)

        // 4 Tapered Turned Legs with Brass Shoes at corners
        val legX = halfW - 0.06f
        val legZ = halfD - 0.06f
        for (dx in listOf(-legX, legX)) {
            for (dz in listOf(-legZ, legZ)) {
                renderLocalFrustum(
                    item.position, item.rotationDegrees,
                    dx, dz, 0.052f, 0.028f, tableH - 0.038f - 0.025f, 0.025f,
                    ColorRGBA(0.12f, 0.16f, 0.23f, 1f), roughness = 0.35f
                )
                renderLocalCylinder(
                    item.position, item.rotationDegrees,
                    dx, dz, 0.036f, 0.025f, 0f,
                    BrassAccentColor, metallic = 0.95f, roughness = 0.15f
                )
            }
        }

        // Chairs and Woven Placemats on Tabletop
        val chairZOffset = halfD + 0.24f
        val placematZ = halfD * 0.42f
        val xOffsets = if (isSixPerson) {
            listOf(-halfW * 0.65f, 0f, halfW * 0.65f)
        } else {
            listOf(-halfW * 0.45f, halfW * 0.45f)
        }

        for (cx in xOffsets) {
            // Front side chairs (-Z) facing table (+Z, rotation 0°)
            renderDiningChair(item.position, item.rotationDegrees, cx, -chairZOffset, 0f, isSelected)
            renderLocalCylinder(item.position, item.rotationDegrees, cx, -placematZ, 0.28f, 0.005f, tableH, ColorRGBA(0.86f, 0.80f, 0.70f, 1f), roughness = 0.8f)

            // Back side chairs (+Z) facing table (-Z, rotation 180°)
            renderDiningChair(item.position, item.rotationDegrees, cx, chairZOffset, 180f, isSelected)
            renderLocalCylinder(item.position, item.rotationDegrees, cx, placematZ, 0.28f, 0.005f, tableH, ColorRGBA(0.86f, 0.80f, 0.70f, 1f), roughness = 0.8f)
        }
    }

    private fun FrameScope.renderDiningChair(
        parentCenter: Point2D,
        parentRotationDeg: Float,
        localX: Float,
        localZ: Float,
        facingAngleDeg: Float,
        isSelected: Boolean
    ) {
        val chairCol = if (isSelected) WallSelectedColor else ColorRGBA(0.20f, 0.25f, 0.33f, 1f)
        val legCol = ColorRGBA(0.12f, 0.16f, 0.23f, 1f)
        val seatW = 0.42f
        val seatD = 0.40f
        val seatH = 0.05f
        val legH = 0.44f

        val lx = seatW * 0.5f - 0.04f
        val lz = seatD * 0.5f - 0.04f
        for (dx in listOf(-lx, lx)) {
            for (dz in listOf(-lz, lz)) {
                renderLocalFrustum(
                    parentCenter, parentRotationDeg,
                    localX + dx, localZ + dz,
                    0.032f, 0.018f, legH - 0.025f, 0.025f,
                    legCol, extraRotationDeg = facingAngleDeg, roughness = 0.35f
                )
                renderLocalCylinder(
                    parentCenter, parentRotationDeg,
                    localX + dx, localZ + dz,
                    0.022f, 0.025f, 0f,
                    BrassAccentColor, extraRotationDeg = facingAngleDeg, metallic = 0.95f, roughness = 0.15f
                )
            }
        }

        // Padded chair seat with soft beveled profile & cushion crown
        renderLocalBeveledBox(
            parentCenter, parentRotationDeg,
            localX, localZ,
            seatW, seatH, seatD, legH,
            chairCol, extraRotationDeg = facingAngleDeg, roughness = 0.6f
        )
        renderLocalPillow(
            parentCenter, parentRotationDeg,
            localX, localZ,
            seatW - 0.02f, 0.035f, seatD - 0.02f, legH + seatH * 0.5f,
            chairCol, extraRotationDeg = facingAngleDeg, roughness = 0.65f
        )

        val rad = -facingAngleDeg * (PI.toFloat() / 180f)
        val backLocalZ = -seatD * 0.5f + 0.02f
        val bz = backLocalZ * cos(rad)
        val bx = -backLocalZ * sin(rad)
        val backH = 0.38f
        val backW = seatW - 0.02f

        renderLocalBeveledBox(
            parentCenter, parentRotationDeg,
            localX + bx, localZ + bz,
            backW, backH, 0.03f, legH + seatH,
            chairCol, extraRotationDeg = facingAngleDeg, roughness = 0.55f
        )
    }

    private fun FrameScope.renderBed(item: PlacedItem, isSelected: Boolean) {
        val frameCol = if (isSelected) WallSelectedColor else WoodLegColor
        val w = item.widthCm / 100f
        val d = item.depthCm / 100f
        val halfW = w * 0.5f
        val halfD = d * 0.5f
        val isDouble = (item.itemType == ItemType.BED_DOUBLE || w > 1.30f)

        // 1. Four Walnut Turned Tapered Legs with Polished Brass Shoes
        val legH = 0.12f
        val ferruleH = 0.030f
        val legX = halfW - 0.06f
        val legZ = halfD - 0.06f
        for (dx in listOf(-legX, legX)) {
            for (dz in listOf(-legZ, legZ)) {
                renderLocalFrustum(
                    item.position, item.rotationDegrees,
                    dx, dz, 0.052f, 0.028f, legH - ferruleH, ferruleH,
                    WoodLegColor, roughness = 0.35f
                )
                renderLocalCylinder(
                    item.position, item.rotationDegrees,
                    dx, dz, 0.034f, ferruleH, 0f,
                    BrassAccentColor, metallic = 0.95f, roughness = 0.15f
                )
            }
        }

        // 2. Bed Base & Side Rails with Beveled Chamfers
        val railH = 0.22f
        renderLocalBeveledBox(
            item.position, item.rotationDegrees,
            0f, 0f, w, railH, d, legH,
            frameCol, roughness = 0.35f
        )

        // 3. Tall Padded Headboard with Channeled Upholstery (+Z)
        val headboardH = 1.05f
        val headboardThick = 0.09f
        val headboardZ = halfD - headboardThick * 0.5f
        // Wooden frame surround
        renderLocalBeveledBox(
            item.position, item.rotationDegrees,
            0f, headboardZ, w, headboardH, headboardThick, 0f,
            frameCol, roughness = 0.35f
        )
        // Vertical upholstered channel panels
        val channelCount = if (isDouble) 6 else 4
        val innerHeadW = w - 0.08f
        val chW = innerHeadW / channelCount
        for (c in 0 until channelCount) {
            val cx = -innerHeadW * 0.5f + chW * 0.5f + c * chW
            renderLocalBeveledBox(
                item.position, item.rotationDegrees,
                cx, headboardZ - 0.015f, chW - 0.008f, headboardH - 0.12f, headboardThick * 0.85f, 0.08f,
                FabricSofaColor, roughness = 0.65f
            )
        }

        // 4. Premium Pillow-Top Mattress with Beveled Edges
        val matW = w - 0.08f
        val matD = d - 0.16f
        val matH = 0.24f
        val matY = legH + railH - 0.04f
        renderLocalBeveledBox(
            item.position, item.rotationDegrees,
            0f, -0.04f, matW, matH, matD, matY,
            ColorRGBA(0.97f, 0.97f, 0.98f, 1f), roughness = 0.45f
        )

        // 5. Plush Folded Duvet with Soft Beveled Loft
        val duvetD = matD * 0.65f
        val duvetZ = -halfD + duvetD * 0.5f + 0.02f
        val duvetY = matY + matH
        val duvetH = 0.045f
        val duvetCol = if (isSelected) WallSelectedColor else ColorRGBA(0.92f, 0.94f, 0.97f, 1f)
        renderLocalBeveledBox(
            item.position, item.rotationDegrees,
            0f, duvetZ, matW + 0.02f, duvetH, duvetD, duvetY,
            duvetCol, roughness = 0.75f
        )

        // 6. Contrast Bed Runner Across the Foot
        val runnerD = matD * 0.24f
        val runnerZ = -halfD + runnerD * 0.5f + 0.04f
        renderLocalBeveledBox(
            item.position, item.rotationDegrees,
            0f, runnerZ, matW + 0.025f, duvetH + 0.005f, runnerD, duvetY,
            AccentPillow2Color, roughness = 0.6f
        )

        // 7. Sleeping Pillows (Organic Sinusoidal Loft) & Decorative Pillows
        val pillowY = matY + matH
        if (isDouble) {
            val pW = matW * 0.44f
            val pD = 0.36f
            val pH = 0.12f
            val pZ = halfD - headboardThick - pD * 0.5f - 0.04f
            val pX = matW * 0.24f

            // Sleeping pillows
            renderLocalPillow(
                item.position, item.rotationDegrees,
                -pX, pZ, pW, pH, pD, pillowY,
                ColorRGBA(0.98f, 0.98f, 1f, 1f), roughness = 0.65f
            )
            renderLocalPillow(
                item.position, item.rotationDegrees,
                pX, pZ, pW, pH, pD, pillowY,
                ColorRGBA(0.98f, 0.98f, 1f, 1f), roughness = 0.65f
            )

            // Accent pillows tilted in front of sleeping pillows
            val accW = 0.36f
            val accD = 0.14f
            val accH = 0.24f
            val accZ = pZ - pD * 0.5f - 0.06f
            renderLocalPillow(
                item.position, item.rotationDegrees,
                -pX, accZ, accW, accH, accD, pillowY,
                AccentPillow1Color, extraRotationDeg = -8f, roughness = 0.6f
            )
            renderLocalPillow(
                item.position, item.rotationDegrees,
                pX, accZ, accW, accH, accD, pillowY,
                AccentPillow1Color, extraRotationDeg = 8f, roughness = 0.6f
            )
        } else {
            val pW = matW * 0.75f
            val pD = 0.36f
            val pH = 0.12f
            val pZ = halfD - headboardThick - pD * 0.5f - 0.04f

            renderLocalPillow(
                item.position, item.rotationDegrees,
                0f, pZ, pW, pH, pD, pillowY,
                ColorRGBA(0.98f, 0.98f, 1f, 1f), roughness = 0.65f
            )

            val accW = 0.38f
            val accD = 0.14f
            val accH = 0.24f
            val accZ = pZ - pD * 0.5f - 0.06f
            renderLocalPillow(
                item.position, item.rotationDegrees,
                0f, accZ, accW, accH, accD, pillowY,
                AccentPillow1Color, extraRotationDeg = 6f, roughness = 0.6f
            )
        }
    }

    private fun FrameScope.renderBunkBed(item: PlacedItem, isSelected: Boolean) {
        val frameCol = if (isSelected) WallSelectedColor else WoodLegColor
        val w = item.widthCm / 100f
        val d = item.depthCm / 100f
        val halfW = w * 0.5f
        val halfD = d * 0.5f
        val postH = 1.75f
        val postSize = 0.055f

        // 1. 4 Corner posts with beveled edges
        for (dx in listOf(-halfW + postSize * 0.5f, halfW - postSize * 0.5f)) {
            for (dz in listOf(-halfD + postSize * 0.5f, halfD - postSize * 0.5f)) {
                renderLocalBeveledBox(item.position, item.rotationDegrees, dx, dz, postSize, postH, postSize, 0f, frameCol, roughness = 0.35f)
            }
        }

        // 2. Lower tier (y = 0.25m)
        val bed1Y = 0.25f
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w - postSize * 2f, 0.12f, d - postSize * 2f, bed1Y, frameCol)
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, -0.02f, w - 0.14f, 0.18f, d - 0.16f, bed1Y + 0.12f, ColorRGBA.White, roughness = 0.45f)
        renderLocalPillow(item.position, item.rotationDegrees, 0f, halfD - 0.24f, w * 0.60f, 0.10f, 0.30f, bed1Y + 0.30f, ColorRGBA(0.94f, 0.96f, 0.98f, 1f), roughness = 0.65f)
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, -halfD * 0.25f, w - 0.12f, 0.035f, d * 0.55f, bed1Y + 0.30f, ColorRGBA(0.18f, 0.45f, 0.85f, 1f))

        // 3. Upper tier (y = 1.18m)
        val bed2Y = 1.18f
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w - postSize * 2f, 0.12f, d - postSize * 2f, bed2Y, frameCol)
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, -0.02f, w - 0.14f, 0.18f, d - 0.16f, bed2Y + 0.12f, ColorRGBA.White, roughness = 0.45f)
        renderLocalPillow(item.position, item.rotationDegrees, 0f, halfD - 0.24f, w * 0.60f, 0.10f, 0.30f, bed2Y + 0.30f, ColorRGBA(0.94f, 0.96f, 0.98f, 1f), roughness = 0.65f)
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, -halfD * 0.25f, w - 0.12f, 0.035f, d * 0.55f, bed2Y + 0.30f, ColorRGBA(0.85f, 0.35f, 0.20f, 1f))

        // 4. Upper safety guard rails with beveled bars
        val railH = 0.25f
        val railY = bed2Y + 0.15f
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, halfD - postSize * 0.5f, w - postSize * 2f, 0.04f, 0.025f, railY + railH - 0.04f, frameCol)
        renderLocalBeveledBox(item.position, item.rotationDegrees, -halfW + postSize * 0.5f, 0f, 0.025f, 0.04f, d - postSize * 2f, railY + railH - 0.04f, frameCol)
        renderLocalBeveledBox(item.position, item.rotationDegrees, -halfW * 0.25f, -halfD + postSize * 0.5f, w * 0.45f, 0.04f, 0.025f, railY + railH - 0.04f, frameCol)

        // 5. Ladder on right front corner with cylindrical rungs
        val ladderX = halfW - 0.14f
        val ladderZ = -halfD - 0.02f
        renderLocalCylinder(item.position, item.rotationDegrees, ladderX - 0.15f, ladderZ, 0.032f, postH * 0.85f, 0f, ChromeColor, metallic = 0.9f)
        renderLocalCylinder(item.position, item.rotationDegrees, ladderX + 0.15f, ladderZ, 0.032f, postH * 0.85f, 0f, ChromeColor, metallic = 0.9f)
        for (r in 0..4) {
            val ry = 0.20f + r * 0.28f
            renderLocalCylinder(item.position, item.rotationDegrees, ladderX, ladderZ, 0.024f, 0.30f, ry, ChromeColor, extraRotationDeg = 90f, metallic = 0.9f)
        }
    }

    private fun FrameScope.renderWardrobe(item: PlacedItem, isSelected: Boolean) {
        val col = if (isSelected) WallSelectedColor else ColorRGBA(0.92f, 0.94f, 0.96f, 1f)
        val w = item.widthCm / 100f
        val d = item.depthCm / 100f
        val h = 2.15f
        val halfW = w * 0.5f
        val halfD = d * 0.5f

        // 1. Recessed Base Plinth with beveled edge
        val plinthH = 0.08f
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0.02f, w - 0.04f, plinthH, d - 0.04f, 0f, CoffeeTableLegColor)

        // 2. Main Carcass Cabinet Box with beveled edge
        val cabH = h - plinthH - 0.03f
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w, cabH, d, plinthH, col, roughness = 0.3f)

        // 3. Top Crown Fascia with beveled chamfers
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w + 0.02f, 0.03f, d + 0.02f, h - 0.03f, col, roughness = 0.3f)

        // 4. Door Panels with Shaker Bevels & Long Stainless Tubular Handles
        val isThreeDoor = w >= 1.50f
        val doorCount = if (isThreeDoor) 3 else 2
        val doorW = (w - 0.02f) / doorCount
        val doorH = cabH - 0.04f
        val doorY = plinthH + 0.02f
        val frontZ = -halfD - 0.008f

        for (i in 0 until doorCount) {
            val dx = -halfW + doorW * 0.5f + 0.01f + i * doorW
            renderLocalBeveledBox(item.position, item.rotationDegrees, dx, frontZ, doorW - 0.01f, doorH, 0.018f, doorY, col, roughness = 0.32f)

            if (isThreeDoor && i == 1) {
                // Center full-length mirror pane with bevel
                renderLocalBeveledBox(item.position, item.rotationDegrees, dx, frontZ - 0.006f, doorW - 0.06f, doorH - 0.12f, 0.008f, doorY + 0.06f, GlassColor, metallic = 0.95f, roughness = 0.02f)
            }

            val handleSideX = if (i == 0) dx + doorW * 0.38f else dx - doorW * 0.38f
            // Full height stainless steel tubular handles on standoffs
            renderLocalCylinder(item.position, item.rotationDegrees, handleSideX, frontZ - 0.025f, 0.020f, 0.55f, 0.92f, ChromeColor, metallic = 0.95f, roughness = 0.12f)
            renderLocalCylinder(item.position, item.rotationDegrees, handleSideX, frontZ - 0.012f, 0.016f, 0.02f, 0.98f, ChromeColor, metallic = 0.95f)
            renderLocalCylinder(item.position, item.rotationDegrees, handleSideX, frontZ - 0.012f, 0.016f, 0.02f, 1.40f, ChromeColor, metallic = 0.95f)
        }
    }

    private fun FrameScope.renderCornerWardrobe(item: PlacedItem, isSelected: Boolean) {
        val col = if (isSelected) WallSelectedColor else ColorRGBA(0.92f, 0.94f, 0.96f, 1f)
        val w = item.widthCm / 100f
        val d = item.depthCm / 100f
        val h = 2.15f
        val halfW = w * 0.5f
        val halfD = d * 0.5f
        val cutW = w * 0.45f
        val cutH = d * 0.45f
        val rearD = d - cutH
        val chaiseW = w - cutW
        val plinthH = 0.08f

        // Recessed base plinth with beveled edges
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, halfD - rearD * 0.5f + 0.02f, w - 0.04f, plinthH, rearD - 0.04f, 0f, CoffeeTableLegColor)
        renderLocalBeveledBox(item.position, item.rotationDegrees, -halfW + chaiseW * 0.5f + 0.02f, -halfD + cutH * 0.5f + 0.02f, chaiseW - 0.04f, plinthH, cutH - 0.04f, 0f, CoffeeTableLegColor)

        // L-shaped body with beveled edges
        val cabH = h - plinthH - 0.03f
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, halfD - rearD * 0.5f, w, cabH, rearD, plinthH, col, roughness = 0.3f)
        renderLocalBeveledBox(item.position, item.rotationDegrees, -halfW + chaiseW * 0.5f, -halfD + cutH * 0.5f, chaiseW, cabH, cutH, plinthH, col, roughness = 0.3f)

        // Top crown fascia
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, halfD - rearD * 0.5f, w + 0.02f, 0.03f, rearD + 0.02f, h - 0.03f, col, roughness = 0.3f)
        renderLocalBeveledBox(item.position, item.rotationDegrees, -halfW + chaiseW * 0.5f, -halfD + cutH * 0.5f, chaiseW + 0.02f, 0.03f, cutH + 0.02f, h - 0.03f, col, roughness = 0.3f)

        // Chrome cylindrical vertical bar handles
        renderLocalCylinder(item.position, item.rotationDegrees, -halfW + chaiseW - 0.05f, -halfD + cutH * 0.5f, 0.022f, 0.45f, 0.95f, ChromeColor, metallic = 0.95f)
        renderLocalCylinder(item.position, item.rotationDegrees, 0f, halfD - rearD - 0.015f, 0.022f, 0.45f, 0.95f, ChromeColor, metallic = 0.95f)
    }

    private fun FrameScope.renderDresser(item: PlacedItem, isSelected: Boolean) {
        val bodyColor = if (isSelected) WallSelectedColor else ColorRGBA(0.92f, 0.94f, 0.96f, 1f)
        val topColor = if (isSelected) WallSelectedColor else CoffeeTableTopColor
        val w = item.widthCm / 100f
        val d = item.depthCm / 100f
        val h = 0.88f
        val halfW = w * 0.5f
        val halfD = d * 0.5f
        val legH = 0.10f
        val ferruleH = 0.025f
        val cabH = h - legH

        // 1. 4 Tapered legs with brass ferrules
        for (dx in listOf(-halfW + 0.06f, halfW - 0.06f)) {
            for (dz in listOf(-halfD + 0.06f, halfD - 0.06f)) {
                renderLocalFrustum(item.position, item.rotationDegrees, dx, dz, 0.040f, 0.024f, legH - ferruleH, ferruleH, WoodLegColor, roughness = 0.35f)
                renderLocalCylinder(item.position, item.rotationDegrees, dx, dz, 0.028f, ferruleH, 0f, BrassAccentColor, metallic = 0.95f, roughness = 0.15f)
            }
        }

        // 2. Main body with beveled CAD edges
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w, cabH, d, legH, bodyColor, roughness = 0.35f)
        // Solid oak countertop slab with chamfered edge
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w + 0.02f, 0.025f, d + 0.02f, h - 0.025f, topColor, roughness = 0.28f)

        // 3. 3 wide horizontal drawers with cylindrical sleek brass handles
        val drawerH = (cabH - 0.025f) / 3f
        for (i in 0..2) {
            val dy = legH + i * drawerH + drawerH * 0.5f
            renderLocalCylinder(
                item.position, item.rotationDegrees,
                0f, -halfD - 0.015f, 0.016f, w * 0.45f, dy - 0.008f,
                BrassAccentColor, extraRotationDeg = 90f, metallic = 0.95f, roughness = 0.15f
            )
        }
    }

    private fun FrameScope.renderNightstand(item: PlacedItem, isSelected: Boolean) {
        val col = if (isSelected) WallSelectedColor else CoffeeTableShelfColor
        val topCol = if (isSelected) WallSelectedColor else CoffeeTableTopColor
        val w = item.widthCm / 100f
        val d = item.depthCm / 100f
        val halfW = w * 0.5f
        val halfD = d * 0.5f
        val h = 0.48f
        val legH = 0.14f
        val ferruleH = 0.025f

        // 1. 4 Tapered legs with brass shoes
        for (dx in listOf(-halfW + 0.04f, halfW - 0.04f)) {
            for (dz in listOf(-halfD + 0.04f, halfD - 0.04f)) {
                renderLocalFrustum(item.position, item.rotationDegrees, dx, dz, 0.034f, 0.020f, legH - ferruleH, ferruleH, WoodLegColor, roughness = 0.35f)
                renderLocalCylinder(item.position, item.rotationDegrees, dx, dz, 0.024f, ferruleH, 0f, BrassAccentColor, metallic = 0.95f, roughness = 0.15f)
            }
        }

        // 2. Nightstand box with beveled CAD edges & beveled oak top
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w, h - legH, d, legH, col, roughness = 0.35f)
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w + 0.015f, 0.02f, d + 0.015f, h - 0.02f, topCol, roughness = 0.28f)

        // 3. Brass spherical pull knob (smooth UV sphere)
        renderLocalSphere(item.position, item.rotationDegrees, 0f, -halfD - 0.018f, 0.028f, legH + (h - legH) * 0.5f - 0.014f, BrassAccentColor, metallic = 0.95f)

        // 4. Bedside table lamp: brass disc, turned stem, flared conical shade
        val lampBaseY = h
        renderLocalCylinder(item.position, item.rotationDegrees, 0f, 0f, 0.14f, 0.015f, lampBaseY, BrassAccentColor, metallic = 0.95f)
        renderLocalCylinder(item.position, item.rotationDegrees, 0f, 0f, 0.022f, 0.18f, lampBaseY + 0.015f, BrassAccentColor, metallic = 0.95f)
        renderLocalFrustum(item.position, item.rotationDegrees, 0f, 0f, 0.12f, 0.18f, 0.16f, lampBaseY + 0.195f, ColorRGBA(0.98f, 0.95f, 0.86f, 1f), roughness = 0.55f)
        renderLocalSphere(item.position, item.rotationDegrees, 0f, 0f, 0.025f, lampBaseY + 0.355f, BrassAccentColor, metallic = 0.95f)
    }

    private fun FrameScope.renderKitchenCounter(item: PlacedItem, isSelected: Boolean) {
        val baseCol = if (isSelected) WallSelectedColor else ColorRGBA(0.93f, 0.94f, 0.96f, 1f)
        val w = item.widthCm / 100f
        val d = item.depthCm / 100f
        val halfW = w * 0.5f
        val halfD = d * 0.5f
        val counterH = 0.90f
        val topThick = 0.04f

        // 1. Recessed Black Toe-Kick Plinth with beveled edge
        val plinthH = 0.10f
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0.03f, w, plinthH, d - 0.06f, 0f, ColorRGBA(0.12f, 0.14f, 0.16f, 1f))

        // 2. Base Cabinet Carcass with beveled edge
        val cabH = counterH - topThick - plinthH
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w, cabH, d, plinthH, baseCol, roughness = 0.35f)

        // 3. Modular Cabinet Doors & Drawer Fronts with Shaker Bevels
        val modCount = max(2, (w / 0.55f).roundToInt())
        val modW = (w - 0.02f) / modCount
        val frontZ = -halfD - 0.008f
        for (i in 0 until modCount) {
            val mx = -halfW + modW * 0.5f + 0.01f + i * modW
            // Door panel with beveled edge
            renderLocalBeveledBox(item.position, item.rotationDegrees, mx, frontZ, modW - 0.012f, cabH - 0.02f, 0.018f, plinthH + 0.01f, baseCol, roughness = 0.35f)
            // Horizontal brushed chrome cylindrical bar handle on standoffs
            val handleY = counterH - topThick - 0.12f
            renderLocalCylinder(item.position, item.rotationDegrees, mx, frontZ - 0.022f, 0.016f, modW * 0.45f, handleY, ChromeColor, extraRotationDeg = 90f, metallic = 0.95f, roughness = 0.12f)
            renderLocalCylinder(item.position, item.rotationDegrees, mx - modW * 0.18f, frontZ - 0.012f, 0.012f, 0.016f, handleY, ChromeColor, metallic = 0.95f)
            renderLocalCylinder(item.position, item.rotationDegrees, mx + modW * 0.18f, frontZ - 0.012f, 0.012f, 0.016f, handleY, ChromeColor, metallic = 0.95f)
        }

        // 4. Solid Polished Quartz Countertop Slab with Beveled Chamfers
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, -0.01f, w + 0.02f, topThick, d + 0.02f, counterH - topThick, ColorRGBA(0.98f, 0.98f, 1.0f, 1f), roughness = 0.15f)

        // 5. Built-in Stainless Steel Sink (on right side)
        val sinkW = min(0.68f, w * 0.42f)
        val sinkD = min(0.46f, d * 0.75f)
        val sinkX = halfW - sinkW * 0.5f - 0.12f
        val sinkZ = 0.01f
        // Stainless steel surround rim with beveled edge
        renderLocalBeveledBox(item.position, item.rotationDegrees, sinkX, sinkZ, sinkW, 0.008f, sinkD, counterH, StainlessSteelColor, metallic = 0.88f, roughness = 0.18f)
        // Recessed basin cavity
        val basinW = sinkW * 0.55f
        val basinD = sinkD - 0.08f
        val basinX = sinkX - sinkW * 0.20f
        renderLocalBeveledBox(item.position, item.rotationDegrees, basinX, sinkZ, basinW, 0.012f, basinD, counterH - 0.01f, ColorRGBA(0.45f, 0.48f, 0.52f, 1f), metallic = 0.85f)
        // Chrome drain strainer plug (smooth cylinder)
        renderLocalCylinder(item.position, item.rotationDegrees, basinX, sinkZ, 0.07f, 0.005f, counterH, ChromeColor, metallic = 0.95f)
        // Ribbed draining grooves on the side
        val drainerX = sinkX + sinkW * 0.26f
        for (g in -2..2) {
            renderLocalBeveledBox(item.position, item.rotationDegrees, drainerX, sinkZ + g * 0.045f, sinkW * 0.30f, 0.003f, 0.015f, counterH + 0.002f, ColorRGBA(0.65f, 0.68f, 0.72f, 1f), metallic = 0.85f)
        }

        // 6. Modern Tall Gooseneck Mixer Tap (smooth cylinders & curved spout)
        val tapX = basinX
        val tapZ = sinkZ + basinD * 0.5f + 0.03f
        renderLocalCylinder(item.position, item.rotationDegrees, tapX, tapZ, 0.048f, 0.04f, counterH, ChromeColor, metallic = 0.95f)
        renderLocalCylinder(item.position, item.rotationDegrees, tapX, tapZ, 0.026f, 0.30f, counterH + 0.04f, ChromeColor, metallic = 0.95f)
        renderLocalCylinder(item.position, item.rotationDegrees, tapX, tapZ - 0.07f, 0.024f, 0.14f, counterH + 0.32f, ChromeColor, extraRotationDeg = 90f, metallic = 0.95f)
        renderLocalCylinder(item.position, item.rotationDegrees, tapX, tapZ - 0.14f, 0.024f, 0.06f, counterH + 0.26f, ChromeColor, metallic = 0.95f)
        // Mixer lever
        renderLocalCylinder(item.position, item.rotationDegrees, tapX + 0.045f, tapZ, 0.014f, 0.08f, counterH + 0.12f, ChromeColor, extraRotationDeg = 45f, metallic = 0.95f)
    }

    private fun FrameScope.renderKitchenIsland(item: PlacedItem, isSelected: Boolean) {
        val baseCol = if (isSelected) WallSelectedColor else ColorRGBA(0.92f, 0.94f, 0.96f, 1f)
        val w = item.widthCm / 100f
        val d = item.depthCm / 100f
        val h = 0.90f
        val topThick = 0.04f
        val halfW = w * 0.5f

        // 1. Island Carcass with beveled edges
        val baseW = w - 0.10f
        val baseD = d - 0.15f // Overhang for seating
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0.05f, baseW, h - topThick, baseD, 0f, baseCol, roughness = 0.35f)

        // 2. Solid Quartz Waterfall Countertop Slab
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w, topThick, d, h - topThick, ColorRGBA(0.98f, 0.98f, 1.0f, 1f), roughness = 0.15f)

        // 3. Flush Induction Cooktop on island top
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0.05f, 0.60f, 0.006f, 0.45f, h, ColorRGBA(0.08f, 0.10f, 0.12f, 1f), roughness = 0.08f)
        // Burner rings
        renderLocalCylinder(item.position, item.rotationDegrees, -0.14f, 0.05f, 0.18f, 0.003f, h + 0.005f, ColorRGBA(0.40f, 0.44f, 0.50f, 1f), metallic = 0.7f)
        renderLocalCylinder(item.position, item.rotationDegrees, 0.14f, 0.05f, 0.20f, 0.003f, h + 0.005f, ColorRGBA(0.40f, 0.44f, 0.50f, 1f), metallic = 0.7f)

        // 4. Two Designer Modern Bar Stools under the breakfast bar overhang (-Z)
        val stoolZ = -d * 0.5f - 0.15f
        val stoolOffsets = listOf(-halfW * 0.45f, halfW * 0.45f)
        for (sx in stoolOffsets) {
            val stoolSeatH = 0.68f
            // 4 Slender tapered legs
            for (dx in listOf(-0.14f, 0.14f)) {
                for (dz in listOf(-0.14f, 0.14f)) {
                    renderLocalFrustum(
                        item.position, item.rotationDegrees,
                        sx + dx, stoolZ + dz,
                        0.024f, 0.014f, stoolSeatH - 0.02f, 0f,
                        CoffeeTableLegColor, roughness = 0.3f, metallic = 0.7f
                    )
                }
            }
            // Torus footrest ring
            renderLocalTorus(
                item.position, item.rotationDegrees,
                sx, stoolZ,
                0.28f, 0.016f, 0.24f,
                ChromeColor, metallic = 0.95f
            )
            // Round leather padded seat (smooth cylinder + cushion crown)
            renderLocalCylinder(
                item.position, item.rotationDegrees,
                sx, stoolZ,
                0.36f, 0.045f, stoolSeatH,
                ColorRGBA(0.18f, 0.22f, 0.28f, 1f), roughness = 0.55f
            )
            renderLocalPillow(
                item.position, item.rotationDegrees,
                sx, stoolZ,
                0.34f, 0.035f, 0.34f, stoolSeatH + 0.035f,
                ColorRGBA(0.18f, 0.22f, 0.28f, 1f), roughness = 0.65f
            )
        }
    }

    private fun FrameScope.renderCookingStove(item: PlacedItem, isSelected: Boolean) {
        val bodyCol = if (isSelected) WallSelectedColor else ColorRGBA(0.20f, 0.23f, 0.28f, 1f)
        val w = item.widthCm / 100f
        val d = item.depthCm / 100f
        val h = 0.88f
        val halfW = w * 0.5f
        val halfD = d * 0.5f

        // 1. Stove body with beveled edges
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w, h - 0.04f, d, 0f, bodyCol, metallic = 0.6f, roughness = 0.3f)
        // 2. Glossy ceramic-glass cooktop with beveled profile
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w, 0.04f, d, h - 0.04f, ColorRGBA(0.06f, 0.08f, 0.10f, 1f), roughness = 0.08f)

        // 3. 4 Burner rings (concentric smooth cylinders & rings)
        val rX = halfW * 0.45f
        val rZ = halfD * 0.42f
        for (dx in listOf(-rX, rX)) {
            for (dz in listOf(-rZ, rZ)) {
                val burnerDia = if (dx > 0 && dz < 0) 0.20f else 0.16f
                renderLocalCylinder(item.position, item.rotationDegrees, dx, dz, burnerDia, 0.006f, h, ColorRGBA(0.35f, 0.38f, 0.42f, 1f), metallic = 0.7f)
                renderLocalCylinder(item.position, item.rotationDegrees, dx, dz, burnerDia * 0.5f, 0.008f, h, ColorRGBA(0.70f, 0.20f, 0.15f, 1f))
            }
        }

        // 4. Control panel with turned cylindrical knobs
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, -halfD - 0.008f, w - 0.04f, 0.08f, 0.015f, h - 0.14f, ChromeColor, metallic = 0.85f, roughness = 0.2f)
        for (k in 0..3) {
            val kx = -w * 0.30f + k * (w * 0.20f)
            renderLocalCylinder(item.position, item.rotationDegrees, kx, -halfD - 0.016f, 0.038f, 0.020f, h - 0.12f, ColorRGBA(0.12f, 0.14f, 0.18f, 1f), metallic = 0.6f)
            renderLocalSphere(item.position, item.rotationDegrees, kx, -halfD - 0.025f, 0.016f, h - 0.11f, ChromeColor, metallic = 0.95f)
        }

        // 5. Oven door with beveled glass window and horizontal chrome tubular bar handle
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, -halfD - 0.005f, w * 0.78f, h * 0.48f, 0.015f, 0.16f, ColorRGBA(0.04f, 0.06f, 0.08f, 1f), roughness = 0.1f)
        renderLocalCylinder(item.position, item.rotationDegrees, 0f, -halfD - 0.035f, 0.022f, w * 0.65f, h * 0.65f, ChromeColor, extraRotationDeg = 90f, metallic = 0.95f)
    }

    private fun FrameScope.renderRefrigerator(item: PlacedItem, isSelected: Boolean) {
        val col = if (isSelected) WallSelectedColor else StainlessSteelColor
        val w = item.widthCm / 100f
        val d = item.depthCm / 100f
        val h = 1.95f
        val halfW = w * 0.5f
        val halfD = d * 0.5f

        // 1. Recessed Base Ventilation Plinth
        val plinthH = 0.06f
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0.02f, w - 0.02f, plinthH, d - 0.04f, 0f, CharcoalMetalColor)
        for (v in 0..2) {
            val vy = 0.012f + v * 0.016f
            renderLocalBox(item.position, item.rotationDegrees, 0f, -halfD + 0.015f, w * 0.85f, 0.006f, 0.005f, vy, ColorRGBA.Black)
        }

        // 2. Main Casing with beveled edges
        val bodyH = h - plinthH
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w, bodyH, d, plinthH, col, metallic = 0.65f, roughness = 0.24f)

        val frontZ = -halfD - 0.008f

        if (item.itemType == ItemType.REFRIGERATOR_SIDE_BY_SIDE) {
            val freezerW = w * 0.40f
            val fridgeW = w - freezerW - 0.015f
            val doorH = bodyH - 0.02f
            val doorY = plinthH + 0.01f

            val fX = -halfW + freezerW * 0.5f
            val rX = halfW - fridgeW * 0.5f

            // Freezer Door (Left) with beveled profile
            renderLocalBeveledBox(item.position, item.rotationDegrees, fX, frontZ, freezerW - 0.008f, doorH, 0.025f, doorY, col, metallic = 0.65f, roughness = 0.22f)
            // Fridge Door (Right) with beveled profile
            renderLocalBeveledBox(item.position, item.rotationDegrees, rX, frontZ, fridgeW - 0.008f, doorH, 0.025f, doorY, col, metallic = 0.65f, roughness = 0.22f)

            // Ice & Water Dispenser Alcove on Freezer Door
            val dispW = freezerW * 0.68f
            val dispH = 0.28f
            val dispY = 1.05f
            renderLocalBeveledBox(item.position, item.rotationDegrees, fX, frontZ - 0.012f, dispW, dispH, 0.02f, dispY, CharcoalMetalColor, roughness = 0.2f)
            renderLocalBox(item.position, item.rotationDegrees, fX, frontZ - 0.018f, dispW * 0.88f, dispH * 0.85f, 0.008f, dispY + dispH * 0.08f, ColorRGBA(0.08f, 0.10f, 0.14f, 1f))
            renderLocalBox(item.position, item.rotationDegrees, fX, frontZ - 0.022f, dispW * 0.45f, 0.015f, 0.004f, dispY + dispH - 0.035f, ColorRGBA(0.2f, 0.75f, 1f, 1f))
            renderLocalCylinder(item.position, item.rotationDegrees, fX, frontZ - 0.015f, 0.035f, 0.08f, dispY + 0.06f, ChromeColor, metallic = 0.95f)

            // Long Full-Height Vertical Tubular Handles
            val handleH = 1.05f
            val handleY = 0.70f
            renderLocalCylinder(item.position, item.rotationDegrees, fX + freezerW * 0.5f - 0.045f, frontZ - 0.03f, 0.022f, handleH, handleY, ChromeColor, metallic = 0.95f, roughness = 0.1f)
            renderLocalCylinder(item.position, item.rotationDegrees, rX - fridgeW * 0.5f + 0.045f, frontZ - 0.03f, 0.022f, handleH, handleY, ChromeColor, metallic = 0.95f, roughness = 0.1f)
        } else {
            val freezerH = bodyH * 0.38f
            val fridgeH = bodyH - freezerH - 0.03f
            val doorW = w - 0.01f

            val fridgeY = plinthH + 0.01f
            renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, frontZ, doorW, fridgeH, 0.025f, fridgeY, col, metallic = 0.65f, roughness = 0.22f)
            val freezerY = fridgeY + fridgeH + 0.015f
            renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, frontZ, doorW, freezerH, 0.025f, freezerY, col, metallic = 0.65f, roughness = 0.22f)

            // Digital Touch Control & Temperature Display Panel on upper door
            renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, frontZ - 0.012f, 0.16f, 0.065f, 0.008f, freezerY + freezerH * 0.5f - 0.032f, ColorRGBA(0.06f, 0.08f, 0.10f, 1f))
            renderLocalBox(item.position, item.rotationDegrees, 0f, frontZ - 0.016f, 0.12f, 0.018f, 0.004f, freezerY + freezerH * 0.5f - 0.01f, ColorRGBA(0.2f, 0.85f, 1.0f, 1f))

            // Sleek Horizontal Brushed Chrome Handles
            renderLocalCylinder(item.position, item.rotationDegrees, 0f, frontZ - 0.028f, 0.022f, doorW * 0.55f, fridgeY + fridgeH - 0.06f, ChromeColor, extraRotationDeg = 90f, metallic = 0.95f, roughness = 0.1f)
            renderLocalCylinder(item.position, item.rotationDegrees, 0f, frontZ - 0.028f, 0.022f, doorW * 0.55f, freezerY + 0.04f, ChromeColor, extraRotationDeg = 90f, metallic = 0.95f, roughness = 0.1f)
        }
    }

    private fun FrameScope.renderBathtub(item: PlacedItem, isSelected: Boolean) {
        val col = if (isSelected) WallSelectedColor else CeramicWhiteColor
        val w = item.widthCm / 100f
        val d = item.depthCm / 100f
        val h = 0.62f
        val halfW = w * 0.5f
        val halfD = d * 0.5f

        // 1. Acrylic Skirt / Apron with beveled edge
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w, h, d, 0f, col, roughness = 0.18f)

        // 2. Recessed Inner Tub Basin Cavity
        val rim = 0.07f
        val innerW = w - rim * 2f
        val innerD = d - rim * 2f
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, innerW, 0.02f, innerD, h - 0.03f, ColorRGBA(0.92f, 0.94f, 0.96f, 1f), roughness = 0.15f)

        // 3. Relaxing Light Blue Semi-Transparent Water Pool
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, innerW - 0.02f, 0.015f, innerD - 0.02f, h - 0.10f, WaterColor, transparent = true)

        // 4. Chrome Drain Plug & Overflow Rosette
        val drainX = -halfW + rim + 0.20f
        renderLocalCylinder(item.position, item.rotationDegrees, drainX, 0f, 0.07f, 0.005f, h - 0.09f, ChromeColor, metallic = 0.95f)
        renderLocalCylinder(item.position, item.rotationDegrees, -halfW + rim + 0.02f, 0f, 0.05f, 0.01f, h - 0.08f, ChromeColor, metallic = 0.95f)

        // 5. Wall-Mounted Bath Mixer Tap & Hand Shower Wand
        val tapX = halfW - rim - 0.12f
        val tapZ = halfD - 0.03f
        renderLocalCylinder(item.position, item.rotationDegrees, tapX, tapZ, 0.05f, 0.06f, h + 0.05f, ChromeColor, metallic = 0.95f)
        renderLocalCylinder(item.position, item.rotationDegrees, tapX, tapZ - 0.08f, 0.025f, 0.12f, h + 0.07f, ChromeColor, extraRotationDeg = 90f, metallic = 0.95f)
        renderLocalCylinder(item.position, item.rotationDegrees, tapX + 0.12f, tapZ, 0.03f, 0.22f, h + 0.08f, ChromeColor, metallic = 0.95f)
    }

    private fun FrameScope.renderFreestandingBathtub(item: PlacedItem, isSelected: Boolean) {
        val col = if (isSelected) WallSelectedColor else ColorRGBA.White
        val w = item.widthCm / 100f
        val d = item.depthCm / 100f
        val h = 0.62f

        // Freestanding soaking tub: beveled body with soft rounded curvature
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w, h, d, 0f, col, roughness = 0.15f)
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w - 0.14f, 0.02f, d - 0.14f, h - 0.08f, ColorRGBA(0.60f, 0.85f, 0.95f, 0.7f), transparent = true)

        // Luxury floor-standing cylindrical mixer pillar with curved spout
        val tapX = w * 0.5f + 0.12f
        renderLocalCylinder(item.position, item.rotationDegrees, tapX, 0f, 0.055f, 0.95f, 0f, ChromeColor, metallic = 0.95f, roughness = 0.1f)
        renderLocalCylinder(item.position, item.rotationDegrees, tapX - 0.08f, 0f, 0.032f, 0.18f, 0.92f, ChromeColor, extraRotationDeg = 90f, metallic = 0.95f, roughness = 0.1f)
        renderLocalSphere(item.position, item.rotationDegrees, tapX, 0f, 0.06f, 0.95f, ChromeColor, metallic = 0.95f)
    }

    private fun FrameScope.renderShowerCabin(item: PlacedItem, isSelected: Boolean) {
        val baseCol = if (isSelected) WallSelectedColor else ColorRGBA.White
        val w = item.widthCm / 100f
        val d = item.depthCm / 100f
        val h = 2.15f
        val halfW = w * 0.5f
        val halfD = d * 0.5f
        val trayH = 0.14f

        // 1. Low-profile beveled acrylic shower tray
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w, trayH, d, 0f, baseCol, roughness = 0.2f)
        // Chrome drain grate
        renderLocalCylinder(item.position, item.rotationDegrees, -halfW + 0.18f, halfD - 0.18f, 0.12f, 0.005f, trayH, ChromeColor, metallic = 0.95f)

        // 2. Extruded chrome frame uprights with beveled profiles
        val frameThick = 0.035f
        val glassH = h - trayH
        renderLocalBeveledBox(item.position, item.rotationDegrees, -halfW + frameThick * 0.5f, -halfD + frameThick * 0.5f, frameThick, glassH, frameThick, trayH, ChromeColor, metallic = 0.88f)
        renderLocalBeveledBox(item.position, item.rotationDegrees, halfW - frameThick * 0.5f, -halfD + frameThick * 0.5f, frameThick, glassH, frameThick, trayH, ChromeColor, metallic = 0.88f)
        renderLocalBeveledBox(item.position, item.rotationDegrees, halfW - frameThick * 0.5f, halfD - frameThick * 0.5f, frameThick, glassH, frameThick, trayH, ChromeColor, metallic = 0.88f)

        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, -halfD + frameThick * 0.5f, w, frameThick, frameThick, h - frameThick, ChromeColor, metallic = 0.88f)
        renderLocalBeveledBox(item.position, item.rotationDegrees, halfW - frameThick * 0.5f, 0f, frameThick, frameThick, d, h - frameThick, ChromeColor, metallic = 0.88f)

        // 3. Safety tempered transparent glass walls
        renderLocalBox(item.position, item.rotationDegrees, 0f, -halfD + 0.015f, w - frameThick * 2f, glassH - 0.05f, 0.012f, trayH + 0.02f, GlassColor, transparent = true, roughness = 0.05f)
        renderLocalBox(item.position, item.rotationDegrees, halfW - 0.015f, 0f, 0.012f, glassH - 0.05f, d - frameThick * 2f, trayH + 0.02f, GlassColor, transparent = true, roughness = 0.05f)

        // 4. Chrome Rain Shower System: vertical column, disc showerhead, mixer valve
        val mixerZ = halfD - 0.03f
        renderLocalCylinder(item.position, item.rotationDegrees, 0f, mixerZ, 0.025f, 1.15f, 0.90f, ChromeColor, metallic = 0.95f)
        renderLocalCylinder(item.position, item.rotationDegrees, 0f, mixerZ - 0.05f, 0.055f, 0.06f, 1.05f, ChromeColor, metallic = 0.95f)
        renderLocalCylinder(item.position, item.rotationDegrees, 0f, mixerZ - 0.15f, 0.022f, 0.30f, 2.05f, ChromeColor, extraRotationDeg = 90f, metallic = 0.95f)
        // Rain showerhead disc (smooth 28-gon)
        renderLocalCylinder(item.position, item.rotationDegrees, 0f, mixerZ - 0.28f, 0.24f, 0.015f, 2.03f, ChromeColor, metallic = 0.95f, segments = 28)
        renderLocalSphere(item.position, item.rotationDegrees, 0f, mixerZ - 0.28f, 0.032f, 2.05f, ChromeColor, metallic = 0.95f)
    }

    private fun FrameScope.renderToilet(item: PlacedItem, isSelected: Boolean) {
        val col = if (isSelected) WallSelectedColor else CeramicWhiteColor
        val w = item.widthCm / 100f
        val d = item.depthCm / 100f
        val halfW = w * 0.5f
        val halfD = d * 0.5f

        // Local orientation:
        // Local +Z is against wall (Cistern tank)
        // Local -Z is facing forward into bathroom (Bowl)

        // 1. Ceramic Base Pedestal with beveled contours
        val pedW = w * 0.55f
        val pedD = d * 0.65f
        val pedH = 0.40f
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, -0.02f, pedW, pedH, pedD, 0f, col, roughness = 0.15f)

        // 2. Rear Cistern Tank with Beveled Lid (+Z)
        val tankW = w * 0.92f
        val tankD = d * 0.38f
        val tankH = 0.46f
        val tankZ = halfD - tankD * 0.5f
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, tankZ, tankW, tankH, tankD, pedH, col, roughness = 0.15f)
        // Cistern Top Lid with bevel
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, tankZ, tankW + 0.02f, 0.03f, tankD + 0.02f, pedH + tankH, col, roughness = 0.15f)
        // Dual-Flush Chrome Push Buttons on Top Lid (smooth cylinders)
        renderLocalCylinder(item.position, item.rotationDegrees, 0f, tankZ, 0.065f, 0.008f, pedH + tankH + 0.03f, ChromeColor, metallic = 0.95f)
        renderLocalCylinder(item.position, item.rotationDegrees, -0.015f, tankZ, 0.022f, 0.012f, pedH + tankH + 0.035f, ChromeColor, metallic = 0.95f)
        renderLocalCylinder(item.position, item.rotationDegrees, 0.015f, tankZ, 0.022f, 0.012f, pedH + tankH + 0.035f, ChromeColor, metallic = 0.95f)

        // 3. Round Ergonomic Bowl (-Z) (smooth 28-gon cylinder)
        val bowlDia = min(w * 0.88f, d * 0.55f)
        val bowlZ = -halfD + bowlDia * 0.5f + 0.02f
        renderLocalCylinder(item.position, item.rotationDegrees, 0f, bowlZ, bowlDia, 0.28f, 0.14f, col, roughness = 0.15f, segments = 28)

        // 4. Closed Soft-Close Ceramic Toilet Seat & Lid with Beveled Profile
        val seatH = 0.035f
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, bowlZ, bowlDia + 0.02f, seatH, bowlDia + 0.04f, 0.42f, col, roughness = 0.12f)

        // Chrome Hinges connecting seat to cistern
        renderLocalCylinder(item.position, item.rotationDegrees, -0.08f, tankZ - tankD * 0.5f, 0.028f, 0.03f, 0.42f, ChromeColor, metallic = 0.95f)
        renderLocalCylinder(item.position, item.rotationDegrees, 0.08f, tankZ - tankD * 0.5f, 0.028f, 0.03f, 0.42f, ChromeColor, metallic = 0.95f)
    }

    private fun FrameScope.renderBidet(item: PlacedItem, isSelected: Boolean) {
        val col = if (isSelected) WallSelectedColor else CeramicWhiteColor
        val w = item.widthCm / 100f
        val d = item.depthCm / 100f
        val halfD = d * 0.5f
        val bidetH = 0.42f

        // 1. Ceramic Pedestal Base with beveled contours
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w * 0.60f, bidetH * 0.6f, d * 0.70f, 0f, col, roughness = 0.18f)

        // 2. Rounded Bowl Body with beveled edges
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w, bidetH * 0.4f, d, bidetH * 0.6f, col, roughness = 0.18f)

        // 3. Inner Basin Cavity with chrome drain plug
        val basinW = w - 0.12f
        val basinD = d - 0.16f
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, -0.02f, basinW, 0.015f, basinD, bidetH - 0.02f, ColorRGBA(0.92f, 0.94f, 0.96f, 1f))
        renderLocalCylinder(item.position, item.rotationDegrees, 0f, -0.04f, 0.065f, 0.005f, bidetH - 0.015f, ChromeColor, metallic = 0.95f)

        // 4. Chrome Mixer Faucet with Swivel Aerator Nozzle at rear (+Z)
        val faucetZ = halfD - 0.08f
        renderLocalCylinder(item.position, item.rotationDegrees, 0f, faucetZ, 0.045f, 0.08f, bidetH, ChromeColor, metallic = 0.95f)
        renderLocalCylinder(item.position, item.rotationDegrees, 0f, faucetZ - 0.04f, 0.022f, 0.08f, bidetH + 0.07f, ChromeColor, extraRotationDeg = 90f, metallic = 0.95f)
        renderLocalCylinder(item.position, item.rotationDegrees, -0.06f, faucetZ, 0.018f, 0.04f, bidetH + 0.04f, ChromeColor, extraRotationDeg = 90f, metallic = 0.95f)
        renderLocalCylinder(item.position, item.rotationDegrees, 0.06f, faucetZ, 0.018f, 0.04f, bidetH + 0.04f, ChromeColor, extraRotationDeg = 90f, metallic = 0.95f)
    }

    private fun FrameScope.renderWashbasin(item: PlacedItem, isSelected: Boolean) {
        val col = if (isSelected) WallSelectedColor else CeramicWhiteColor
        val w = item.widthCm / 100f
        val d = item.depthCm / 100f
        val halfW = w * 0.5f
        val halfD = d * 0.5f
        val isDouble = (item.itemType == ItemType.WASHBASIN_DOUBLE || w >= 1.20f)

        // 1. Vanity Cabinet with beveled edges (y = 0.22m to 0.82m)
        val cabY = 0.22f
        val cabH = 0.60f
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w, cabH, d - 0.02f, cabY, ColorRGBA(0.92f, 0.94f, 0.96f, 1f), roughness = 0.35f)

        // 2. Cabinet Doors / Drawers with Shaker Bevels & Cylindrical Chrome Handles
        val frontZ = -halfD + 0.005f
        if (isDouble) {
            val dW = (w - 0.03f) / 4f
            for (i in 0..3) {
                val dx = -halfW + dW * 0.5f + 0.01f + i * dW
                renderLocalBeveledBox(item.position, item.rotationDegrees, dx, frontZ - 0.008f, dW - 0.01f, cabH - 0.04f, 0.016f, cabY + 0.02f, ColorRGBA(0.94f, 0.95f, 0.97f, 1f))
                renderLocalCylinder(item.position, item.rotationDegrees, dx, frontZ - 0.018f, 0.016f, dW * 0.5f, cabY + cabH - 0.12f, ChromeColor, extraRotationDeg = 90f, metallic = 0.95f)
            }
        } else {
            val dW = (w - 0.02f) / 2f
            for (i in 0..1) {
                val dx = -halfW + dW * 0.5f + 0.01f + i * dW
                renderLocalBeveledBox(item.position, item.rotationDegrees, dx, frontZ - 0.008f, dW - 0.01f, cabH - 0.04f, 0.016f, cabY + 0.02f, ColorRGBA(0.94f, 0.95f, 0.97f, 1f))
                renderLocalCylinder(item.position, item.rotationDegrees, dx, frontZ - 0.018f, 0.016f, dW * 0.5f, cabY + cabH - 0.12f, ChromeColor, extraRotationDeg = 90f, metallic = 0.95f)
            }
        }

        // 3. Solid Quartz / Marble Countertop Slab with Beveled Chamfers
        val topThick = 0.035f
        val topY = cabY + cabH
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w + 0.02f, topThick, d + 0.02f, topY, CeramicWhiteColor, roughness = 0.15f)

        // 4. Ceramic Sink Basin(s) and Mixer Faucet(s)
        val sinkOffsets = if (isDouble) listOf(-w * 0.26f, w * 0.26f) else listOf(0f)
        val basinW = if (isDouble) min(0.48f, w * 0.40f) else min(0.55f, w * 0.75f)
        val basinD = d * 0.65f

        for (sx in sinkOffsets) {
            renderLocalBeveledBox(item.position, item.rotationDegrees, sx, -0.02f, basinW, 0.015f, basinD, topY + topThick - 0.01f, ColorRGBA(0.90f, 0.93f, 0.96f, 1f), roughness = 0.1f)
            renderLocalCylinder(item.position, item.rotationDegrees, sx, -0.02f, 0.065f, 0.006f, topY + topThick, ChromeColor, metallic = 0.95f)

            // Gooseneck mixer faucet
            val faucetZ = halfD - 0.06f
            renderLocalCylinder(item.position, item.rotationDegrees, sx, faucetZ, 0.045f, 0.04f, topY + topThick, ChromeColor, metallic = 0.95f)
            renderLocalCylinder(item.position, item.rotationDegrees, sx, faucetZ, 0.022f, 0.24f, topY + topThick + 0.04f, ChromeColor, metallic = 0.95f)
            renderLocalCylinder(item.position, item.rotationDegrees, sx, faucetZ - 0.05f, 0.02f, 0.10f, topY + topThick + 0.26f, ChromeColor, extraRotationDeg = 90f, metallic = 0.95f)
            renderLocalCylinder(item.position, item.rotationDegrees, sx + 0.035f, faucetZ, 0.014f, 0.05f, topY + topThick + 0.12f, ChromeColor, extraRotationDeg = 45f, metallic = 0.95f)
        }

        // 5. Frameless LED Backlit Mirror Mounted on Wall (+Z)
        val mirrorW = w * 0.92f
        val mirrorH = 0.75f
        val mirrorY = topY + topThick + 0.22f
        val mirrorZ = halfD - 0.008f
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, mirrorZ, mirrorW, mirrorH, 0.015f, mirrorY, GlassColor, metallic = 0.95f, roughness = 0.02f)
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, mirrorZ + 0.008f, mirrorW + 0.04f, mirrorH + 0.04f, 0.005f, mirrorY - 0.02f, ColorRGBA(0.95f, 0.92f, 0.82f, 0.6f))
    }

    private fun FrameScope.renderWashingMachine(item: PlacedItem, isSelected: Boolean) {
        val bodyCol = if (isSelected) WallSelectedColor else ColorRGBA(0.96f, 0.97f, 0.98f, 1f)
        val w = item.widthCm / 100f
        val d = item.depthCm / 100f
        val h = 0.85f
        val halfW = w * 0.5f
        val halfD = d * 0.5f

        // 1. Anti-vibration cabinet body with beveled CAD edges
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w, h, d, 0f, bodyCol, roughness = 0.25f)

        // 2. Control fascia panel with detergent drawer & rotary dial
        val panelH = 0.14f
        val panelY = h - panelH
        renderLocalBeveledBox(item.position, item.rotationDegrees, -halfW + 0.10f, -halfD - 0.005f, 0.14f, 0.09f, 0.015f, panelY + 0.02f, ColorRGBA(0.88f, 0.90f, 0.94f, 1f), roughness = 0.3f)
        // Rotary program selector knob (smooth cylinder)
        renderLocalCylinder(item.position, item.rotationDegrees, 0f, -halfD - 0.015f, 0.058f, 0.025f, panelY + 0.04f, ChromeColor, metallic = 0.95f, roughness = 0.15f)
        // Digital LED cycle status display
        renderLocalBeveledBox(item.position, item.rotationDegrees, halfW - 0.10f, -halfD - 0.005f, 0.12f, 0.06f, 0.01f, panelY + 0.04f, ColorRGBA(0.08f, 0.12f, 0.16f, 1f), roughness = 0.1f)
        renderLocalBox(item.position, item.rotationDegrees, halfW - 0.10f, -halfD - 0.012f, 0.09f, 0.025f, 0.005f, panelY + 0.055f, ColorRGBA(0.1f, 0.85f, 1.0f, 1f))

        // 3. Large front-loading porthole door with chrome circular rim & dark glass
        val drumY = 0.40f
        val drumOuterR = 0.22f
        renderLocalCylinder(item.position, item.rotationDegrees, 0f, -halfD - 0.015f, drumOuterR * 2f, 0.025f, drumY - drumOuterR, ChromeColor, extraRotationDeg = 90f, metallic = 0.95f, roughness = 0.12f, segments = 28)
        renderLocalTorus(item.position, item.rotationDegrees, 0f, -halfD - 0.022f, drumOuterR * 2f, 0.022f, drumY, ChromeColor, extraRotationDeg = 90f)
        renderLocalCylinder(item.position, item.rotationDegrees, 0f, -halfD - 0.020f, (drumOuterR - 0.04f) * 2f, 0.018f, drumY - (drumOuterR - 0.04f), ColorRGBA(0.08f, 0.15f, 0.22f, 0.85f), extraRotationDeg = 90f, transparent = true, roughness = 0.05f, segments = 28)
        // Chrome door latch handle
        renderLocalCylinder(item.position, item.rotationDegrees, drumOuterR - 0.02f, -halfD - 0.03f, 0.022f, 0.08f, drumY - 0.04f, ChromeColor, metallic = 0.95f)
    }

    private fun FrameScope.renderOfficeDesk(item: PlacedItem, isSelected: Boolean) {
        val topColor = if (isSelected) WallSelectedColor else CoffeeTableTopColor
        val frameColor = CharcoalMetalColor
        val w = item.widthCm / 100f
        val d = item.depthCm / 100f
        val halfW = w * 0.5f
        val halfD = d * 0.5f
        val deskH = 0.75f
        val topThick = 0.038f
        val legH = deskH - topThick

        // 1. Natural Solid Oak / Walnut Desk Top with Beveled Edges
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w, topThick, d, legH, topColor, roughness = 0.28f)

        // 2. Matte Anthracite Steel U-Frame Legs with Beveled Profiles
        val legThick = 0.045f
        renderLocalBeveledBox(item.position, item.rotationDegrees, -halfW + 0.05f, 0f, legThick, legH, d * 0.82f, 0f, frameColor, metallic = 0.8f, roughness = 0.25f)
        renderLocalBeveledBox(item.position, item.rotationDegrees, halfW - 0.05f, 0f, legThick, legH, d * 0.82f, 0f, frameColor, metallic = 0.8f, roughness = 0.25f)

        // 3. Privacy Modesty Panel along the rear (+Z)
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, halfD - 0.08f, w * 0.85f, 0.35f, 0.018f, legH * 0.45f, frameColor, metallic = 0.7f)

        // 4. Drawer Pedestal Unit Under Right Side with Beveled Drawers & Chrome Handles
        val pedW = min(0.42f, w * 0.32f)
        val pedD = d * 0.78f
        val pedH = legH - 0.04f
        val pedX = halfW - pedW * 0.5f - 0.08f
        renderLocalBeveledBox(item.position, item.rotationDegrees, pedX, 0f, pedW, pedH, pedD, 0.04f, CoffeeTableShelfColor, roughness = 0.35f)
        val dH = (pedH - 0.03f) / 3f
        for (i in 0..2) {
            val hy = 0.055f + i * dH + dH * 0.5f
            renderLocalCylinder(item.position, item.rotationDegrees, pedX, -pedD * 0.5f - 0.012f, 0.016f, pedW * 0.45f, hy, ChromeColor, extraRotationDeg = 90f, metallic = 0.95f)
        }

        // 5. Desktop Accessories:
        // Center Leather Blotter with beveled edge
        renderLocalBeveledBox(item.position, item.rotationDegrees, -0.08f, -0.04f, 0.70f, 0.005f, 0.42f, deskH, CharcoalMetalColor, roughness = 0.75f)
        // Cable grommet at rear right corner (smooth cylinder)
        renderLocalCylinder(item.position, item.rotationDegrees, halfW - 0.16f, halfD - 0.12f, 0.07f, 0.006f, deskH, ChromeColor, metallic = 0.9f)
        // Slim open laptop with beveled chassis
        renderLocalBeveledBox(item.position, item.rotationDegrees, -0.08f, -0.04f, 0.32f, 0.008f, 0.22f, deskH + 0.005f, StainlessSteelColor, metallic = 0.85f)
        renderLocalBeveledBox(item.position, item.rotationDegrees, -0.08f, 0.07f, 0.32f, 0.20f, 0.008f, deskH + 0.01f, CharcoalMetalColor, extraRotationDeg = -15f)
    }

    private fun FrameScope.renderCornerDesk(item: PlacedItem, isSelected: Boolean) {
        val topColor = if (isSelected) WallSelectedColor else CoffeeTableTopColor
        val legColor = WindowFrameColor

        val w = item.widthCm / 100f
        val d = item.depthCm / 100f
        val h = item.heightCm / 100f
        val halfW = w * 0.5f
        val halfD = d * 0.5f

        val cutW = w * 0.45f
        val cutH = d * 0.45f
        val topThick = 0.035f
        val legH = h - topThick

        // 1. L-shaped tabletop (two seamless connected sections with CAD Bevels)
        val rearD = d - cutH
        val rearZ = halfD - rearD * 0.5f
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, rearZ, w, topThick, rearD, legH, topColor, roughness = 0.28f)
        val chaiseW = w - cutW
        val chaiseX = -halfW + chaiseW * 0.5f
        val chaiseZ = -halfD + cutH * 0.5f
        renderLocalBeveledBox(item.position, item.rotationDegrees, chaiseX, chaiseZ, chaiseW, topThick, cutH, legH, topColor, roughness = 0.28f)

        // 2. Corner cable grommet (smooth cylinder)
        renderLocalCylinder(item.position, item.rotationDegrees, -halfW + 0.18f, halfD - 0.18f, 0.07f, 0.006f, h, WindowFrameColor, metallic = 0.8f)

        // 3. Steel frame legs at corners with smooth cylinders
        val legPositions = listOf(
            Pair(-halfW + 0.05f, halfD - 0.05f),
            Pair(halfW - 0.05f, halfD - 0.05f),
            Pair(halfW - 0.05f, -halfD + cutH + 0.05f),
            Pair(halfW - cutW + 0.05f, -halfD + cutH + 0.05f),
            Pair(halfW - cutW - 0.05f, -halfD + 0.05f),
            Pair(-halfW + 0.05f, -halfD + 0.05f)
        )
        for (lp in legPositions) {
            renderLocalCylinder(item.position, item.rotationDegrees, lp.first, lp.second, 0.045f, legH, 0f, legColor, metallic = 0.8f, roughness = 0.25f)
        }

        // 4. Drawer pedestal under right wing with beveled edges & cylindrical handles
        val pedW = cutW * 0.65f
        val pedD = rearD - 0.08f
        val pedH = legH - 0.04f
        val pedX = halfW - pedW * 0.5f - 0.04f
        renderLocalBeveledBox(item.position, item.rotationDegrees, pedX, rearZ, pedW, pedH, pedD, 0.04f, CoffeeTableShelfColor, roughness = 0.35f)
        for (i in 0..2) {
            val handleY = 0.08f + i * (pedH / 3f) + (pedH / 6f)
            renderLocalCylinder(item.position, item.rotationDegrees, pedX, rearZ - pedD * 0.5f - 0.012f, 0.015f, pedW * 0.45f, handleY, ChromeColor, extraRotationDeg = 90f, metallic = 0.95f)
        }
    }

    private fun FrameScope.renderOfficeChair(item: PlacedItem, isSelected: Boolean) {
        val col = if (isSelected) WallSelectedColor else ColorRGBA(0.12f, 0.16f, 0.23f, 1f)
        val metalCol = ColorRGBA(0.25f, 0.30f, 0.38f, 1f)

        // 1. 5-Star Caster Base with Tapered Spokes & Dual Caster Wheels
        for (i in 0 until 5) {
            val ang = (i * 72f) * (PI.toFloat() / 180f)
            val legLen = 0.26f
            val lx = sin(ang) * (legLen * 0.5f)
            val lz = cos(ang) * (legLen * 0.5f)
            renderLocalFrustum(
                item.position, item.rotationDegrees,
                lx, lz,
                0.035f, 0.020f, legLen, 0.06f,
                metalCol, extraRotationDeg = -i * 72f + 90f, metallic = 0.85f
            )
            // Dual wheels at leg tip (smooth cylinders)
            val wx = sin(ang) * legLen
            val wz = cos(ang) * legLen
            renderLocalCylinder(item.position, item.rotationDegrees, wx, wz, 0.045f, 0.04f, 0.02f, ColorRGBA(0.08f, 0.10f, 0.14f, 1f))
        }

        // 2. Central Pneumatic Column / Gas Lift Cylinder (smooth cylinder + telescoping sleeve)
        renderLocalCylinder(item.position, item.rotationDegrees, 0f, 0f, 0.065f, 0.40f, 0.06f, ChromeColor, metallic = 0.95f, roughness = 0.12f)
        renderLocalCylinder(item.position, item.rotationDegrees, 0f, 0f, 0.075f, 0.12f, 0.06f, metalCol, metallic = 0.8f)

        // 3. Ergonomic Saddle Seat Cushion (smooth 28-gon cylinder + sinusoidal comfort crown)
        val seatDia = 0.48f
        val seatH = 0.08f
        val seatY = 0.45f
        renderLocalCylinder(item.position, item.rotationDegrees, 0f, 0f, seatDia, seatH, seatY, col, roughness = 0.65f, segments = 28)
        renderLocalPillow(item.position, item.rotationDegrees, 0f, 0f, seatDia - 0.02f, 0.035f, seatDia - 0.02f, seatY + seatH * 0.6f, col, roughness = 0.65f)

        // 4. Backrest support post & Ergonomic curved backrest with beveled profile at -Z
        val backZ = -0.19f
        renderLocalCylinder(item.position, item.rotationDegrees, 0f, backZ + 0.04f, 0.045f, 0.36f, seatY + 0.04f, metalCol, metallic = 0.8f)
        // Main backrest cushion with beveled edge
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, backZ, 0.44f, 0.38f, 0.045f, seatY + 0.14f, col, roughness = 0.65f)
        // Lateral curve wings for lumbar support
        renderLocalBeveledBox(item.position, item.rotationDegrees, -0.18f, backZ + 0.02f, 0.12f, 0.34f, 0.035f, seatY + 0.16f, col, extraRotationDeg = -15f, roughness = 0.65f)
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0.18f, backZ + 0.02f, 0.12f, 0.34f, 0.035f, seatY + 0.16f, col, extraRotationDeg = 15f, roughness = 0.65f)

        // 5. Left and Right Ergonomic Armrests (turned cylindrical risers + beveled pads)
        val armX = 0.23f
        for (side in listOf(-1f, 1f)) {
            val ax = armX * side
            renderLocalCylinder(item.position, item.rotationDegrees, ax, 0f, 0.032f, 0.18f, seatY + 0.02f, metalCol, metallic = 0.85f)
            renderLocalPillow(item.position, item.rotationDegrees, ax, 0f, 0.07f, 0.03f, 0.24f, seatY + 0.20f, ColorRGBA(0.15f, 0.18f, 0.24f, 1f), roughness = 0.55f)
        }
    }

    private fun FrameScope.renderFilingCabinet(item: PlacedItem, isSelected: Boolean) {
        val col = if (isSelected) WallSelectedColor else CoffeeTableShelfColor
        val w = item.widthCm / 100f
        val d = item.depthCm / 100f
        val h = 1.05f
        val halfD = d * 0.5f

        // Carcass with beveled edges
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w, h, d, 0f, col, roughness = 0.35f)

        // 3 Drawers with beveled profiles & cylindrical chrome bar handles
        val dH = (h - 0.04f) / 3f
        for (i in 0..2) {
            val dy = 0.02f + i * dH + dH * 0.5f
            renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, -halfD - 0.005f, w - 0.03f, dH - 0.015f, 0.015f, dy - (dH - 0.015f) * 0.5f, col, roughness = 0.35f)
            renderLocalCylinder(item.position, item.rotationDegrees, 0f, -halfD - 0.016f, 0.016f, w * 0.45f, dy - 0.008f, ChromeColor, extraRotationDeg = 90f, metallic = 0.95f)
        }
    }

    // =========================================================================
    // 8. HVAC & Air Engineering Equipment (Процедурные элементы ОВиК)
    // =========================================================================

    private fun FrameScope.renderHvacDiffuser(item: PlacedItem, isSelected: Boolean) {
        val w = item.widthCm / 100f
        val d = item.depthCm / 100f
        val col = if (isSelected) WallSelectedColor else ColorRGBA.White
        val y = 2.70f

        // 1. Ceiling mounting beveled flange frame (catches specular ceiling downlights)
        val flangeH = 0.025f
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w, flangeH, d, y, col, roughness = 0.25f)

        // 2. Three concentric stepped square aerodynamic louvers with beveled profiles
        val step1W = w * 0.78f
        val step1D = d * 0.78f
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, step1W, 0.020f, step1D, y - 0.006f, ColorRGBA(0.91f, 0.93f, 0.96f, 1f), roughness = 0.25f)

        val step2W = w * 0.55f
        val step2D = d * 0.55f
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, step2W, 0.018f, step2D, y - 0.012f, col, roughness = 0.25f)

        val step3W = w * 0.34f
        val step3D = d * 0.34f
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, step3W, 0.015f, step3D, y - 0.018f, ColorRGBA(0.01f, 0.55f, 0.82f, 1f), roughness = 0.3f)

        // 3. Center aerodynamic blanking core plate with beveled chamfer
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w * 0.16f, 0.012f, d * 0.16f, y - 0.022f, col, roughness = 0.22f)

        // 4. Aerodynamic 4-way diagonal vane dividers
        val veinThick = 0.008f
        for (ang in listOf(45f, -45f)) {
            renderLocalBox(item.position, item.rotationDegrees, 0f, 0f, w * 0.72f, 0.014f, veinThick, y - 0.012f, CharcoalMetalColor, extraRotationDeg = ang)
        }
    }

    private fun FrameScope.renderHvacRoundDiffuser(item: PlacedItem, isSelected: Boolean) {
        val dia = min(item.widthCm, item.depthCm) / 100f
        val col = if (isSelected) WallSelectedColor else ColorRGBA.White
        val y = 2.70f

        // 1. Outer ceiling mounting ring using smooth 28-gon cylinder + Torus trim bead
        renderLocalCylinder(item.position, item.rotationDegrees, 0f, 0f, dia, 0.025f, y, col, roughness = 0.25f, segments = 28)
        renderLocalTorus(item.position, item.rotationDegrees, 0f, 0f, dia + 0.015f, 0.010f, y + 0.012f, col)

        // 2. Three concentric smooth swirl rings (smooth 28-gon cylinders)
        renderLocalCylinder(item.position, item.rotationDegrees, 0f, 0f, dia * 0.74f, 0.020f, y - 0.006f, ColorRGBA(0.91f, 0.93f, 0.96f, 1f), roughness = 0.25f, segments = 28)
        renderLocalCylinder(item.position, item.rotationDegrees, 0f, 0f, dia * 0.48f, 0.018f, y - 0.012f, ColorRGBA(0.01f, 0.55f, 0.82f, 1f), roughness = 0.30f, segments = 28)
        renderLocalCylinder(item.position, item.rotationDegrees, 0f, 0f, dia * 0.26f, 0.015f, y - 0.018f, col, roughness = 0.25f, segments = 28)

        // 3. Central aerodynamic discharge cone hub (frustum) & center cap (sphere)
        renderLocalFrustum(item.position, item.rotationDegrees, 0f, 0f, dia * 0.12f, dia * 0.04f, 0.020f, y - 0.028f, col, roughness = 0.22f)
        renderLocalSphere(item.position, item.rotationDegrees, 0f, 0f, dia * 0.045f, y - 0.026f, ChromeColor, metallic = 0.95f)
    }

    private fun FrameScope.renderHvacGrille(item: PlacedItem, isSelected: Boolean) {
        val w = item.widthCm / 100f
        val h = item.heightCm / 100f
        val y0 = item.elevationCm / 100f
        val frameCol = if (isSelected) WallSelectedColor else ColorRGBA(0.86f, 0.89f, 0.93f, 1f)
        val bladeCol = ColorRGBA(0.92f, 0.42f, 0.10f, 1f) // Architectural accent damper color

        // 1. Extruded aluminum perimeter frame with beveled outer contours
        val depth = 0.045f
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w, h, depth, y0, frameCol, metallic = 0.75f, roughness = 0.25f)

        // 2. Recessed dark acoustic plenum cavity
        renderLocalBox(item.position, item.rotationDegrees, 0f, 0.010f, w - 0.06f, h - 0.06f, 0.025f, y0 + 0.03f, CharcoalMetalColor)

        // 3. Deflected aerofoil louvers with beveled profiles
        val bladeCount = max(3, (h / 0.075f).roundToInt())
        val bH = (h - 0.08f) / bladeCount
        for (b in 0 until bladeCount) {
            val by = y0 + 0.04f + b * bH
            renderLocalBeveledBox(
                item.position, item.rotationDegrees,
                0f, -0.005f,
                w - 0.06f, 0.012f, 0.022f,
                by, bladeCol,
                extraRotationDeg = -22f,
                roughness = 0.35f
            )
        }

        // 4. Central vertical tie-rod stiffening wire (smooth cylinder)
        renderLocalCylinder(item.position, item.rotationDegrees, 0f, -0.008f, 0.012f, h - 0.06f, y0 + 0.03f, StainlessSteelColor, metallic = 0.9f)
    }

    private fun FrameScope.renderHvacDuct(item: PlacedItem, isSelected: Boolean) {
        val w = item.widthCm / 100f
        val d = item.depthCm / 100f
        val col = if (isSelected) WallSelectedColor else StainlessSteelColor
        val y = 2.45f
        val ductH = 0.28f

        // 1. Main galvanized sheet metal duct trunk with beveled CAD profile
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w, ductH, d, y, col, metallic = 0.85f, roughness = 0.22f)

        // 2. TDC / Mez slide-on flange reinforcements at both ends with beveled collars
        val flangeSize = 0.038f
        val flangeY = y - 0.015f
        val flangeH = ductH + 0.030f
        val flangeD = d + 0.030f
        renderLocalBeveledBox(item.position, item.rotationDegrees, -w * 0.5f + flangeSize * 0.5f, 0f, flangeSize, flangeH, flangeD, flangeY, ChromeColor, metallic = 0.92f, roughness = 0.15f)
        renderLocalBeveledBox(item.position, item.rotationDegrees, w * 0.5f - flangeSize * 0.5f, 0f, flangeSize, flangeH, flangeD, flangeY, ChromeColor, metallic = 0.92f, roughness = 0.15f)

        // 3. Stiffening cross-broken sheet metal pattern (beveled diagonal crease relief)
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w * 0.82f, 0.006f, 0.022f, y + ductH, ColorRGBA(0.70f, 0.74f, 0.78f, 1f), metallic = 0.8f)
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, -d * 0.5f - 0.002f, w * 0.82f, 0.022f, 0.006f, y + ductH * 0.5f - 0.011f, ColorRGBA(0.70f, 0.74f, 0.78f, 1f), metallic = 0.8f)
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, d * 0.5f + 0.002f, w * 0.82f, 0.022f, 0.006f, y + ductH * 0.5f - 0.011f, ColorRGBA(0.70f, 0.74f, 0.78f, 1f), metallic = 0.8f)
    }

    private fun FrameScope.renderHvacAirUnit(item: PlacedItem, isSelected: Boolean) {
        val w = item.widthCm / 100f
        val d = item.depthCm / 100f
        val h = 0.65f
        val y = 2.15f
        val col = if (isSelected) WallSelectedColor else StainlessSteelColor
        val frameCol = CharcoalMetalColor
        val halfW = w * 0.5f
        val halfD = d * 0.5f

        // 1. Double-wall insulated AHU casing with beveled corners
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w, h, d, y, col, metallic = 0.78f, roughness = 0.25f)

        // 2. Corner structural aluminum extrusions with beveled posts
        val postSize = 0.045f
        for (dx in listOf(-halfW + postSize * 0.5f, halfW - postSize * 0.5f)) {
            for (dz in listOf(-halfD + postSize * 0.5f, halfD - postSize * 0.5f)) {
                renderLocalBeveledBox(item.position, item.rotationDegrees, dx, dz, postSize, h + 0.004f, postSize, y - 0.002f, frameCol, metallic = 0.85f, roughness = 0.2f)
            }
        }

        // 3. Inspection Access Doors with Quarter-Turn Locking Handles on Front (-Z)
        val frontZ = -halfD - 0.008f
        val doorW = w * 0.42f
        renderLocalBeveledBox(item.position, item.rotationDegrees, -w * 0.24f, frontZ, doorW, h * 0.82f, 0.018f, y + h * 0.09f, col, metallic = 0.78f, roughness = 0.22f)
        renderLocalBeveledBox(item.position, item.rotationDegrees, w * 0.24f, frontZ, doorW, h * 0.82f, 0.018f, y + h * 0.09f, col, metallic = 0.78f, roughness = 0.22f)

        // Quarter-turn locking handles (turned cylinder + beveled bar handle)
        for (hx in listOf(-w * 0.07f, w * 0.07f)) {
            renderLocalCylinder(item.position, item.rotationDegrees, hx, frontZ - 0.015f, 0.026f, 0.025f, y + h * 0.5f - 0.012f, frameCol, metallic = 0.85f)
            renderLocalBeveledBox(item.position, item.rotationDegrees, hx, frontZ - 0.025f, 0.022f, 0.085f, 0.018f, y + h * 0.5f - 0.042f, ChromeColor, metallic = 0.95f)
        }

        // 4. Differential Pressure Dial Manometer Gauges (smooth 28-gon cylinder + Torus bezel + indicator)
        val gaugeZ = frontZ - 0.012f
        val gaugeX = -w * 0.35f
        val gaugeY = y + h * 0.72f
        renderLocalCylinder(item.position, item.rotationDegrees, gaugeX, gaugeZ, 0.075f, 0.020f, gaugeY - 0.037f, ChromeColor, extraRotationDeg = 90f, metallic = 0.95f, segments = 28)
        renderLocalTorus(item.position, item.rotationDegrees, gaugeX, gaugeZ - 0.012f, 0.075f, 0.008f, gaugeY, ChromeColor, extraRotationDeg = 90f)
        renderLocalCylinder(item.position, item.rotationDegrees, gaugeX, gaugeZ - 0.010f, 0.055f, 0.005f, gaugeY - 0.027f, ColorRGBA(0.1f, 0.85f, 0.35f, 1f), extraRotationDeg = 90f, segments = 28)

        // 5. Supply duct connection spigot collar on right (+X)
        renderLocalBeveledBox(item.position, item.rotationDegrees, halfW + 0.04f, 0f, 0.08f, h * 0.65f, d * 0.65f, y + h * 0.18f, StainlessSteelColor, metallic = 0.85f)
    }

    private fun FrameScope.renderHvacFan(item: PlacedItem, isSelected: Boolean) {
        val col = if (isSelected) WallSelectedColor else ColorRGBA(0.78f, 0.82f, 0.86f, 1f)
        val w = item.widthCm / 100f
        val d = item.depthCm / 100f
        val dia = min(w, d)
        val y = 2.35f
        val fanH = 0.38f

        // 1. Aerodynamic cylindrical fan casing using smooth 28-gon cylinder
        renderLocalCylinder(item.position, item.rotationDegrees, 0f, 0f, dia, fanH, y, col, metallic = 0.80f, roughness = 0.25f, segments = 28)

        // 2. Connecting duct flanges at ends with Torus bead stiffening rings
        val flangeDia = dia + 0.06f
        renderLocalCylinder(item.position, item.rotationDegrees, 0f, 0f, flangeDia, 0.030f, y, ChromeColor, metallic = 0.90f, segments = 28)
        renderLocalTorus(item.position, item.rotationDegrees, 0f, 0f, flangeDia, 0.012f, y + 0.015f, ChromeColor)

        renderLocalCylinder(item.position, item.rotationDegrees, 0f, 0f, flangeDia, 0.030f, y + fanH - 0.030f, ChromeColor, metallic = 0.90f, segments = 28)
        renderLocalTorus(item.position, item.rotationDegrees, 0f, 0f, flangeDia, 0.012f, y + fanH - 0.015f, ChromeColor)

        // 3. Central aerodynamic spinner cone & hub
        renderLocalFrustum(item.position, item.rotationDegrees, 0f, 0f, dia * 0.32f, dia * 0.14f, 0.12f, y + fanH * 0.5f - 0.06f, CharcoalMetalColor, roughness = 0.3f)
        renderLocalSphere(item.position, item.rotationDegrees, 0f, 0f, dia * 0.16f, y + fanH * 0.5f, ChromeColor, metallic = 0.95f)

        // 4. External IP55 electric drive motor: cylindrical motor body + beveled terminal box
        val motorY = y + 0.08f
        val motorZ = dia * 0.44f
        renderLocalCylinder(item.position, item.rotationDegrees, 0f, motorZ, 0.18f, 0.22f, motorY, CharcoalMetalColor, metallic = 0.7f, roughness = 0.35f)
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, motorZ + 0.07f, 0.12f, 0.08f, 0.08f, motorY + 0.07f, CharcoalMetalColor, metallic = 0.7f)
        // Cable gland on terminal box
        renderLocalCylinder(item.position, item.rotationDegrees, 0.065f, motorZ + 0.07f, 0.024f, 0.025f, motorY + 0.09f, BrassAccentColor, extraRotationDeg = 90f, metallic = 0.95f)
    }

    private fun FrameScope.renderHvacAcIndoor(item: PlacedItem, isSelected: Boolean) {
        val col = if (isSelected) WallSelectedColor else CeramicWhiteColor
        val w = item.widthCm / 100f
        val d = item.depthCm / 100f
        val h = 0.32f
        val y = 2.35f
        val halfD = d * 0.5f

        // 1. Sleek Streamlined Indoor Unit Chassis with beveled curves (mounted on wall +Z)
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w, h, d, y, col, roughness = 0.18f)

        // Chrome trim accent line across front fascia
        renderLocalBox(item.position, item.rotationDegrees, 0f, -halfD - 0.003f, w * 0.95f, 0.005f, 0.004f, y + h * 0.35f, ChromeColor, metallic = 0.95f)

        // 2. Air Intake Top Slits with fine relief
        for (i in 0..4) {
            val sz = -d * 0.30f + i * (d * 0.14f)
            renderLocalBox(item.position, item.rotationDegrees, 0f, sz, w * 0.88f, 0.005f, 0.010f, y + h, CharcoalMetalColor)
        }

        // 3. Bottom Motorized Discharge Vane / Louver Flap with beveled aerofoil
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, -halfD + 0.025f, w * 0.92f, 0.025f, 0.045f, y + 0.02f, ColorRGBA(0.90f, 0.92f, 0.95f, 1f), roughness = 0.2f)

        // 4. Concealed Digital LED Temperature Display & Status Ring
        val dispX = w * 0.30f
        val dispY = y + h * 0.52f
        renderLocalBeveledBox(item.position, item.rotationDegrees, dispX, -halfD - 0.005f, 0.08f, 0.035f, 0.005f, dispY, ColorRGBA(0.15f, 0.85f, 1.0f, 1f))
        renderLocalTorus(item.position, item.rotationDegrees, dispX + 0.055f, -halfD - 0.006f, 0.016f, 0.003f, dispY + 0.018f, ColorRGBA(0.2f, 0.9f, 0.4f, 1f), extraRotationDeg = 90f)
    }

    private fun FrameScope.renderHvacAcOutdoor(item: PlacedItem, isSelected: Boolean) {
        val col = if (isSelected) WallSelectedColor else ColorRGBA(0.86f, 0.88f, 0.91f, 1f)
        val w = item.widthCm / 100f
        val d = item.depthCm / 100f
        val h = item.heightCm / 100f
        val halfW = w * 0.5f
        val halfD = d * 0.5f

        // 1. Anti-Vibration Neoprene Rubber Mount Feet (cylinders with Torus buffer)
        val footH = 0.05f
        for (fx in listOf(-halfW + 0.12f, halfW - 0.12f)) {
            for (fz in listOf(-halfD + 0.08f, halfD - 0.08f)) {
                renderLocalCylinder(item.position, item.rotationDegrees, fx, fz, 0.08f, footH, 0f, CharcoalMetalColor, roughness = 0.7f)
                renderLocalTorus(item.position, item.rotationDegrees, fx, fz, 0.08f, 0.012f, footH * 0.5f, CharcoalMetalColor)
            }
        }

        // 2. Heavy Galvanized Steel Casing with CAD Bevels
        val bodyH = h - footH
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w, bodyH, d, footH, col, metallic = 0.55f, roughness = 0.28f)

        // 3. Large Front High-Efficiency Axial Fan Grille & Cowling (smooth 28-gon cylinder + Torus)
        val fanDia = min(w * 0.62f, bodyH * 0.78f)
        val fanX = -halfW + fanDia * 0.5f + 0.06f
        val fanY = footH + bodyH * 0.5f
        val frontZ = -halfD - 0.008f
        renderLocalCylinder(item.position, item.rotationDegrees, fanX, frontZ, fanDia, 0.018f, fanY - fanDia * 0.5f, CharcoalMetalColor, extraRotationDeg = 90f, metallic = 0.75f, segments = 28)
        renderLocalTorus(item.position, item.rotationDegrees, fanX, frontZ - 0.010f, fanDia, 0.015f, fanY, CharcoalMetalColor, extraRotationDeg = 90f)

        // Central Aerodynamic Spinner Bullet
        renderLocalSphere(item.position, item.rotationDegrees, fanX, frontZ - 0.022f, fanDia * 0.16f, fanY, ChromeColor, metallic = 0.95f)

        // Concentric protective wire rings (torus)
        renderLocalTorus(item.position, item.rotationDegrees, fanX, frontZ - 0.014f, fanDia * 0.65f, 0.006f, fanY, ChromeColor, extraRotationDeg = 90f)
        renderLocalTorus(item.position, item.rotationDegrees, fanX, frontZ - 0.014f, fanDia * 0.35f, 0.006f, fanY, ChromeColor, extraRotationDeg = 90f)

        // 4. Right Side Heat Exchanger / Compressor Service Chamber with Brass Valve Ports
        val servX = halfW - (w - fanDia - 0.12f) * 0.5f
        renderLocalBeveledBox(item.position, item.rotationDegrees, servX, frontZ, 0.14f, 0.22f, 0.04f, footH + 0.12f, col, metallic = 0.6f)
        // Brass service valve ports (smooth cylinders)
        renderLocalCylinder(item.position, item.rotationDegrees, servX - 0.025f, frontZ - 0.025f, 0.025f, 0.045f, footH + 0.16f, BrassAccentColor, extraRotationDeg = 90f, metallic = 0.95f)
        renderLocalCylinder(item.position, item.rotationDegrees, servX + 0.025f, frontZ - 0.025f, 0.030f, 0.045f, footH + 0.16f, BrassAccentColor, extraRotationDeg = 90f, metallic = 0.95f)

        // Rear Heat Exchanger condenser coil louvers
        renderLocalBox(item.position, item.rotationDegrees, 0f, halfD + 0.005f, w * 0.88f, bodyH * 0.82f, 0.010f, footH + bodyH * 0.08f, ColorRGBA(0.40f, 0.44f, 0.50f, 1f), metallic = 0.85f)
    }

    // =========================================================================
    // 9. Professional IT, Datacenter & Office Equipment (Серверные, рабочие места)
    // =========================================================================

    private fun FrameScope.renderServerRack(item: PlacedItem, isSelected: Boolean) {
        val w = item.widthCm / 100f
        val d = item.depthCm / 100f
        val h = item.heightCm / 100f
        val rackColor = if (isSelected) WallSelectedColor else ColorRGBA(0.12f, 0.14f, 0.18f, 1f)
        val frameColor = ColorRGBA(0.08f, 0.09f, 0.11f, 1f)

        // 1. Heavy 19" 42U rack chassis cabinet with beveled corner posts
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w, h, d, 0f, rackColor, roughness = 0.28f, metallic = 0.65f)

        // 2. Tinted safety glass / perforated front door with beveled frame
        val doorZ = -d * 0.5f - 0.010f
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, doorZ, w * 0.88f, h * 0.94f, 0.018f, h * 0.03f, GlassColor, transparent = true, roughness = 0.08f)
        // Swing handle lock (cylinder + lever)
        renderLocalCylinder(item.position, item.rotationDegrees, w * 0.36f, doorZ - 0.015f, 0.028f, 0.020f, h * 0.5f - 0.010f, ChromeColor, metallic = 0.95f)
        renderLocalBeveledBox(item.position, item.rotationDegrees, w * 0.36f, doorZ - 0.022f, 0.020f, 0.14f, 0.015f, h * 0.5f - 0.07f, CharcoalMetalColor, metallic = 0.85f)

        // 3. Stack of individual 1U, 2U, and 4U Server Blade units
        val slotCount = 6
        val slotH = (h * 0.80f) / slotCount
        for (i in 0 until slotCount) {
            val sy = h * 0.10f + i * slotH
            // Server blade beveled faceplate
            renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, -d * 0.5f + 0.020f, w * 0.78f, slotH * 0.86f, 0.025f, sy, frameColor, roughness = 0.45f, metallic = 0.6f)
            // Hot-swap 2.5" drive caddy bays
            for (c in 0..3) {
                val cx = -w * 0.22f + c * (w * 0.10f)
                renderLocalBox(item.position, item.rotationDegrees, cx, -d * 0.5f + 0.006f, w * 0.085f, slotH * 0.35f, 0.005f, sy + slotH * 0.25f, CharcoalMetalColor)
            }
            // Green & Blue server status activity LEDs (smooth cylinders)
            renderLocalCylinder(item.position, item.rotationDegrees, -w * 0.32f, -d * 0.5f + 0.005f, 0.014f, 0.008f, sy + slotH * 0.45f, ColorRGBA(0.2f, 0.95f, 0.35f, 1f), extraRotationDeg = 90f)
            renderLocalCylinder(item.position, item.rotationDegrees, -w * 0.29f, -d * 0.5f + 0.005f, 0.014f, 0.008f, sy + slotH * 0.45f, ColorRGBA(0.2f, 0.65f, 1.0f, 1f), extraRotationDeg = 90f)
        }

        // 4. Roof high-volume exhaust fan grilles (dual 28-gon discs + Torus bezels)
        for (fx in listOf(-w * 0.22f, w * 0.22f)) {
            renderLocalCylinder(item.position, item.rotationDegrees, fx, 0f, 0.18f, 0.010f, h, CharcoalMetalColor, segments = 28)
            renderLocalTorus(item.position, item.rotationDegrees, fx, 0f, 0.18f, 0.010f, h + 0.005f, CharcoalMetalColor)
        }
    }

    private fun FrameScope.renderPcWorkstation(item: PlacedItem, isSelected: Boolean) {
        val w = item.widthCm / 100f
        val d = item.depthCm / 100f
        val h = 0.75f
        val topColor = if (isSelected) WallSelectedColor else ColorRGBA(0.88f, 0.85f, 0.80f, 1f)
        val legColor = ColorRGBA(0.15f, 0.17f, 0.20f, 1f)
        val halfW = w * 0.5f - 0.06f
        val halfD = d * 0.5f - 0.06f

        // 1. Solid Oak CAD Workstation Desk Top with Beveled Edges
        val topThick = 0.038f
        val legH = h - topThick
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w, topThick, d, legH, topColor, roughness = 0.28f)

        // 2. Tubular Steel Legs: smooth 24-gon cylinders with floor leveling glides
        for (dx in listOf(-halfW, halfW)) {
            for (dz in listOf(-halfD, halfD)) {
                renderLocalCylinder(item.position, item.rotationDegrees, dx, dz, 0.048f, legH, 0.015f, legColor, metallic = 0.82f, roughness = 0.25f, segments = 24)
                renderLocalCylinder(item.position, item.rotationDegrees, dx, dz, 0.065f, 0.015f, 0f, ChromeColor, metallic = 0.95f)
            }
        }

        // 3. Dual 27" Widescreen Monitors on Articulated Heavy Gas-Spring Mount
        val monW = min(0.58f, w * 0.45f)
        val monH = 0.36f
        val monY = h + 0.14f

        // Heavy desk clamp mount base (smooth cylinder)
        renderLocalCylinder(item.position, item.rotationDegrees, 0f, halfD * 0.6f, 0.12f, 0.020f, h, legColor, metallic = 0.85f)
        // Vertical column riser (smooth cylinder)
        renderLocalCylinder(item.position, item.rotationDegrees, 0f, halfD * 0.6f, 0.045f, 0.28f, h + 0.02f, ChromeColor, metallic = 0.95f)

        // Left monitor tilted at 12°
        val leftMonX = -monW * 0.52f
        renderLocalBeveledBox(item.position, item.rotationDegrees, leftMonX, halfD * 0.35f, monW, monH, 0.022f, monY, ColorRGBA(0.1f, 0.12f, 0.15f, 1f), extraRotationDeg = 12f)
        renderLocalBox(item.position, item.rotationDegrees, leftMonX, halfD * 0.35f - 0.012f, monW * 0.95f, monH * 0.92f, 0.005f, monY + 0.015f, ColorRGBA(0.18f, 0.36f, 0.58f, 1f), extraRotationDeg = 12f)

        // Right monitor tilted at -12°
        val rightMonX = monW * 0.52f
        renderLocalBeveledBox(item.position, item.rotationDegrees, rightMonX, halfD * 0.35f, monW, monH, 0.022f, monY, ColorRGBA(0.1f, 0.12f, 0.15f, 1f), extraRotationDeg = -12f)
        renderLocalBox(item.position, item.rotationDegrees, rightMonX, halfD * 0.35f - 0.012f, monW * 0.95f, monH * 0.92f, 0.005f, monY + 0.015f, ColorRGBA(0.18f, 0.36f, 0.58f, 1f), extraRotationDeg = -12f)

        // 4. Desktop Peripherals:
        // Spun aluminum cable grommet
        renderLocalCylinder(item.position, item.rotationDegrees, halfW - 0.10f, halfD - 0.08f, 0.065f, 0.006f, h, ChromeColor, metallic = 0.95f)
        renderLocalTorus(item.position, item.rotationDegrees, halfW - 0.10f, halfD - 0.08f, 0.065f, 0.006f, h + 0.003f, ChromeColor)
        // Mechanical keyboard with beveled chassis
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, -0.06f, 0.44f, 0.014f, 0.14f, h, CharcoalMetalColor, roughness = 0.4f)
        // Ergonomic mouse (smooth contoured pillow/loft)
        renderLocalPillow(item.position, item.rotationDegrees, 0.28f, -0.06f, 0.075f, 0.028f, 0.12f, h, CharcoalMetalColor, roughness = 0.35f)

        // 5. IT Workstation Tower Chassis under right side of desk
        val towerW = 0.21f
        val towerH = 0.46f
        val towerD = 0.48f
        val towerX = halfW - 0.08f
        renderLocalBeveledBox(item.position, item.rotationDegrees, towerX, 0f, towerW, towerH, towerD, 0.02f, ColorRGBA(0.12f, 0.13f, 0.16f, 1f), metallic = 0.65f)
        // Power button (sphere) & USB port
        renderLocalSphere(item.position, item.rotationDegrees, towerX, -towerD * 0.5f - 0.005f, 0.016f, towerH - 0.03f, ChromeColor, metallic = 0.95f)
    }

    private fun FrameScope.renderDispatchConsole(item: PlacedItem, isSelected: Boolean) {
        val w = item.widthCm / 100f
        val d = item.depthCm / 100f
        val h = 0.75f
        val consoleColor = if (isSelected) WallSelectedColor else ColorRGBA(0.20f, 0.22f, 0.26f, 1f)

        // 1. Contoured ergonomic console table top with beveled edges
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w, 0.045f, d, h - 0.045f, consoleColor, roughness = 0.25f, metallic = 0.4f)

        // 2. Heavy steel pedestal pedestals & rear acoustic modesty baffle
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, d * 0.30f, w * 0.90f, h * 0.90f, 0.08f, 0f, ColorRGBA(0.12f, 0.14f, 0.18f, 1f), metallic = 0.75f)

        // 3. Elevated heavy structural beam riser for multi-monitor array
        val riserH = 0.12f
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, d * 0.35f, w * 0.95f, riserH, 0.24f, h, ColorRGBA(0.15f, 0.17f, 0.20f, 1f), metallic = 0.8f)

        // 4. Bank of 3 curved dispatch monitors (angled at 18°, 0°, -18°)
        val monW = (w * 0.84f) / 3f
        val monH = 0.38f
        val monY = h + riserH + 0.02f

        // Left monitor angled at 18°
        renderLocalBeveledBox(item.position, item.rotationDegrees, -monW * 1.05f, d * 0.35f, monW, monH, 0.025f, monY, ColorRGBA(0.1f, 0.12f, 0.15f, 1f), extraRotationDeg = 18f)
        renderLocalBox(item.position, item.rotationDegrees, -monW * 1.05f, d * 0.35f - 0.012f, monW * 0.94f, monH * 0.92f, 0.005f, monY + 0.015f, ColorRGBA(0.08f, 0.45f, 0.65f, 1f), extraRotationDeg = 18f)

        // Center monitor
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, d * 0.35f, monW, monH, 0.025f, monY, ColorRGBA(0.1f, 0.12f, 0.15f, 1f))
        renderLocalBox(item.position, item.rotationDegrees, 0f, d * 0.35f - 0.012f, monW * 0.94f, monH * 0.92f, 0.005f, monY + 0.015f, ColorRGBA(0.12f, 0.55f, 0.35f, 1f))

        // Right monitor angled at -18°
        renderLocalBeveledBox(item.position, item.rotationDegrees, monW * 1.05f, d * 0.35f, monW, monH, 0.025f, monY, ColorRGBA(0.1f, 0.12f, 0.15f, 1f), extraRotationDeg = -18f)
        renderLocalBox(item.position, item.rotationDegrees, monW * 1.05f, d * 0.35f - 0.012f, monW * 0.94f, monH * 0.92f, 0.005f, monY + 0.015f, ColorRGBA(0.55f, 0.25f, 0.65f, 1f), extraRotationDeg = -18f)

        // 5. Communications goose-neck microphone (smooth cylinders + sphere head)
        renderLocalCylinder(item.position, item.rotationDegrees, -w * 0.38f, -d * 0.15f, 0.045f, 0.015f, h, CharcoalMetalColor, metallic = 0.85f)
        renderLocalCylinder(item.position, item.rotationDegrees, -w * 0.38f, -d * 0.15f, 0.012f, 0.24f, h + 0.015f, ChromeColor, extraRotationDeg = 25f, metallic = 0.95f)
        renderLocalSphere(item.position, item.rotationDegrees, -w * 0.38f + 0.05f, -d * 0.15f, 0.022f, h + 0.24f, CharcoalMetalColor)
    }

    private fun FrameScope.renderConferenceTable(item: PlacedItem, isSelected: Boolean) {
        val w = item.widthCm / 100f
        val d = item.depthCm / 100f
        val h = 0.75f
        val topColor = if (isSelected) WallSelectedColor else ColorRGBA(0.78f, 0.64f, 0.48f, 1f) // American Walnut

        // 1. Executive Boardroom Table Top with Elegant 45° CAD Bevels
        val topThick = 0.045f
        val legH = h - topThick
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w, topThick, d, legH, topColor, roughness = 0.26f)

        // 2. Center Flush Flip-Up Multimedia Connectivity Box with beveled lid
        val boxW = min(0.65f, w * 0.40f)
        val boxD = min(0.20f, d * 0.25f)
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, boxW, 0.008f, boxD, h, ColorRGBA(0.22f, 0.24f, 0.28f, 1f), metallic = 0.85f, roughness = 0.2f)

        // 3. Dual Architectonic Pedestal Steel Bases with beveled columns & floor plates
        val pedOffset = w * 0.28f
        val baseW = min(0.35f, w * 0.18f)
        val baseD = d * 0.58f
        for (px in listOf(-pedOffset, pedOffset)) {
            // Heavy flat floor base plate with beveled edges
            renderLocalBeveledBox(item.position, item.rotationDegrees, px, 0f, baseW * 1.35f, 0.025f, baseD, 0f, CharcoalMetalColor, metallic = 0.85f, roughness = 0.2f)
            // Central supporting column pillar
            renderLocalBeveledBox(item.position, item.rotationDegrees, px, 0f, baseW, legH - 0.025f, baseD * 0.70f, 0.025f, CharcoalMetalColor, metallic = 0.82f, roughness = 0.25f)
        }
    }

    private fun FrameScope.renderMetalRackStorage(item: PlacedItem, isSelected: Boolean) {
        val w = item.widthCm / 100f
        val d = item.depthCm / 100f
        val h = item.heightCm / 100f
        val steelColor = if (isSelected) WallSelectedColor else ColorRGBA(0.72f, 0.76f, 0.80f, 1f)
        val halfW = w * 0.5f - 0.025f
        val halfD = d * 0.5f - 0.025f

        // 1. 4 Heavy-duty slotted steel uprights with beveled profiles
        val postThick = 0.045f
        for (dx in listOf(-halfW, halfW)) {
            for (dz in listOf(-halfD, halfD)) {
                renderLocalBeveledBox(item.position, item.rotationDegrees, dx, dz, postThick, h, postThick, 0f, steelColor, metallic = 0.82f, roughness = 0.25f)
                // Floor anchor base plates (smooth cylinders)
                renderLocalCylinder(item.position, item.rotationDegrees, dx, dz, 0.08f, 0.008f, 0f, steelColor, metallic = 0.85f)
            }
        }

        // 2. 5 Heavy-duty corrugated steel shelves with beveled lips
        val shelfCount = 5
        val shelfH = 0.035f
        val dH = (h - 0.10f) / (shelfCount - 1)
        for (i in 0 until shelfCount) {
            val sy = 0.06f + i * dH
            renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w, shelfH, d, sy, steelColor, metallic = 0.75f, roughness = 0.3f)

            // Realistic stored cargo on middle shelves (i = 1, 2, 3)
            if (i == 1) {
                // Kraft corrugated carton boxes
                renderLocalBeveledBox(item.position, item.rotationDegrees, -w * 0.22f, 0f, w * 0.38f, dH * 0.65f, d * 0.75f, sy + shelfH, ColorRGBA(0.78f, 0.65f, 0.48f, 1f), roughness = 0.7f)
                renderLocalBeveledBox(item.position, item.rotationDegrees, w * 0.22f, 0f, w * 0.32f, dH * 0.55f, d * 0.70f, sy + shelfH, ColorRGBA(0.74f, 0.60f, 0.44f, 1f), roughness = 0.7f)
            } else if (i == 2) {
                // Industrial Euro plastic tote storage bins (cobalt blue)
                renderLocalBeveledBox(item.position, item.rotationDegrees, -w * 0.24f, 0f, w * 0.36f, dH * 0.50f, d * 0.72f, sy + shelfH, ColorRGBA(0.08f, 0.38f, 0.74f, 1f), roughness = 0.35f)
                renderLocalBeveledBox(item.position, item.rotationDegrees, w * 0.24f, 0f, w * 0.36f, dH * 0.50f, d * 0.72f, sy + shelfH, ColorRGBA(0.85f, 0.28f, 0.15f, 1f), roughness = 0.35f)
            }
        }
    }

    private fun FrameScope.renderReceptionDesk(item: PlacedItem, isSelected: Boolean) {
        val w = item.widthCm / 100f
        val d = item.depthCm / 100f
        val h = 1.15f
        val counterColor = if (isSelected) WallSelectedColor else ColorRGBA(0.96f, 0.96f, 0.97f, 1f)
        val marbleColor = ColorRGBA(0.95f, 0.95f, 0.96f, 1f)
        val woodAccent = ColorRGBA(0.82f, 0.68f, 0.52f, 1f)

        // 1. Polished Brass Floor Kick-Plate Plinth along front
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, -d * 0.45f, w, 0.08f, 0.09f, 0f, BrassAccentColor, metallic = 0.95f, roughness = 0.15f)

        // 2. High front privacy greeting screen with 3D horizontal acoustic flutings
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, -d * 0.45f, w, h - 0.08f, 0.08f, 0.08f, counterColor, roughness = 0.25f)
        for (g in 0..6) {
            val gy = 0.18f + g * 0.12f
            renderLocalBox(item.position, item.rotationDegrees, 0f, -d * 0.45f - 0.045f, w * 0.92f, 0.018f, 0.008f, gy, woodAccent)
        }

        // 3. Thick Polished Calacatta Quartz Transaction Counter Slab with Bevels
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, -d * 0.45f, w * 1.04f, 0.042f, 0.32f, h - 0.042f, marbleColor, roughness = 0.15f)

        // 4. Side enclosure return wing
        renderLocalBeveledBox(item.position, item.rotationDegrees, w * 0.46f, 0f, 0.08f, h, d * 0.90f, 0f, counterColor, roughness = 0.25f)

        // 5. Lower Receptionist Workstation Desk with Oak Top & Drawer Pedestal
        val deskH = 0.75f
        val deskD = d * 0.65f
        val deskW = w * 0.88f
        val deskZ = d * 0.12f
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, deskZ, deskW, 0.035f, deskD, deskH - 0.035f, woodAccent, roughness = 0.28f)

        // Cable grommet (smooth cylinder)
        renderLocalCylinder(item.position, item.rotationDegrees, -deskW * 0.35f, deskZ + deskD * 0.3f, 0.065f, 0.006f, deskH, ChromeColor, metallic = 0.95f)

        // Drawer pedestal under right side with beveled drawers & chrome handles
        val pedW = 0.38f
        val pedH = deskH - 0.08f
        val pedX = deskW * 0.5f - pedW * 0.5f - 0.04f
        renderLocalBeveledBox(item.position, item.rotationDegrees, pedX, deskZ, pedW, pedH, deskD * 0.85f, 0.04f, CoffeeTableShelfColor, roughness = 0.35f)
        for (i in 0..2) {
            val hy = 0.08f + i * (pedH / 3f) + (pedH / 6f)
            renderLocalCylinder(item.position, item.rotationDegrees, pedX, deskZ - deskD * 0.42f - 0.012f, 0.016f, pedW * 0.50f, hy, ChromeColor, extraRotationDeg = 90f, metallic = 0.95f)
        }
    }

    private fun FrameScope.renderIndustrialEquipment(item: PlacedItem, isSelected: Boolean) {
        val w = item.widthCm / 100f
        val d = item.depthCm / 100f
        val h = item.heightCm / 100f
        val bodyColor = if (isSelected) WallSelectedColor else ColorRGBA(0.24f, 0.27f, 0.32f, 1f)

        // 1. Heavy welded industrial steel cabinet with beveled machine bed
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, 0f, w, h, d, 0f, bodyColor, roughness = 0.28f, metallic = 0.65f)

        // 2. Operator HMI Touchscreen Control Panel on Articulated Arm
        val panelZ = -d * 0.5f - 0.015f
        val panelY = h * 0.68f
        renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, panelZ, w * 0.48f, 0.22f, 0.025f, panelY, ColorRGBA(0.1f, 0.12f, 0.15f, 1f), metallic = 0.8f)
        renderLocalBox(item.position, item.rotationDegrees, 0f, panelZ - 0.014f, w * 0.44f, 0.18f, 0.005f, panelY + 0.02f, ColorRGBA(0.12f, 0.68f, 0.95f, 1f))

        // 3. Prominent Emergency Stop (E-STOP) Safety Mushroom Button
        val estopX = w * 0.32f
        val estopY = panelY + 0.10f
        // Yellow safety collar ring (Torus)
        renderLocalTorus(item.position, item.rotationDegrees, estopX, panelZ - 0.010f, 0.045f, 0.008f, estopY, ColorRGBA(0.98f, 0.85f, 0.10f, 1f), extraRotationDeg = 90f)
        // Red mushroom button head (smooth cylinder + sphere dome)
        renderLocalCylinder(item.position, item.rotationDegrees, estopX, panelZ - 0.025f, 0.045f, 0.022f, estopY - 0.022f, ColorRGBA(0.92f, 0.15f, 0.15f, 1f), extraRotationDeg = 90f)
        renderLocalSphere(item.position, item.rotationDegrees, estopX, panelZ - 0.035f, 0.042f, estopY, ColorRGBA(0.92f, 0.15f, 0.15f, 1f))

        // 4. Tri-Color Andon Industrial Signal Tower (Green, Yellow, Red luminous cylinders)
        val towerX = -w * 0.42f
        val towerZ = -d * 0.38f
        renderLocalCylinder(item.position, item.rotationDegrees, towerX, towerZ, 0.025f, 0.20f, h, StainlessSteelColor, metallic = 0.95f)
        renderLocalCylinder(item.position, item.rotationDegrees, towerX, towerZ, 0.045f, 0.045f, h + 0.20f, ColorRGBA(0.15f, 0.90f, 0.30f, 1f), segments = 24)
        renderLocalCylinder(item.position, item.rotationDegrees, towerX, towerZ, 0.045f, 0.045f, h + 0.25f, ColorRGBA(0.98f, 0.80f, 0.15f, 1f), segments = 24)
        renderLocalCylinder(item.position, item.rotationDegrees, towerX, towerZ, 0.045f, 0.045f, h + 0.30f, ColorRGBA(0.95f, 0.15f, 0.15f, 1f), segments = 24)
        renderLocalSphere(item.position, item.rotationDegrees, towerX, towerZ, 0.048f, h + 0.35f, CharcoalMetalColor)

        // 5. Stamped cooling ventilation louver array
        for (i in 0..4) {
            val ly = h * 0.20f + i * 0.042f
            renderLocalBeveledBox(item.position, item.rotationDegrees, 0f, -d * 0.5f - 0.005f, w * 0.70f, 0.018f, 0.010f, ly, ColorRGBA(0.12f, 0.14f, 0.18f, 1f))
        }
    }
}

