package com.cheezo.ductworks.ui

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import com.cheezo.ductworks.model.BoundingBox
import com.cheezo.ductworks.model.Point2D
import kotlin.math.max
import kotlin.math.min

/**
 * Manages pan and zoom transformations between Screen Space (pixels) and World Space (centimeters).
 */
@Stable
class CadViewportState(
    initialZoom: Float = 0.8f,
    initialPan: Offset = Offset.Zero
) {
    /**
     * Scale factor: pixels per real-world centimeter (cm).
     */
    var zoom by mutableStateOf(initialZoom)
        private set

    /**
     * Pan offset in screen pixels.
     */
    var panOffset by mutableStateOf(initialPan)
        private set

    val minZoom: Float = 0.05f
    val maxZoom: Float = 10.0f

    fun screenToWorld(screenOffset: Offset): Point2D {
        val wx = (screenOffset.x - panOffset.x) / zoom
        val wy = (screenOffset.y - panOffset.y) / zoom
        return Point2D(wx, wy)
    }

    fun worldToScreen(worldPoint: Point2D): Offset {
        val sx = worldPoint.x * zoom + panOffset.x
        val sy = worldPoint.y * zoom + panOffset.y
        return Offset(sx, sy)
    }

    fun panBy(delta: Offset) {
        panOffset += delta
    }

    fun setPan(newPan: Offset) {
        panOffset = newPan
    }

    /**
     * Zooms in or out around a screen pivot point (e.g. mouse cursor or screen center).
     */
    fun zoomAround(screenPivot: Offset, factor: Float) {
        val targetZoom = (zoom * factor).coerceIn(minZoom, maxZoom)
        if (targetZoom == zoom) return

        // Compute world point under screen pivot before zoom
        val worldPivot = screenToWorld(screenPivot)

        zoom = targetZoom
        // Adjust panOffset so that the world point remains under the screen pivot
        panOffset = Offset(
            screenPivot.x - worldPivot.x * zoom,
            screenPivot.y - worldPivot.y * zoom
        )
    }

    fun resetView(screenCenter: Offset) {
        zoom = 0.8f
        panOffset = screenCenter
    }

    /**
     * Fits the plan bounding box neatly within the screen dimensions.
     */
    fun fitToBounds(box: BoundingBox, screenWidth: Float, screenHeight: Float, paddingPx: Float = 80f) {
        if (screenWidth <= 0 || screenHeight <= 0) return

        val boxWidth = max(box.width, 100f)
        val boxHeight = max(box.height, 100f)

        val availWidth = max(screenWidth - paddingPx * 2, 100f)
        val availHeight = max(screenHeight - paddingPx * 2, 100f)

        val zoomX = availWidth / boxWidth
        val zoomY = availHeight / boxHeight
        val newZoom = min(zoomX, zoomY).coerceIn(minZoom, 3.0f)

        zoom = newZoom
        val center = box.center
        panOffset = Offset(
            screenWidth * 0.5f - center.x * newZoom,
            screenHeight * 0.5f - center.y * newZoom
        )
    }
}
