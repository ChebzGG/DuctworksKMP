package com.cheezo.ductworks.engine

import com.cheezo.ductworks.model.*
import kotlin.math.atan2

/**
 * Robust planar graph face-finding algorithm.
 * Automatically detects enclosed rooms from connected walls.
 */
object RoomDetector {

    private data class DirectedEdge(
        val from: String,
        val to: String,
        val angle: Double
    )

    /**
     * Finds all enclosed interior rooms in the plan.
     * Existing rooms are matched by vertex set to retain their names, types, and custom settings.
     */
    fun detectRooms(
        vertices: Map<String, WallVertex>,
        walls: Map<String, WallSegment>,
        existingRooms: List<Room>
    ): List<Room> {
        if (vertices.size < 3 || walls.size < 3) return emptyList()

        // 0. Planarize walls with respect to vertices: any vertex lying on a wall splits the wall in the planar graph
        val planarWalls = walls.toMutableMap()
        var splitOccurred = true
        var iteration = 0
        while (splitOccurred && iteration++ < 25) {
            splitOccurred = false
            for (w in planarWalls.values.toList()) {
                if (w.id !in planarWalls) continue
                val pStart = vertices[w.startVertexId]?.position ?: continue
                val pEnd = vertices[w.endVertexId]?.position ?: continue
                val line = LineSegment(pStart, pEnd)
                if (line.length < 5f) continue

                val tol = maxOf(20f, w.thicknessCm * 0.75f)
                val onWall = mutableListOf<Pair<WallVertex, Float>>()
                for (v in vertices.values) {
                    if (v.id == w.startVertexId || v.id == w.endVertexId) continue
                    val dist = line.distanceTo(v.position)
                    val t = line.parameterOf(v.position)
                    if (dist <= tol && v.position.distanceTo(pStart) > 5f && v.position.distanceTo(pEnd) > 5f && t in 0.01f..0.99f) {
                        onWall.add(v to t)
                    }
                }

                if (onWall.isNotEmpty()) {
                    onWall.sortBy { it.second }
                    planarWalls.remove(w.id)
                    var prevId = w.startVertexId
                    for ((v, _) in onWall) {
                        val sub = WallSegment(startVertexId = prevId, endVertexId = v.id, thicknessCm = w.thicknessCm, wallType = w.wallType)
                        planarWalls[sub.id] = sub
                        prevId = v.id
                    }
                    val last = WallSegment(startVertexId = prevId, endVertexId = w.endVertexId, thicknessCm = w.thicknessCm, wallType = w.wallType)
                    planarWalls[last.id] = last
                    splitOccurred = true
                    break
                }
            }
        }

        // Unify near-coincident vertices in planarWalls to guarantee topologically closed cycles
        val vList = vertices.values.toList()
        val replaceMap = mutableMapOf<String, String>()
        for (i in vList.indices) {
            val v1 = vList[i]
            if (v1.id in replaceMap) continue
            for (j in i + 1 until vList.size) {
                val v2 = vList[j]
                if (v2.id in replaceMap) continue
                if (v1.position.distanceTo(v2.position) <= 15f) {
                    replaceMap[v2.id] = v1.id
                }
            }
        }
        if (replaceMap.isNotEmpty()) {
            val pWallsList = planarWalls.values.toList()
            for (w in pWallsList) {
                var newStart = w.startVertexId
                while (newStart in replaceMap) newStart = replaceMap[newStart]!!
                var newEnd = w.endVertexId
                while (newEnd in replaceMap) newEnd = replaceMap[newEnd]!!
                if (newStart == newEnd) {
                    planarWalls.remove(w.id)
                } else if (newStart != w.startVertexId || newEnd != w.endVertexId) {
                    planarWalls[w.id] = w.copy(startVertexId = newStart, endVertexId = newEnd)
                }
            }
        }

        // 1. Build adjacency list of directed half-edges from planar walls
        val outgoingEdges = mutableMapOf<String, MutableList<DirectedEdge>>()
        for (v in vertices.keys) {
            if (v !in replaceMap) {
                outgoingEdges[v] = mutableListOf()
            }
        }

        for (w in planarWalls.values) {
            val pStart = vertices[w.startVertexId]?.position ?: continue
            val pEnd = vertices[w.endVertexId]?.position ?: continue

            val angleStartToEnd = normalizeAngle(atan2((pEnd.y - pStart.y).toDouble(), (pEnd.x - pStart.x).toDouble()))
            val angleEndToStart = normalizeAngle(atan2((pStart.y - pEnd.y).toDouble(), (pStart.x - pEnd.x).toDouble()))

            outgoingEdges[w.startVertexId]?.add(DirectedEdge(w.startVertexId, w.endVertexId, angleStartToEnd))
            outgoingEdges[w.endVertexId]?.add(DirectedEdge(w.endVertexId, w.startVertexId, angleEndToStart))
        }

        // Sort outgoing edges around each vertex in counter-clockwise order (ascending angle)
        for (edges in outgoingEdges.values) {
            edges.sortBy { it.angle }
        }

        // 2. Traverse all planar faces using the "next edge turning right/left" rule
        val visitedHalfEdges = mutableSetOf<Pair<String, String>>()
        val rawFaces = mutableListOf<List<String>>()

        for ((u, edges) in outgoingEdges) {
            for (edge in edges) {
                val halfEdge = Pair(edge.from, edge.to)
                if (halfEdge in visitedHalfEdges) continue

                // Trace this face
                val cycle = mutableListOf<String>()
                var currentFrom = edge.from
                var currentTo = edge.to
                var validCycle = false

                val stepLimit = planarWalls.size * 2 + 10
                var steps = 0

                while (steps++ < stepLimit) {
                    val currentHalf = Pair(currentFrom, currentTo)
                    if (currentHalf in visitedHalfEdges) {
                        break
                    }
                    visitedHalfEdges.add(currentHalf)
                    cycle.add(currentFrom)

                    if (currentTo == edge.from && cycle.size >= 3) {
                        validCycle = true
                        break
                    }

                    // At currentTo, find the incoming edge from currentFrom reversed
                    val currentToEdges = outgoingEdges[currentTo] ?: break
                    if (currentToEdges.isEmpty()) break

                    // Incoming angle is from currentTo to currentFrom
                    val pCurrTo = vertices[currentTo]?.position ?: break
                    val pCurrFrom = vertices[currentFrom]?.position ?: break
                    val incomingRevAngle = normalizeAngle(atan2((pCurrFrom.y - pCurrTo.y).toDouble(), (pCurrFrom.x - pCurrTo.x).toDouble()))

                    // Find the next counter-clockwise edge
                    var nextEdgeIndex = -1
                    for (i in currentToEdges.indices) {
                        if (currentToEdges[i].to == currentFrom) {
                            nextEdgeIndex = i
                            break
                        }
                    }

                    if (nextEdgeIndex == -1) {
                        // Find closest by angle
                        for (i in currentToEdges.indices) {
                            if (currentToEdges[i].angle >= incomingRevAngle) {
                                nextEdgeIndex = i
                                break
                            }
                        }
                        if (nextEdgeIndex == -1) nextEdgeIndex = 0
                    }

                    // Next edge in CCW order is (nextEdgeIndex + 1) % size
                    val nextEdge = currentToEdges[(nextEdgeIndex + 1) % currentToEdges.size]
                    currentFrom = currentTo
                    currentTo = nextEdge.to
                }

                if (validCycle && cycle.size >= 3) {
                    // Check signed area to filter out the outer bounding face
                    val points = cycle.mapNotNull { vertices[it]?.position }
                    if (points.size >= 3) {
                        val signedArea = calculateSignedArea(points)
                        // In standard screen coords (Y down), counter-clockwise interior traversal has signedArea < 0.
                        // The unbounded exterior face around the entire building has signedArea > 0 and must be filtered out!
                        // Minimum practical room area: 0.2 m^2 (2,000 cm^2).
                        if (signedArea < -2_000f) {
                            rawFaces.add(cycle)
                        }
                    }
                }
            }
        }

        // Deduplicate faces with identical vertex sets
        val uniqueCycles = mutableListOf<List<String>>()
        val seenVertexSets = mutableSetOf<Set<String>>()

        for (face in rawFaces) {
            val set = face.toSet()
            if (set.size >= 3 && set !in seenVertexSets) {
                seenVertexSets.add(set)
                uniqueCycles.add(face)
            }
        }

        // Map existing rooms or generate new rooms (preserving names, textures, types)
        val remainingExisting = existingRooms.toMutableList()
        val assignedRooms = mutableMapOf<Int, Room>()

        // 1. Exact vertex set match
        for (i in uniqueCycles.indices) {
            val set = uniqueCycles[i].toSet()
            val exact = remainingExisting.firstOrNull { it.vertexIds.toSet() == set }
            if (exact != null) {
                assignedRooms[i] = exact.copy(vertexIds = uniqueCycles[i])
                remainingExisting.remove(exact)
            }
        }

        // 2. Overlap / Subdivide match (when vertices were added to an existing room's wall)
        for (i in uniqueCycles.indices) {
            if (i in assignedRooms) continue
            val newSet = uniqueCycles[i].toSet()
            val cyclePoints = uniqueCycles[i].mapNotNull { vertices[it]?.position }
            if (cyclePoints.size < 3) continue

            val match = remainingExisting.firstOrNull { existing ->
                val oldSet = existing.vertexIds.toSet()
                val sharedCount = oldSet.intersect(newSet).size
                val isSubset = sharedCount >= 3 && (sharedCount >= oldSet.size - 1)
                if (isSubset) return@firstOrNull true

                val oldCentroid = existing.calculateCentroid(vertices)
                PolygonMath.isPointInside(oldCentroid, cyclePoints)
            }
            if (match != null) {
                assignedRooms[i] = match.copy(vertexIds = uniqueCycles[i])
                remainingExisting.remove(match)
            }
        }

        // 3. New rooms for unassigned cycles
        val existingNums = existingRooms.mapNotNull { r ->
            Regex("""\d+""").find(r.name)?.value?.toIntOrNull()
        }
        var nextNum = (existingNums.maxOrNull() ?: 0) + 1

        return uniqueCycles.indices.map { i ->
            assignedRooms[i] ?: run {
                Room(
                    id = WallVertex.generateId("room"),
                    name = "Комната ${nextNum++}",
                    roomType = RoomType.LIVING_ROOM,
                    vertexIds = uniqueCycles[i],
                    ceilingHeightCm = 270f
                )
            }
        }
    }

    private fun normalizeAngle(rad: Double): Double {
        var angle = rad
        while (angle < 0) angle += 2 * Math.PI
        while (angle >= 2 * Math.PI) angle -= 2 * Math.PI
        return angle
    }

    private fun calculateSignedArea(points: List<Point2D>): Float {
        var sum = 0f
        for (i in points.indices) {
            val curr = points[i]
            val next = points[(i + 1) % points.size]
            sum += (curr.x * next.y) - (next.x * curr.y)
        }
        return sum * 0.5f
    }
}
