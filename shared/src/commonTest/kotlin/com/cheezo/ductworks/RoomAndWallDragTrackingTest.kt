package com.cheezo.ductworks

import com.cheezo.ductworks.engine.CadEngine
import com.cheezo.ductworks.model.PlanDocument
import com.cheezo.ductworks.model.Point2D
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoomAndWallDragTrackingTest {

    @Test
    fun testRoomDragMovesFurnitureAndOpenings() {
        val sampleDoc = PlanDocument.createSamplePlan()
        val engine = CadEngine(sampleDoc)

        val room = sampleDoc.rooms.first()
        val window = sampleDoc.items["sample-window"]!!
        val door = sampleDoc.items["sample-door"]!!
        val sofa = sampleDoc.items["sample-sofa"]!!

        val delta = Point2D(150f, 80f)

        engine.beginDrag()
        val draggedDoc = engine.dragRoom(room.id, delta)

        // 1. All vertices of the room should be translated by delta
        for (vId in room.vertexIds) {
            val originalPos = sampleDoc.vertices[vId]!!.position
            val draggedPos = draggedDoc.vertices[vId]!!.position
            assertEquals(originalPos.x + delta.x, draggedPos.x, 0.01f)
            assertEquals(originalPos.y + delta.y, draggedPos.y, 0.01f)
        }

        // 2. Sofa inside room must be translated by delta
        val draggedSofa = draggedDoc.items["sample-sofa"]!!
        assertEquals(sofa.position.x + delta.x, draggedSofa.position.x, 0.01f)
        assertEquals(sofa.position.y + delta.y, draggedSofa.position.y, 0.01f)

        // 3. Window on room wall w1 must be translated by delta
        val draggedWindow = draggedDoc.items["sample-window"]!!
        assertEquals(window.position.x + delta.x, draggedWindow.position.x, 0.01f)
        assertEquals(window.position.y + delta.y, draggedWindow.position.y, 0.01f)

        // 4. Door on room wall w4 must be translated by delta
        val draggedDoor = draggedDoc.items["sample-door"]!!
        assertEquals(door.position.x + delta.x, draggedDoor.position.x, 0.01f)
        assertEquals(door.position.y + delta.y, draggedDoor.position.y, 0.01f)
    }

    @Test
    fun testWallDragMovesOpeningOnWall() {
        val sampleDoc = PlanDocument.createSamplePlan()
        val engine = CadEngine(sampleDoc)

        val window = sampleDoc.items["sample-window"]!!
        val wallId = window.wallId!!
        val originalWall = sampleDoc.walls[wallId]!!
        val sofa = sampleDoc.items["sample-sofa"]!!

        // Drag wall w1 perpendicularly
        val delta = Point2D(0f, -50f)

        engine.beginDrag()
        val draggedDoc = engine.dragWall(wallId, delta)

        // Window on wall w1 must follow the wall
        val draggedWindow = draggedDoc.items["sample-window"]!!
        val wallLine = originalWall.toLineSegment(draggedDoc.vertices)!!

        // Window must lie on the shifted wall line
        val distToNewWall = wallLine.distanceTo(draggedWindow.position)
        assertTrue(distToNewWall < 0.1f, "Window should stay on the moved wall, distance was $distToNewWall")
        assertEquals(wallLine.angleDegrees, draggedWindow.rotationDegrees, 0.5f)

        // Sofa inside room should NOT move when only a wall is dragged
        val draggedSofa = draggedDoc.items["sample-sofa"]!!
        assertEquals(sofa.position.x, draggedSofa.position.x, 0.01f)
        assertEquals(sofa.position.y, draggedSofa.position.y, 0.01f)
    }

    @Test
    fun testVertexDragTracksOpeningOnAdjoiningWall() {
        val sampleDoc = PlanDocument.createSamplePlan()
        val engine = CadEngine(sampleDoc)

        val window = sampleDoc.items["sample-window"]!!
        val wall = sampleDoc.walls[window.wallId]!!

        // Drag the end vertex of the window's wall
        val vEnd = sampleDoc.vertices[wall.endVertexId]!!
        val targetPos = vEnd.position + Point2D(50f, 30f)

        engine.beginDrag()
        val draggedDoc = engine.dragVertex(wall.endVertexId, targetPos)

        val draggedWindow = draggedDoc.items["sample-window"]!!
        val newWallLine = wall.toLineSegment(draggedDoc.vertices)!!

        // Window must stay on the stretched/rotated wall
        val distToNewWall = newWallLine.distanceTo(draggedWindow.position)
        assertTrue(distToNewWall < 0.1f, "Window should stay on the wall after vertex move, distance was $distToNewWall")
        assertEquals(newWallLine.angleDegrees, draggedWindow.rotationDegrees, 0.5f)
    }

    @Test
    fun testTwoAdjacentRoomsDoesNotDetectOuterBoundingFace() {
        val v1 = com.cheezo.ductworks.model.WallVertex(id = "v1", position = Point2D(0f, 0f))
        val v2 = com.cheezo.ductworks.model.WallVertex(id = "v2", position = Point2D(400f, 0f))
        val v3 = com.cheezo.ductworks.model.WallVertex(id = "v3", position = Point2D(400f, 300f))
        val v4 = com.cheezo.ductworks.model.WallVertex(id = "v4", position = Point2D(0f, 300f))
        val v5 = com.cheezo.ductworks.model.WallVertex(id = "v5", position = Point2D(800f, 0f))
        val v6 = com.cheezo.ductworks.model.WallVertex(id = "v6", position = Point2D(800f, 300f))

        val vertices = mapOf(
            v1.id to v1, v2.id to v2, v3.id to v3,
            v4.id to v4, v5.id to v5, v6.id to v6
        )

        val w1 = com.cheezo.ductworks.model.WallSegment(id = "w1", startVertexId = "v1", endVertexId = "v2")
        val w2 = com.cheezo.ductworks.model.WallSegment(id = "w2", startVertexId = "v2", endVertexId = "v3") // shared
        val w3 = com.cheezo.ductworks.model.WallSegment(id = "w3", startVertexId = "v3", endVertexId = "v4")
        val w4 = com.cheezo.ductworks.model.WallSegment(id = "w4", startVertexId = "v4", endVertexId = "v1")

        val w5 = com.cheezo.ductworks.model.WallSegment(id = "w5", startVertexId = "v2", endVertexId = "v5")
        val w6 = com.cheezo.ductworks.model.WallSegment(id = "w6", startVertexId = "v5", endVertexId = "v6")
        val w7 = com.cheezo.ductworks.model.WallSegment(id = "w7", startVertexId = "v6", endVertexId = "v3")

        val walls = mapOf(
            w1.id to w1, w2.id to w2, w3.id to w3, w4.id to w4,
            w5.id to w5, w6.id to w6, w7.id to w7
        )

        val rooms = com.cheezo.ductworks.engine.RoomDetector.detectRooms(vertices, walls, emptyList())
        assertEquals(2, rooms.size, "Should detect exactly 2 rooms, not the outer perimeter as a 3rd room!")
    }

    @Test
    fun testTJunctionWallDraggingPreservesPerpendicularity() {
        // Horizontal wall w1: (0, 0) to (400, 0)
        // Horizontal continuation wall w2: (400, 0) to (800, 0)
        // Perpendicular partition wall w3: (400, 0) to (400, 300)
        val v1 = com.cheezo.ductworks.model.WallVertex(id = "v1", position = Point2D(0f, 0f))
        val v2 = com.cheezo.ductworks.model.WallVertex(id = "v2", position = Point2D(400f, 0f)) // T-junction vertex
        val v3 = com.cheezo.ductworks.model.WallVertex(id = "v3", position = Point2D(800f, 0f))
        val v4 = com.cheezo.ductworks.model.WallVertex(id = "v4", position = Point2D(400f, 300f))

        val vertices = mapOf(v1.id to v1, v2.id to v2, v3.id to v3, v4.id to v4)
        val w1 = com.cheezo.ductworks.model.WallSegment(id = "w1", startVertexId = "v1", endVertexId = "v2")
        val w2 = com.cheezo.ductworks.model.WallSegment(id = "w2", startVertexId = "v2", endVertexId = "v3")
        val w3 = com.cheezo.ductworks.model.WallSegment(id = "w3", startVertexId = "v2", endVertexId = "v4") // Perpendicular partition
        val walls = mapOf(w1.id to w1, w2.id to w2, w3.id to w3)

        // Drag wall w1 up by 50 units (delta = (0, -50))
        val newVertices = com.cheezo.ductworks.engine.WallDraggingEngine.dragWall(
            wallId = "w1",
            totalDelta = Point2D(0f, -50f),
            baselineVertices = vertices,
            currentWalls = walls
        )

        val movedV2 = newVertices["v2"]!!
        // The T-junction vertex v2 should slide along the line of w3: X must stay exactly 400.0f, Y moves to -50.0f!
        assertEquals(400f, movedV2.position.x, 0.01f, "T-junction vertex must stay along the perpendicular partition axis!")
        assertEquals(-50f, movedV2.position.y, 0.01f, "T-junction vertex must shift vertically to match the shifted wall!")
    }

    @Test
    fun testOpeningClampedWithinWallBounds() {
        val sampleDoc = PlanDocument.createSamplePlan()
        val engine = CadEngine(sampleDoc)

        val window = sampleDoc.items["sample-window"]!! // Width 140 cm on w1
        val wallId = window.wallId!!

        // Shorten wall w1 down to 160 cm (barely enough for 140 cm window)
        val resizedDoc = engine.resizeWallLength(wallId, 160f)
        val resizedWindow = resizedDoc.items["sample-window"]!!

        // Window width is 140 cm, halfW is 70 cm. On a 160 cm wall, center MUST be within [70/160, 1 - 70/160] = [0.4375, 0.5625]
        val ratio = resizedWindow.offsetAlongWallRatio
        assertTrue(ratio in 0.43f..0.57f, "Window ratio should be clamped within wall endpoints, was $ratio")
    }

    @Test
    fun testItemToItemMagneticEdgeSnap() {
        // Bed at (0, 0), width 200, depth 210
        val bed = com.cheezo.ductworks.model.PlacedItem(
            id = "bed",
            catalogItemId = "bed_double",
            itemType = com.cheezo.ductworks.model.ItemType.BED_DOUBLE,
            name = "Кровать",
            position = Point2D(0f, 0f),
            widthCm = 200f,
            depthCm = 210f
        )

        // Nightstand with width 50, depth 45
        val nightstand = com.cheezo.ductworks.model.PlacedItem(
            id = "nightstand",
            catalogItemId = "nightstand",
            itemType = com.cheezo.ductworks.model.ItemType.NIGHTSTAND,
            name = "Тумба",
            position = Point2D(130f, -80f), // user placed near right edge of bed and near headboard
            widthCm = 50f,
            depthCm = 45f
        )

        // Expected right edge of bed: 0 + 100 = 100.
        // Nightstand halfW: 25.
        // Snapped X: 100 + 25 = 125!
        // Expected headboard top: 0 - 105 = -105.
        // Nightstand flush top: -105 + 22.5 = -82.5!
        val snapResult = com.cheezo.ductworks.engine.cad.InteriorPlanningEngine.resolveItemToItemSnap(
            movingItem = nightstand,
            rawTargetPos = Point2D(130f, -80f),
            otherItems = listOf(bed),
            snapDistanceCm = 15f
        )

        assertTrue(snapResult != null, "Should snap nightstand to bed edge!")
        assertEquals(125f, snapResult.snappedPosition.x, 0.01f, "X should snap flush to the right edge of bed!")
        assertEquals(-82.5f, snapResult.snappedPosition.y, 0.01f, "Y should snap flush to the headboard of bed!")
        assertTrue(snapResult.guides.isNotEmpty(), "Should generate visual alignment guides!")
    }

    @Test
    fun testClosingWallsCreatesRoom() {
        val engine = CadEngine(PlanDocument())

        // Draw 4 walls forming a closed 4x3m room
        engine.addWall(Point2D(0f, 0f), Point2D(400f, 0f))
        engine.addWall(Point2D(400f, 0f), Point2D(400f, 300f))
        engine.addWall(Point2D(400f, 300f), Point2D(0f, 300f))
        // 4th closing wall back to (0,0)
        val finalDoc = engine.addWall(Point2D(0f, 300f), Point2D(0f, 0f))

        assertEquals(1, finalDoc.rooms.size, "Closing 4 walls should automatically detect and create 1 room!")
        assertEquals(4, finalDoc.rooms.first().vertexIds.size, "Room should have 4 vertices!")
    }

    @Test
    fun testOrthoDisabledAllowsFreeAngle() {
        val origin = Point2D(100f, 100f)
        val diagonalPoint = Point2D(250f, 210f) // 36° angle, neither horizontal nor vertical

        // With Ortho ON: should lock to either horizontal or vertical
        val snapOrthoOn = com.cheezo.ductworks.model.CadSnapper.resolveSnap(
            rawPoint = diagonalPoint,
            originPoint = origin,
            vertices = emptyList(),
            walls = emptyList(),
            vertexMap = emptyMap(),
            gridStepCm = 20f,
            isGridSnapEnabled = false,
            isOrthoEnabled = true
        )
        // One of the axes must be identical to origin
        assertTrue(
            abs(snapOrthoOn.point.x - origin.x) < 0.01f || abs(snapOrthoOn.point.y - origin.y) < 0.01f,
            "With Ortho ON, movement must lock to 90-degree axis!"
        )

        // With Ortho OFF: should allow free diagonal angle
        val snapOrthoOff = com.cheezo.ductworks.model.CadSnapper.resolveSnap(
            rawPoint = diagonalPoint,
            originPoint = origin,
            vertices = emptyList(),
            walls = emptyList(),
            vertexMap = emptyMap(),
            gridStepCm = 20f,
            isGridSnapEnabled = false,
            isOrthoEnabled = false
        )
        assertEquals(diagonalPoint.x, snapOrthoOff.point.x, 0.01f, "With Ortho OFF, X must not be locked!")
        assertEquals(diagonalPoint.y, snapOrthoOff.point.y, 0.01f, "With Ortho OFF, Y must not be locked!")
    }

    @Test
    fun testSnapDisabledAllowsFreeContinuousPosition() {
        val nearVertex = com.cheezo.ductworks.model.WallVertex(id = "v1", position = Point2D(100f, 100f))
        val rawPoint = Point2D(105.7f, 103.2f) // 6.5cm from vertex, non-grid coordinates

        // With Snapping ON: should magnetize to nearVertex
        val snapOn = com.cheezo.ductworks.model.CadSnapper.resolveSnap(
            rawPoint = rawPoint,
            originPoint = null,
            vertices = listOf(nearVertex),
            walls = emptyList(),
            vertexMap = mapOf(nearVertex.id to nearVertex),
            gridStepCm = 20f,
            isGridSnapEnabled = true,
            isOrthoEnabled = false
        )
        assertEquals(nearVertex.position, snapOn.point, "With Snap ON, should snap to vertex!")

        // With Snapping OFF: should NOT magnetize to nearVertex, should keep exact rawPoint
        val snapOff = com.cheezo.ductworks.model.CadSnapper.resolveSnap(
            rawPoint = rawPoint,
            originPoint = null,
            vertices = listOf(nearVertex),
            walls = emptyList(),
            vertexMap = mapOf(nearVertex.id to nearVertex),
            gridStepCm = 20f,
            isGridSnapEnabled = false,
            isOrthoEnabled = false
        )
        assertEquals(rawPoint.x, snapOff.point.x, 0.01f, "With Snap OFF, point X must be exact raw position!")
        assertEquals(rawPoint.y, snapOff.point.y, 0.01f, "With Snap OFF, point Y must be exact raw position!")
        assertEquals(com.cheezo.ductworks.model.SnapType.NONE, snapOff.type, "With Snap OFF, snap type must be NONE!")
    }

    @Test
    fun testOrthoDoesNotSnapToArbitraryWallEdgeAtAngle() {
        val origin = Point2D(100f, 100f)
        // A wall running parallel 12 cm away, from (0, 88) to (500, 88)
        val vA = com.cheezo.ductworks.model.WallVertex(id = "va", position = Point2D(0f, 88f))
        val vB = com.cheezo.ductworks.model.WallVertex(id = "vb", position = Point2D(500f, 88f))
        val wall = com.cheezo.ductworks.model.WallSegment(id = "w_parallel", startVertexId = "va", endVertexId = "vb")
        val vertexMap = mapOf("va" to vA, "vb" to vB)

        // Cursor is at (255, 95) - 7 cm from the wall, but user is moving horizontally from (100, 100)
        val rawPoint = Point2D(255f, 95f)

        val snap = com.cheezo.ductworks.model.CadSnapper.resolveSnap(
            rawPoint = rawPoint,
            originPoint = origin,
            vertices = listOf(vA, vB),
            walls = listOf(wall),
            vertexMap = vertexMap,
            gridStepCm = 20f,
            isGridSnapEnabled = true,
            isOrthoEnabled = true
        )

        // In AutoCAD, Ortho must maintain y = 100f! It must NOT jump to y = 88f on the nearby wall!
        assertEquals(100f, snap.point.y, 0.01f, "Ortho must remain locked to origin.y and not bend to nearby wall!")
        assertEquals(260f, snap.point.x, 0.01f, "Horizontal ortho position should snap to grid along the axis!")
    }

    @Test
    fun testOrthoSnapsToPerpendicularWallIntersection() {
        val origin = Point2D(100f, 100f)
        // A vertical wall crossing x = 300 from y = 0 to y = 400
        val vA = com.cheezo.ductworks.model.WallVertex(id = "va", position = Point2D(300f, 0f))
        val vB = com.cheezo.ductworks.model.WallVertex(id = "vb", position = Point2D(300f, 400f))
        val wall = com.cheezo.ductworks.model.WallSegment(id = "w_vert", startVertexId = "va", endVertexId = "vb")
        val vertexMap = mapOf("va" to vA, "vb" to vB)

        // User moves horizontally towards the wall: cursor is near (295, 102)
        val rawPoint = Point2D(295f, 102f)

        val snap = com.cheezo.ductworks.model.CadSnapper.resolveSnap(
            rawPoint = rawPoint,
            originPoint = origin,
            vertices = listOf(vA, vB),
            walls = listOf(wall),
            vertexMap = vertexMap,
            gridStepCm = 20f,
            isGridSnapEnabled = true,
            isOrthoEnabled = true,
            wallSnapRadiusCm = 15f
        )

        // Should hit the wall perpendicularly at (300, 100)
        assertEquals(300f, snap.point.x, 0.01f, "Should snap to vertical wall's X!")
        assertEquals(100f, snap.point.y, 0.01f, "Should stay on horizontal ortho axis Y!")
        assertEquals(com.cheezo.ductworks.model.SnapType.WALL_EDGE, snap.type)
        assertEquals("w_vert", snap.snappedWallId)
    }

    @Test
    fun testOrthoAlignsWithVertexAlongAxis() {
        val origin = Point2D(100f, 100f)
        // An existing corner located at (400, 300)
        val corner = com.cheezo.ductworks.model.WallVertex(id = "corner", position = Point2D(400f, 300f))

        // Moving horizontally along y = 100, approaching x = 400 (e.g. cursor at 405, 98)
        val rawPoint = Point2D(405f, 98f)

        val snap = com.cheezo.ductworks.model.CadSnapper.resolveSnap(
            rawPoint = rawPoint,
            originPoint = origin,
            vertices = listOf(corner),
            walls = emptyList(),
            vertexMap = mapOf("corner" to corner),
            gridStepCm = 20f,
            isGridSnapEnabled = true,
            isOrthoEnabled = true
        )

        // Should align X to 400 and keep Y = 100
        assertEquals(400f, snap.point.x, 0.01f, "O-Track should align X with opposite corner!")
        assertEquals(100f, snap.point.y, 0.01f, "O-Track must keep Ortho horizontal axis Y!")
        assertEquals(com.cheezo.ductworks.model.SnapType.ALIGNMENT_GUIDE, snap.type)
        assertTrue(snap.guideLines.any { !it.isHorizontal }, "Should include vertical alignment guide line!")
    }
}

