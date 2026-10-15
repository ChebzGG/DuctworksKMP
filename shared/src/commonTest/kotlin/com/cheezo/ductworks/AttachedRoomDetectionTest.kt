package com.cheezo.ductworks

import com.cheezo.ductworks.engine.CadEngine
import com.cheezo.ductworks.model.PlanDocument
import com.cheezo.ductworks.model.Point2D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AttachedRoomDetectionTest {

    @Test
    fun testAttachedIncompleteRoomFormsEnclosedRoom() {
        val engine = CadEngine(PlanDocument())

        // 1. Create a full rectangular room [ ] (800x600 cm)
        // Corners: (0, 0), (800, 0), (800, 600), (0, 600)
        engine.addRectangularRoom(
            center = Point2D(400f, 300f),
            widthCm = 800f,
            heightCm = 600f,
            name = "Главная комната"
        )

        val doc1 = engine.currentDocument
        assertEquals(1, doc1.rooms.size, "Initial room must be detected")
        assertEquals("Главная комната", doc1.rooms[0].name)

        // 2. Attach an incomplete room "]" (3 walls) to the right wall (x=800)
        // Top wall: (800, 100) -> (1560, 100)
        engine.addWall(Point2D(800f, 100f), Point2D(1560f, 100f))

        // Right wall: (1560, 100) -> (1560, 540)
        engine.addWall(Point2D(1560f, 100f), Point2D(1560f, 540f))

        // Bottom wall: (1560, 540) -> (800, 540)
        val finalDoc = engine.addWall(Point2D(1560f, 540f), Point2D(800f, 540f))

        // 3. Verify that BOTH rooms are now detected: [ ] and ] -> [ ] ]
        assertEquals(2, finalDoc.rooms.size, "Both rooms must be detected when incomplete room attaches to existing wall")

        // First room must retain its name and area
        val mainRoom = finalDoc.rooms.firstOrNull { it.name == "Главная комната" }
        assertTrue(mainRoom != null, "Original room name must be preserved")

        val newRoom = finalDoc.rooms.firstOrNull { it.id != mainRoom.id }
        assertTrue(newRoom != null, "Attached room must exist")

        // Main room area should be ~ 48 m² (8m x 6m)
        val area1 = mainRoom.calculateAreaM2(finalDoc.vertices)
        assertEquals(48.0f, area1, 0.5f)

        // Attached room area should be ~ (15.6 - 8.0) * (5.4 - 1.0) = 7.6m * 4.4m = 33.44 m²
        val area2 = newRoom.calculateAreaM2(finalDoc.vertices)
        assertEquals(33.44f, area2, 0.5f)
    }
}
