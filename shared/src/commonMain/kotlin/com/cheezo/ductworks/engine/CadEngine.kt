package com.cheezo.ductworks.engine

import com.cheezo.ductworks.model.*

/**
 * Core CAD Engine supporting high-precision drawing, wall stretching, vertex dragging,
 * room translation, wall resizing, and planar topology detection.
 */
class CadEngine(
    initialDocument: PlanDocument = PlanDocument.createSamplePlan()
) {
    private val history = PlanHistoryManager(initialDocument)

    init {
        val planar = planarizeDocument(history.getCurrentDocument())
        if (planar != history.getCurrentDocument()) {
            history.reset(planar)
        }
    }

    val currentDocument: PlanDocument get() = history.getCurrentDocument()
    val canUndo: Boolean get() = history.canUndo
    val canRedo: Boolean get() = history.canRedo

    fun undo(): PlanDocument = history.undo()
    fun redo(): PlanDocument = history.redo()

    fun reset(doc: PlanDocument = PlanDocument()) {
        history.reset(planarizeDocument(doc))
    }

    // --- Transaction API for 60 FPS interactive dragging ---

    private var dragBaseline: PlanDocument? = null

    fun beginDrag() {
        dragBaseline = currentDocument
        history.beginTransaction()
    }

    /**
     * Synchronizes all openings (doors, windows) attached to walls that were moved, rotated, or resized.
     * Computes their new positions along the updated wall segments based on their relative parameter (ratio),
     * and updates their rotation to track changes in the wall's orientation angle.
     */
    fun syncOpeningsToMovedWalls(
        baseDoc: PlanDocument,
        newVertices: Map<String, WallVertex>,
        newWalls: Map<String, WallSegment> = baseDoc.walls
    ): Map<String, PlacedItem> {
        val updatedItems = baseDoc.items.toMutableMap()

        for ((itemId, item) in baseDoc.items) {
            val isOpening = item.itemType.isOpening || item.wallId != null
            if (!isOpening) continue

            // 1. Identify the host wall
            var hostWall = if (item.wallId != null) newWalls[item.wallId] else null
            if (hostWall == null) {
                val nearest = findNearestWall(item.position, 35f, baseDoc.walls, baseDoc.vertices)
                if (nearest != null) {
                    hostWall = newWalls[nearest.first.id]
                }
            }
            if (hostWall == null) continue

            // 2. Check if host wall endpoints moved
            val startBase = baseDoc.vertices[hostWall.startVertexId]?.position ?: continue
            val endBase = baseDoc.vertices[hostWall.endVertexId]?.position ?: continue
            val startNew = newVertices[hostWall.startVertexId]?.position ?: continue
            val endNew = newVertices[hostWall.endVertexId]?.position ?: continue

            val lineBase = LineSegment(startBase, endBase)
            val lineNew = LineSegment(startNew, endNew)

            // If wall endpoints didn't move at all, keep item unchanged
            if (startBase == startNew && endBase == endNew) continue

            // 3. Determine the relative parameter t on the wall
            val rawT = if (item.offsetAlongWallRatio in 0.001f..0.999f) {
                item.offsetAlongWallRatio
            } else {
                lineBase.parameterOf(item.position)
            }

            // Clamp t so opening stays within wall endpoints based on half its width
            val newWallLen = lineNew.length
            val halfW = item.widthCm * 0.5f
            val t = if (newWallLen > item.widthCm) {
                val minT = halfW / newWallLen
                val maxT = 1f - minT
                rawT.coerceIn(minT, maxT)
            } else {
                0.5f
            }

            // 4. Calculate new position along the new wall segment
            val newPosition = lineNew.pointAt(t)

            // 5. Update rotation to track wall angle change
            val angleBase = lineBase.angleDegrees
            val angleNew = lineNew.angleDegrees
            val deltaAngle = angleNew - angleBase
            var newRotation = (item.rotationDegrees + deltaAngle) % 360f
            if (newRotation < 0f) newRotation += 360f

            updatedItems[itemId] = item.copy(
                position = newPosition,
                rotationDegrees = newRotation,
                wallId = hostWall.id,
                offsetAlongWallRatio = t
            )
        }

        return updatedItems
    }

    fun dragVertex(vertexId: String, targetPos: Point2D): PlanDocument {
        val base = dragBaseline ?: currentDocument
        val newVertices = WallDraggingEngine.moveVertex(vertexId, targetPos, base.vertices)
        val newItems = syncOpeningsToMovedWalls(base, newVertices)
        val draftDoc = base.copy(vertices = newVertices, items = newItems)
        history.updateDraft(draftDoc)
        return draftDoc
    }

    fun dragWall(wallId: String, totalDelta: Point2D): PlanDocument {
        val base = dragBaseline ?: currentDocument
        val newVertices = WallDraggingEngine.dragWall(wallId, totalDelta, base.vertices, base.walls)
        val newItems = syncOpeningsToMovedWalls(base, newVertices)
        val draftDoc = base.copy(vertices = newVertices, items = newItems)
        history.updateDraft(draftDoc)
        return draftDoc
    }

    fun dragRoom(roomId: String, totalDelta: Point2D): PlanDocument {
        val base = dragBaseline ?: currentDocument
        val room = base.rooms.firstOrNull { it.id == roomId } ?: return currentDocument
        val newVertices = WallDraggingEngine.translateRoom(room, totalDelta, base.vertices)

        // Room geometry in baseline
        val roomPoly = room.getPolygonPoints(base.vertices)
        val roomVertexIds = room.vertexIds.toSet()

        // Room perimeter walls
        val roomWalls = base.walls.values.filter { wall ->
            wall.startVertexId in roomVertexIds && wall.endVertexId in roomVertexIds
        }
        val roomWallIds = roomWalls.map { it.id }.toSet()

        // 1. Identify which items belong to this room (either inside polygon or hosted on room perimeter walls)
        val newItems = base.items.mapValues { (_, item) ->
            val isOnRoomWall = item.wallId in roomWallIds || (item.isOpening && roomWalls.any { wall ->
                val line = wall.toLineSegment(base.vertices) ?: return@any false
                line.distanceTo(item.position) <= wall.thicknessCm * 1.5f
            })
            val isInsideRoom = PolygonMath.isPointInside(item.position, roomPoly) ||
                    (!item.isOpening && item.getBoundingCorners().any { PolygonMath.isPointInside(it, roomPoly) })

            if (isOnRoomWall || isInsideRoom) {
                // Ensure room openings maintain or establish wallId
                val attachedWallId = if (item.isOpening && (item.wallId == null || item.wallId in roomWallIds)) {
                    item.wallId ?: roomWalls.minByOrNull { wall ->
                        wall.toLineSegment(base.vertices)?.distanceTo(item.position) ?: Float.MAX_VALUE
                    }?.id
                } else item.wallId

                item.copy(
                    position = item.position + totalDelta,
                    wallId = attachedWallId
                )
            } else {
                item
            }
        }.toMutableMap()

        // 2. Also run syncOpeningsToMovedWalls for any openings on walls connected to moved vertices
        // (e.g. walls of adjoining rooms whose shared vertices shifted)
        val syncedItems = syncOpeningsToMovedWalls(base, newVertices)
        for ((itemId, syncedItem) in syncedItems) {
            val baseItem = base.items[itemId] ?: continue
            val wasMovedByRoom = newItems[itemId]?.position != baseItem.position
            if (!wasMovedByRoom && syncedItem.position != baseItem.position) {
                newItems[itemId] = syncedItem
            }
        }

        val draftDoc = base.copy(vertices = newVertices, items = newItems)
        history.updateDraft(draftDoc)
        return draftDoc
    }

    fun commitDrag(): PlanDocument {
        dragBaseline = null
        activeItemAlignGuides = emptyList()
        val doc = currentDocument
        val newVertices = doc.vertices.toMutableMap()
        val newWalls = doc.walls.toMutableMap()
        val newItems = doc.items.toMutableMap()
        autoPlanarizeJunctions(newVertices, newWalls, newItems)
        val detectedRooms = RoomDetector.detectRooms(newVertices, newWalls, doc.rooms)
        val finalDoc = doc.copy(
            vertices = newVertices,
            walls = newWalls,
            items = newItems,
            rooms = detectedRooms
        )
        history.commitTransaction(finalDoc)
        return finalDoc
    }

    fun cancelDrag(): PlanDocument {
        dragBaseline = null
        activeItemAlignGuides = emptyList()
        return history.cancelTransaction()
    }

    // --- Discrete CAD Operations ---

    fun mergeCoincidentVertices(
        vertices: MutableMap<String, WallVertex>,
        walls: MutableMap<String, WallSegment>,
        items: MutableMap<String, PlacedItem> = mutableMapOf(),
        toleranceCm: Float = 15f
    ): Boolean {
        var mergedAny = false
        val vList = vertices.values.toList()
        val replaceMap = mutableMapOf<String, String>()

        for (i in vList.indices) {
            val v1 = vList[i]
            if (v1.id in replaceMap) continue
            for (j in i + 1 until vList.size) {
                val v2 = vList[j]
                if (v2.id in replaceMap) continue
                if (v1.position.distanceTo(v2.position) <= toleranceCm) {
                    replaceMap[v2.id] = v1.id
                    mergedAny = true
                }
            }
        }

        if (mergedAny) {
            val wallIds = walls.keys.toList()
            for (wId in wallIds) {
                val wall = walls[wId] ?: continue
                var newStart = wall.startVertexId
                while (newStart in replaceMap) {
                    newStart = replaceMap[newStart]!!
                }
                var newEnd = wall.endVertexId
                while (newEnd in replaceMap) {
                    newEnd = replaceMap[newEnd]!!
                }

                if (newStart == newEnd) {
                    walls.remove(wId)
                    for ((itemId, item) in items) {
                        if (item.wallId == wId) {
                            items[itemId] = item.copy(wallId = null)
                        }
                    }
                } else if (newStart != wall.startVertexId || newEnd != wall.endVertexId) {
                    walls[wId] = wall.copy(startVertexId = newStart, endVertexId = newEnd)
                }
            }

            for (vId in replaceMap.keys) {
                vertices.remove(vId)
            }
        }

        return mergedAny
    }

    fun autoPlanarizeJunctions(
        vertices: MutableMap<String, WallVertex>,
        walls: MutableMap<String, WallSegment>,
        items: MutableMap<String, PlacedItem> = mutableMapOf(),
        toleranceCm: Float = 20f
    ): Boolean {
        var anyChanged = mergeCoincidentVertices(vertices, walls, items, toleranceCm * 0.75f)
        var iteration = 0

        while (iteration++ < 30) {
            var splitInThisPass = false
            val currentWalls = walls.values.toList()

            for (wall in currentWalls) {
                if (wall.id !in walls) continue

                val pStart = vertices[wall.startVertexId]?.position ?: continue
                val pEnd = vertices[wall.endVertexId]?.position ?: continue
                val line = LineSegment(pStart, pEnd)
                val wallLen = line.length
                if (wallLen < 5f) continue

                val verticesOnWall = mutableListOf<Pair<WallVertex, Float>>()
                val effectiveTol = maxOf(toleranceCm, wall.thicknessCm * 0.75f)

                for (v in vertices.values) {
                    if (v.id == wall.startVertexId || v.id == wall.endVertexId) continue

                    val dist = line.distanceTo(v.position)
                    val t = line.parameterOf(v.position)

                    val dStart = v.position.distanceTo(pStart)
                    val dEnd = v.position.distanceTo(pEnd)

                    if (dist <= effectiveTol && dStart > 5f && dEnd > 5f && t in 0.01f..0.99f) {
                        verticesOnWall.add(v to t)
                    }
                }

                if (verticesOnWall.isNotEmpty()) {
                    verticesOnWall.sortBy { it.second }

                    for ((v, _) in verticesOnWall) {
                        val snapped = line.closestPointTo(v.position)
                        vertices[v.id] = v.copy(position = snapped)
                    }

                    walls.remove(wall.id)
                    val newSegments = mutableListOf<WallSegment>()
                    var prevVertexId = wall.startVertexId

                    for ((v, _) in verticesOnWall) {
                        val seg = WallSegment(
                            startVertexId = prevVertexId,
                            endVertexId = v.id,
                            thicknessCm = wall.thicknessCm,
                            heightCm = wall.heightCm,
                            wallType = wall.wallType
                        )
                        walls[seg.id] = seg
                        newSegments.add(seg)
                        prevVertexId = v.id
                    }

                    val lastSeg = WallSegment(
                        startVertexId = prevVertexId,
                        endVertexId = wall.endVertexId,
                        thicknessCm = wall.thicknessCm,
                        heightCm = wall.heightCm,
                        wallType = wall.wallType
                    )
                    walls[lastSeg.id] = lastSeg
                    newSegments.add(lastSeg)

                    for ((itemId, item) in items) {
                        if (item.wallId == wall.id) {
                            var bestSeg: WallSegment? = null
                            var bestDist = Float.MAX_VALUE
                            for (seg in newSegments) {
                                val sLine = seg.toLineSegment(vertices) ?: continue
                                val d = sLine.distanceTo(item.position)
                                if (d < bestDist) {
                                    bestDist = d
                                    bestSeg = seg
                                }
                            }
                            if (bestSeg != null) {
                                val bLine = bestSeg.toLineSegment(vertices)
                                val ratio = bLine?.parameterOf(item.position) ?: 0.5f
                                items[itemId] = item.copy(
                                    wallId = bestSeg.id,
                                    offsetAlongWallRatio = ratio.coerceIn(0.05f, 0.95f)
                                )
                            }
                        }
                    }

                    splitInThisPass = true
                    anyChanged = true
                    break
                }
            }

            if (!splitInThisPass) break
        }

        return anyChanged
    }

    fun planarizeDocument(doc: PlanDocument): PlanDocument {
        val newVertices = doc.vertices.toMutableMap()
        val newWalls = doc.walls.toMutableMap()
        val newItems = doc.items.toMutableMap()
        val changed = autoPlanarizeJunctions(newVertices, newWalls, newItems)
        val detectedRooms = RoomDetector.detectRooms(newVertices, newWalls, doc.rooms)
        if (changed || detectedRooms.size != doc.rooms.size) {
            return doc.copy(
                vertices = newVertices,
                walls = newWalls,
                items = newItems,
                rooms = detectedRooms
            )
        }
        return doc
    }

    fun ensurePlanarGraph(): PlanDocument {
        val doc = currentDocument
        val planar = planarizeDocument(doc)
        if (planar != doc) {
            history.pushState(planar)
        }
        return planar
    }

    fun addWall(
        pStart: Point2D,
        pEnd: Point2D,
        thicknessCm: Float = 20f,
        wallType: WallType = WallType.INTERIOR,
        existingStartVertexId: String? = null,
        existingEndVertexId: String? = null,
        toleranceCm: Float = 20f
    ): PlanDocument {
        if (pStart.distanceTo(pEnd) < 5f) return currentDocument

        val doc = currentDocument
        val newVertices = doc.vertices.toMutableMap()
        val newWalls = doc.walls.toMutableMap()
        val newItems = doc.items.toMutableMap()
        val effectiveTol = maxOf(toleranceCm, thicknessCm * 0.75f)

        // Start vertex
        val startId = existingStartVertexId ?: run {
            val nearby = findNearestVertex(pStart, effectiveTol, newVertices)
            if (nearby != null) nearby.id
            else {
                val wallNear = findNearestWall(pStart, effectiveTol, newWalls, newVertices)
                val pos = wallNear?.second ?: pStart
                val v = WallVertex(position = pos)
                newVertices[v.id] = v
                v.id
            }
        }

        // End vertex
        val endId = existingEndVertexId ?: run {
            val nearby = findNearestVertex(pEnd, effectiveTol, newVertices)
            if (nearby != null && nearby.id != startId) nearby.id
            else {
                val wallNear = findNearestWall(pEnd, effectiveTol, newWalls, newVertices)
                val pos = if (wallNear != null && (wallNear.first.startVertexId != startId && wallNear.first.endVertexId != startId)) wallNear.second else pEnd
                val v = WallVertex(position = pos)
                newVertices[v.id] = v
                v.id
            }
        }

        if (startId == endId) return currentDocument

        val exists = newWalls.values.any {
            (it.startVertexId == startId && it.endVertexId == endId) ||
            (it.startVertexId == endId && it.endVertexId == startId)
        }
        if (!exists) {
            val wall = WallSegment(
                startVertexId = startId,
                endVertexId = endId,
                thicknessCm = thicknessCm,
                wallType = wallType
            )
            newWalls[wall.id] = wall
        }

        autoPlanarizeJunctions(newVertices, newWalls, newItems, effectiveTol)

        val detectedRooms = RoomDetector.detectRooms(newVertices, newWalls, doc.rooms)
        val updatedDoc = doc.copy(
            vertices = newVertices,
            walls = newWalls,
            rooms = detectedRooms,
            items = newItems
        )
        history.pushState(updatedDoc)
        return updatedDoc
    }

    fun splitWall(wallId: String, splitPoint: Point2D): PlanDocument {
        val doc = currentDocument
        val wall = doc.walls[wallId] ?: return doc

        val newVertices = doc.vertices.toMutableMap()
        val newWalls = doc.walls.toMutableMap()

        val newVertex = WallVertex(position = splitPoint)
        newVertices[newVertex.id] = newVertex

        newWalls.remove(wallId)
        val wall1 = WallSegment(
            startVertexId = wall.startVertexId,
            endVertexId = newVertex.id,
            thicknessCm = wall.thicknessCm,
            heightCm = wall.heightCm,
            wallType = wall.wallType
        )
        val wall2 = WallSegment(
            startVertexId = newVertex.id,
            endVertexId = wall.endVertexId,
            thicknessCm = wall.thicknessCm,
            heightCm = wall.heightCm,
            wallType = wall.wallType
        )
        newWalls[wall1.id] = wall1
        newWalls[wall2.id] = wall2

        val line1 = wall1.toLineSegment(newVertices)
        val line2 = wall2.toLineSegment(newVertices)
        val newItems = doc.items.mapValues { (_, item) ->
            if (item.wallId == wallId) {
                val d1 = line1?.distanceTo(item.position) ?: Float.MAX_VALUE
                val d2 = line2?.distanceTo(item.position) ?: Float.MAX_VALUE
                if (d1 <= d2 && line1 != null) {
                    val ratio = line1.parameterOf(item.position)
                    item.copy(wallId = wall1.id, offsetAlongWallRatio = ratio)
                } else if (line2 != null) {
                    val ratio = line2.parameterOf(item.position)
                    item.copy(wallId = wall2.id, offsetAlongWallRatio = ratio)
                } else {
                    item
                }
            } else item
        }

        val detectedRooms = RoomDetector.detectRooms(newVertices, newWalls, doc.rooms)
        val updatedDoc = doc.copy(
            vertices = newVertices,
            walls = newWalls,
            rooms = detectedRooms,
            items = newItems
        )
        history.pushState(updatedDoc)
        return updatedDoc
    }

    fun deleteWall(wallId: String): PlanDocument {
        val doc = currentDocument
        if (wallId !in doc.walls) return doc

        val newWalls = doc.walls.toMutableMap()
        val removedWall = newWalls.remove(wallId) ?: return doc

        val newVertices = doc.vertices.toMutableMap()
        val startConnected = newWalls.values.any { it.hasVertex(removedWall.startVertexId) }
        val endConnected = newWalls.values.any { it.hasVertex(removedWall.endVertexId) }

        if (!startConnected) newVertices.remove(removedWall.startVertexId)
        if (!endConnected) newVertices.remove(removedWall.endVertexId)

        val newItems = doc.items.mapValues { (_, item) ->
            if (item.wallId == wallId) item.copy(wallId = null) else item
        }

        val detectedRooms = RoomDetector.detectRooms(newVertices, newWalls, doc.rooms)
        val updatedDoc = doc.copy(
            vertices = newVertices,
            walls = newWalls,
            rooms = detectedRooms,
            items = newItems
        )
        history.pushState(updatedDoc)
        return updatedDoc
    }

    fun deleteVertex(vertexId: String): PlanDocument {
        val doc = currentDocument
        if (vertexId !in doc.vertices) return doc

        val newVertices = doc.vertices.toMutableMap()
        newVertices.remove(vertexId)

        val removedWallIds = doc.walls.filterValues { it.hasVertex(vertexId) }.keys
        val newWalls = doc.walls.filterValues { !it.hasVertex(vertexId) }.toMutableMap()
        val activeVertexIds = newWalls.values.flatMap { listOf(it.startVertexId, it.endVertexId) }.toSet()
        newVertices.keys.retainAll(activeVertexIds)

        val newItems = doc.items.mapValues { (_, item) ->
            if (item.wallId in removedWallIds) item.copy(wallId = null) else item
        }

        val detectedRooms = RoomDetector.detectRooms(newVertices, newWalls, doc.rooms)
        val updatedDoc = doc.copy(
            vertices = newVertices,
            walls = newWalls,
            rooms = detectedRooms,
            items = newItems
        )
        history.pushState(updatedDoc)
        return updatedDoc
    }

    fun resizeWallLength(wallId: String, newLengthCm: Float, anchorVertexId: String? = null): PlanDocument {
        val doc = currentDocument
        val newVertices = WallDraggingEngine.resizeWallLength(
            wallId,
            newLengthCm,
            anchorVertexId,
            doc.vertices,
            doc.walls
        )
        val newItems = syncOpeningsToMovedWalls(doc, newVertices)
        val detectedRooms = RoomDetector.detectRooms(newVertices, doc.walls, doc.rooms)
        val updatedDoc = doc.copy(
            vertices = newVertices,
            items = newItems,
            rooms = detectedRooms
        )
        history.pushState(updatedDoc)
        return updatedDoc
    }

    fun addRectangularRoom(
        center: Point2D,
        widthCm: Float = 400f,
        heightCm: Float = 300f,
        thicknessCm: Float = 20f,
        wallType: WallType = WallType.INTERIOR,
        name: String = "Комната"
    ): PlanDocument {
        val doc = currentDocument
        val halfW = widthCm * 0.5f
        val halfH = heightCm * 0.5f

        val v1 = WallVertex(position = Point2D(center.x - halfW, center.y - halfH))
        val v2 = WallVertex(position = Point2D(center.x + halfW, center.y - halfH))
        val v3 = WallVertex(position = Point2D(center.x + halfW, center.y + halfH))
        val v4 = WallVertex(position = Point2D(center.x - halfW, center.y + halfH))

        val w1 = WallSegment(startVertexId = v1.id, endVertexId = v2.id, thicknessCm = thicknessCm, wallType = wallType)
        val w2 = WallSegment(startVertexId = v2.id, endVertexId = v3.id, thicknessCm = thicknessCm, wallType = wallType)
        val w3 = WallSegment(startVertexId = v3.id, endVertexId = v4.id, thicknessCm = thicknessCm, wallType = wallType)
        val w4 = WallSegment(startVertexId = v4.id, endVertexId = v1.id, thicknessCm = thicknessCm, wallType = wallType)

        val newVertices = (doc.vertices + mapOf(v1.id to v1, v2.id to v2, v3.id to v3, v4.id to v4)).toMutableMap()
        val newWalls = (doc.walls + mapOf(w1.id to w1, w2.id to w2, w3.id to w3, w4.id to w4)).toMutableMap()
        val newItems = doc.items.toMutableMap()

        autoPlanarizeJunctions(newVertices, newWalls, newItems)

        val detectedRooms = RoomDetector.detectRooms(newVertices, newWalls, doc.rooms)
        val updatedRooms = detectedRooms.map { room ->
            if (room.vertexIds.toSet().containsAll(setOf(v1.id, v2.id, v3.id, v4.id))) {
                room.copy(name = name)
            } else room
        }

        val updatedDoc = doc.copy(
            vertices = newVertices,
            walls = newWalls,
            rooms = updatedRooms,
            items = newItems
        )
        history.pushState(updatedDoc)
        return updatedDoc
    }

    fun addLShapedRoom(center: Point2D): PlanDocument {
        val doc = currentDocument
        // L-shaped polygon vertices
        val pts = listOf(
            Point2D(-250f, -200f),
            Point2D(250f, -200f),
            Point2D(250f, 0f),
            Point2D(50f, 0f),
            Point2D(50f, 200f),
            Point2D(-250f, 200f)
        ).map { it + center }

        val createdVertices = pts.map { WallVertex(position = it) }
        val newVertices = doc.vertices.toMutableMap()
        for (v in createdVertices) newVertices[v.id] = v

        val newWalls = doc.walls.toMutableMap()
        for (i in createdVertices.indices) {
            val vStart = createdVertices[i]
            val vEnd = createdVertices[(i + 1) % createdVertices.size]
            val w = WallSegment(startVertexId = vStart.id, endVertexId = vEnd.id, thicknessCm = 20f, wallType = WallType.INTERIOR)
            newWalls[w.id] = w
        }

        val newItems = doc.items.toMutableMap()
        autoPlanarizeJunctions(newVertices, newWalls, newItems)

        val detectedRooms = RoomDetector.detectRooms(newVertices, newWalls, doc.rooms)
        val updatedDoc = doc.copy(
            vertices = newVertices,
            walls = newWalls,
            rooms = detectedRooms,
            items = newItems
        )
        history.pushState(updatedDoc)
        return updatedDoc
    }

    fun updateWall(
        wallId: String,
        thicknessCm: Float? = null,
        wallType: WallType? = null,
        heightCm: Float? = null
    ): PlanDocument {
        val doc = currentDocument
        val wall = doc.walls[wallId] ?: return doc

        val updatedWall = wall.copy(
            thicknessCm = thicknessCm ?: wall.thicknessCm,
            wallType = wallType ?: wall.wallType,
            heightCm = heightCm ?: wall.heightCm
        )
        val newWalls = doc.walls + (wallId to updatedWall)
        val updatedDoc = doc.copy(walls = newWalls)
        history.pushState(updatedDoc)
        return updatedDoc
    }

    fun updateAllWallsHeight(heightCm: Float): PlanDocument {
        val doc = currentDocument
        val newWalls = doc.walls.mapValues { it.value.copy(heightCm = heightCm) }
        val updatedDoc = doc.copy(walls = newWalls)
        history.pushState(updatedDoc)
        return updatedDoc
    }

    fun updateRoom(
        roomId: String,
        name: String? = null,
        roomType: RoomType? = null,
        ceilingHeightCm: Float? = null
    ): PlanDocument {
        val doc = currentDocument
        val updatedRooms = doc.rooms.map { room ->
            if (room.id == roomId) {
                room.copy(
                    name = name ?: room.name,
                    roomType = roomType ?: room.roomType,
                    ceilingHeightCm = ceilingHeightCm ?: room.ceilingHeightCm
                )
            } else room
        }
        val updatedDoc = doc.copy(rooms = updatedRooms)
        history.pushState(updatedDoc)
        return updatedDoc
    }

    fun findNearestVertex(
        point: Point2D,
        maxDistance: Float,
        vertices: Map<String, WallVertex> = currentDocument.vertices
    ): WallVertex? {
        var closest: WallVertex? = null
        var minDist = maxDistance
        for (v in vertices.values) {
            val d = point.distanceTo(v.position)
            if (d < minDist) {
                minDist = d
                closest = v
            }
        }
        return closest
    }

    fun findNearestWall(
        point: Point2D,
        maxDistance: Float,
        walls: Map<String, WallSegment> = currentDocument.walls,
        vertices: Map<String, WallVertex> = currentDocument.vertices
    ): Pair<WallSegment, Point2D>? {
        var closestWall: WallSegment? = null
        var closestPoint: Point2D? = null
        var minDist = maxDistance

        for (w in walls.values) {
            val line = w.toLineSegment(vertices) ?: continue
            val proj = line.closestPointTo(point)
            val d = point.distanceTo(proj)
            if (d < minDist) {
                minDist = d
                closestWall = w
                closestPoint = proj
            }
        }

        return if (closestWall != null && closestPoint != null) {
            Pair(closestWall, closestPoint)
        } else null
    }

    fun findRoomAt(
        point: Point2D,
        rooms: List<Room> = currentDocument.rooms,
        vertices: Map<String, WallVertex> = currentDocument.vertices
    ): Room? {
        for (room in rooms) {
            val poly = room.getPolygonPoints(vertices)
            if (PolygonMath.isPointInside(point, poly)) {
                return room
            }
        }
        return null
    }

    // --- PlacedItem (Furniture, Doors, Windows, HVAC) Operations ---

    fun addPlacedItem(item: PlacedItem): PlanDocument {
        val doc = currentDocument
        val resolvedItem = if (item.isOpening) {
            val nearestWall = findNearestWall(item.position, 60f, doc.walls, doc.vertices)
            if (nearestWall != null) {
                val wall = nearestWall.first
                val proj = nearestWall.second
                val line = wall.toLineSegment(doc.vertices)
                val angle = line?.angleDegrees ?: 0f
                val ratio = line?.parameterOf(proj) ?: 0.5f
                item.copy(
                    position = proj,
                    wallId = wall.id,
                    rotationDegrees = angle,
                    offsetAlongWallRatio = ratio
                )
            } else {
                item
            }
        } else {
            item
        }
        val newItems = doc.items + (resolvedItem.id to resolvedItem)
        val updated = doc.copy(items = newItems)
        history.pushState(updated)
        return updated
    }

    var activeItemAlignGuides: List<com.cheezo.ductworks.engine.cad.InteriorPlanningEngine.SmartAlignGuide> = emptyList()
        private set

    fun dragPlacedItem(itemId: String, totalDelta: Point2D): PlanDocument {
        val base = dragBaseline ?: currentDocument
        val item = base.items[itemId] ?: return currentDocument
        val rawTargetPos = item.position + totalDelta

        val isOpening = item.itemType.isOpening

        val updatedItem = if (isOpening) {
            activeItemAlignGuides = emptyList()
            val nearestWall = findNearestWall(rawTargetPos, 50f, base.walls, base.vertices)
            if (nearestWall != null) {
                val wall = nearestWall.first
                val proj = nearestWall.second
                val line = wall.toLineSegment(base.vertices)
                val angle = line?.angleDegrees ?: 0f
                val wallLen = line?.length ?: 1f
                val halfW = item.widthCm * 0.5f
                val rawRatio = line?.parameterOf(proj) ?: 0.5f
                val ratio = if (wallLen > item.widthCm) {
                    val minT = halfW / wallLen
                    val maxT = 1f - minT
                    rawRatio.coerceIn(minT, maxT)
                } else 0.5f
                val finalPos = line?.pointAt(ratio) ?: proj
                item.copy(
                    position = finalPos,
                    wallId = wall.id,
                    rotationDegrees = angle,
                    offsetAlongWallRatio = ratio
                )
            } else {
                item.copy(position = rawTargetPos, wallId = null)
            }
        } else {
            val wallSnap = com.cheezo.ductworks.engine.cad.InteriorPlanningEngine.resolveWallSnap(
                item = item,
                rawTargetPos = rawTargetPos,
                walls = base.walls.values,
                vertices = base.vertices
            )
            val otherItems = base.items.values.filter { it.id != itemId && !it.isOpening }
            val itemSnap = com.cheezo.ductworks.engine.cad.InteriorPlanningEngine.resolveItemToItemSnap(
                movingItem = item,
                rawTargetPos = wallSnap?.snappedPosition ?: rawTargetPos,
                otherItems = otherItems
            )
            activeItemAlignGuides = itemSnap?.guides ?: emptyList()

            val finalPos = itemSnap?.snappedPosition ?: wallSnap?.snappedPosition ?: rawTargetPos
            val finalRot = wallSnap?.snappedRotationDegrees ?: item.rotationDegrees

            item.copy(
                position = finalPos,
                rotationDegrees = finalRot
            )
        }

        val newItems = base.items + (itemId to updatedItem)
        val draft = base.copy(items = newItems)
        history.updateDraft(draft)
        return draft
    }

    fun rotatePlacedItemLive(itemId: String, angleDegrees: Float): PlanDocument {
        val base = history.baselineState ?: currentDocument
        val item = base.items[itemId] ?: return currentDocument
        val updatedItem = item.copy(rotationDegrees = angleDegrees)
        val newItems = base.items + (itemId to updatedItem)
        val draft = base.copy(items = newItems)
        history.updateDraft(draft)
        return draft
    }

    fun updatePlacedItem(
        itemId: String,
        position: Point2D? = null,
        rotationDegrees: Float? = null,
        widthCm: Float? = null,
        depthCm: Float? = null,
        heightCm: Float? = null,
        elevationCm: Float? = null,
        flipH: Boolean? = null,
        flipV: Boolean? = null,
        zIndex: Int? = null
    ): PlanDocument {
        val doc = currentDocument
        val item = doc.items[itemId] ?: return doc
        val updated = item.copy(
            position = position ?: item.position,
            rotationDegrees = rotationDegrees ?: item.rotationDegrees,
            widthCm = widthCm ?: item.widthCm,
            depthCm = depthCm ?: item.depthCm,
            heightCm = heightCm ?: item.heightCm,
            elevationCm = elevationCm ?: item.elevationCm,
            flipHorizontal = flipH ?: item.flipHorizontal,
            flipVertical = flipV ?: item.flipVertical,
            zIndex = zIndex ?: item.zIndex
        )
        val newItems = doc.items + (itemId to updated)
        val updatedDoc = doc.copy(items = newItems)
        history.pushState(updatedDoc)
        return updatedDoc
    }

    fun rotatePlacedItem(itemId: String, degreesDelta: Float): PlanDocument {
        val doc = currentDocument
        val item = doc.items[itemId] ?: return doc
        var newAngle = (item.rotationDegrees + degreesDelta) % 360f
        if (newAngle < 0) newAngle += 360f
        return updatePlacedItem(itemId, rotationDegrees = newAngle)
    }

    fun duplicatePlacedItem(itemId: String): PlanDocument {
        val doc = currentDocument
        val item = doc.items[itemId] ?: return doc
        val duplicated = item.copy(
            id = WallVertex.generateId("item"),
            position = item.position + Point2D(30f, 30f)
        )
        return addPlacedItem(duplicated)
    }

    fun deletePlacedItem(itemId: String): PlanDocument {
        val doc = currentDocument
        if (itemId !in doc.items) return doc
        val newItems = doc.items - itemId
        val updated = doc.copy(items = newItems)
        history.pushState(updated)
        return updated
    }

    fun findNearestItem(point: Point2D): PlacedItem? {
        val doc = currentDocument
        for (item in doc.items.values.reversed()) {
            if (item.containsPoint(point)) return item
        }
        var closest: PlacedItem? = null
        var minDist = 25f
        for (item in doc.items.values) {
            val d = point.distanceTo(item.position)
            if (d < minDist) {
                minDist = d
                closest = item
            }
        }
        return closest
    }
}
