package com.cheezo.ductworks.model

import kotlin.math.abs

/**
 * An individual furniture piece, door, window, or HVAC fixture placed on the floor plan canvas.
 */
data class PlacedItem(
    val id: String = WallVertex.generateId("item"),
    val catalogItemId: String,
    val itemType: ItemType,
    val name: String,
    val position: Point2D,
    val rotationDegrees: Float = 0f,
    val widthCm: Float,
    val depthCm: Float,
    val heightCm: Float = 80f,
    val elevationCm: Float = 0f,

    // Wall mounting properties (for doors, windows, and wall vents)
    val wallId: String? = null,
    val offsetAlongWallRatio: Float = 0.5f, // 0.0 to 1.0 along wall segment
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val zIndex: Int = 0
) {
    val isOpening: Boolean get() = itemType.isOpening || wallId != null

    /**
     * Calculates the 4 corner points of the rotated bounding rectangle in world space.
     */
    fun getBoundingCorners(): List<Point2D> {
        val halfW = widthCm * 0.5f
        val halfD = depthCm * 0.5f

        val localCorners = listOf(
            Point2D(-halfW, -halfD),
            Point2D(halfW, -halfD),
            Point2D(halfW, halfD),
            Point2D(-halfW, halfD)
        )

        return localCorners.map { localPt ->
            localPt.rotate(rotationDegrees) + position
        }
    }

    /**
     * Checks if a world coordinate [p] is inside this item's boundary.
     */
    fun containsPoint(p: Point2D): Boolean {
        // Transform point into local space relative to center and rotation
        val unrotated = (p - position).rotate(-rotationDegrees)
        val halfW = widthCm * 0.5f
        val halfD = depthCm * 0.5f
        return abs(unrotated.x) <= halfW && abs(unrotated.y) <= halfD
    }

    /**
     * Top-most rotation knob handle in world space (offset outside top border).
     */
    fun getRotationHandlePoint(): Point2D {
        val halfD = depthCm * 0.5f
        val offsetLocal = Point2D(0f, -halfD - 28f)
        return offsetLocal.rotate(rotationDegrees) + position
    }

    companion object {
        fun fromCatalog(catalogItem: CatalogItem, position: Point2D): PlacedItem =
            PlacedItem(
                catalogItemId = catalogItem.id,
                itemType = catalogItem.itemType,
                name = catalogItem.nameRu,
                position = position,
                widthCm = catalogItem.defaultWidthCm,
                depthCm = catalogItem.defaultDepthCm,
                heightCm = catalogItem.defaultHeightCm,
                elevationCm = catalogItem.defaultElevationCm,
                zIndex = if (catalogItem.itemType == ItemType.CARPET_RECT) -10 else 0
            )
    }
}
