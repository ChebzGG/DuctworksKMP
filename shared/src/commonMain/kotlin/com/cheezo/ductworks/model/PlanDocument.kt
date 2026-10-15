package com.cheezo.ductworks.model

/**
 * Immutable snapshot of the architectural floor plan document,
 * including structural walls, enclosed rooms, and placed furniture/doors/windows.
 */
data class PlanDocument(
    val id: String = WallVertex.generateId("doc"),
    val name: String = "Новый проект",
    val vertices: Map<String, WallVertex> = emptyMap(),
    val walls: Map<String, WallSegment> = emptyMap(),
    val rooms: List<Room> = emptyList(),
    val items: Map<String, PlacedItem> = emptyMap(),
    val defaultCeilingHeightCm: Float = 270f
) {
    fun getConnectedWalls(vertexId: String): List<WallSegment> =
        walls.values.filter { it.hasVertex(vertexId) }

    fun getBoundingBox(padding: Float = 100f): BoundingBox {
        if (vertices.isEmpty() && items.isEmpty()) {
            return BoundingBox(-500f, -500f, 500f, 500f)
        }
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        for (v in vertices.values) {
            val p = v.position
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }

        for (item in items.values) {
            for (corner in item.getBoundingCorners()) {
                if (corner.x < minX) minX = corner.x
                if (corner.y < minY) minY = corner.y
                if (corner.x > maxX) maxX = corner.x
                if (corner.y > maxY) maxY = corner.y
            }
        }

        return BoundingBox(
            minX - padding,
            minY - padding,
            maxX + padding,
            maxY + padding
        )
    }

    val totalFloorAreaM2: Float
        get() = rooms.sumOf { it.calculateAreaM2(vertices).toDouble() }.toFloat()

    val totalWallsLengthM: Float
        get() = walls.values.sumOf { (it.toLineSegment(vertices)?.length ?: 0f) / 100.0 }.toFloat()

    companion object {
        /**
         * Creates a starter rectangular sample room with furniture, doors, and windows (Planner 5D style).
         */
        fun createSamplePlan(): PlanDocument {
            val v1 = WallVertex("v1", Point2D(-300f, -220f))
            val v2 = WallVertex("v2", Point2D(300f, -220f))
            val v3 = WallVertex("v3", Point2D(300f, 220f))
            val v4 = WallVertex("v4", Point2D(-300f, 220f))

            val w1 = WallSegment("w1", "v1", "v2", thicknessCm = 25f, wallType = WallType.EXTERIOR)
            val w2 = WallSegment("w2", "v2", "v3", thicknessCm = 25f, wallType = WallType.EXTERIOR)
            val w3 = WallSegment("w3", "v3", "v4", thicknessCm = 25f, wallType = WallType.EXTERIOR)
            val w4 = WallSegment("w4", "v4", "v1", thicknessCm = 25f, wallType = WallType.EXTERIOR)

            val room = Room(
                id = "room-main",
                name = "Гостиная",
                roomType = RoomType.LIVING_ROOM,
                vertexIds = listOf("v1", "v2", "v3", "v4"),
                ceilingHeightCm = 280f
            )

            // Sample doors, windows, and furniture
            val window = PlacedItem(
                id = "sample-window",
                catalogItemId = "window_single",
                itemType = ItemType.WINDOW_SINGLE,
                name = "Окно",
                position = Point2D(0f, -220f),
                rotationDegrees = 0f,
                widthCm = 140f,
                depthCm = 25f,
                wallId = "w1",
                offsetAlongWallRatio = 0.5f
            )

            val door = PlacedItem(
                id = "sample-door",
                catalogItemId = "door_single",
                itemType = ItemType.DOOR_SINGLE,
                name = "Дверь",
                position = Point2D(-300f, 0f),
                rotationDegrees = 90f,
                widthCm = 90f,
                depthCm = 25f,
                wallId = "w4",
                offsetAlongWallRatio = 0.5f
            )

            val sofa = PlacedItem(
                id = "sample-sofa",
                catalogItemId = "sofa_straight",
                itemType = ItemType.SOFA_STRAIGHT,
                name = "Диван прямой",
                position = Point2D(0f, 130f),
                rotationDegrees = 0f,
                widthCm = 220f,
                depthCm = 90f,
                heightCm = 85f
            )

            val coffeeTable = PlacedItem(
                id = "sample-table",
                catalogItemId = "coffee_table",
                itemType = ItemType.COFFEE_TABLE,
                name = "Журнальный столик",
                position = Point2D(0f, 30f),
                rotationDegrees = 0f,
                widthCm = 110f,
                depthCm = 60f,
                heightCm = 45f
            )

            return PlanDocument(
                name = "План помещения 1",
                vertices = mapOf(v1.id to v1, v2.id to v2, v3.id to v3, v4.id to v4),
                walls = mapOf(w1.id to w1, w2.id to w2, w3.id to w3, w4.id to w4),
                rooms = listOf(room),
                items = mapOf(
                    window.id to window,
                    door.id to door,
                    sofa.id to sofa,
                    coffeeTable.id to coffeeTable
                )
            )
        }
    }
}
