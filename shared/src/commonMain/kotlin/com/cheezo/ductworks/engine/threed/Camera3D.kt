package com.cheezo.ductworks.engine.threed

import androidx.compose.ui.geometry.Offset
import kotlin.math.*

/**
 * 3D Orbit Camera for smooth architectural navigation, perspective projection, and depth-sorting.
 */
class Camera3D(
    var target: Vector3D = Vector3D(0f, 50f, 0f),
    var distance: Float = 1100f,
    var yawDegrees: Float = 45f,
    var pitchDegrees: Float = 38f,
    var fovDegrees: Float = 50f
) {
    fun orbit(deltaYaw: Float, deltaPitch: Float) {
        yawDegrees = (yawDegrees + deltaYaw) % 360f
        if (yawDegrees < 0f) yawDegrees += 360f
        pitchDegrees = (pitchDegrees + deltaPitch).coerceIn(5f, 85f)
    }

    fun pan(deltaScreenX: Float, deltaScreenY: Float) {
        val yawRad = yawDegrees * (PI.toFloat() / 180f)
        val side = Vector3D(cos(yawRad), 0f, -sin(yawRad))
        val forward = Vector3D(sin(yawRad), 0f, cos(yawRad))

        val panFactor = distance * 0.0014f
        target = target + (side * (-deltaScreenX * panFactor)) + (forward * (deltaScreenY * panFactor))
    }

    fun zoomBy(factor: Float) {
        distance = (distance * factor).coerceIn(150f, 8000f)
    }

    fun frameBuilding(centerX: Float, centerZ: Float, spanCm: Float) {
        target = Vector3D(centerX, 50f, centerZ)
        distance = (spanCm * 1.6f).coerceIn(400f, 5000f)
    }

    fun reset() {
        target = Vector3D(0f, 50f, 0f)
        distance = 1100f
        yawDegrees = 45f
        pitchDegrees = 38f
    }

    fun setPresetTop() {
        pitchDegrees = 85f
        yawDegrees = 0f
    }

    fun setPresetIsometric() {
        pitchDegrees = 35.264f
        yawDegrees = 45f
    }

    fun setPresetFront() {
        pitchDegrees = 15f
        yawDegrees = 0f
    }

    fun getEyePosition(): Vector3D {
        val yawRad = yawDegrees * (PI.toFloat() / 180f)
        val pitchRad = pitchDegrees * (PI.toFloat() / 180f)

        val cosPitch = cos(pitchRad)
        val sinPitch = sin(pitchRad)

        val eyeX = target.x + distance * cosPitch * sin(yawRad)
        val eyeY = target.y + distance * sinPitch
        val eyeZ = target.z + distance * cosPitch * cos(yawRad)

        return Vector3D(eyeX, eyeY, eyeZ)
    }

    fun getViewMatrix(): Matrix4 {
        val eye = getEyePosition()
        return Matrix4.lookAt(eye, target, Vector3D.Up)
    }

    fun getProjectionMatrix(width: Float, height: Float): Matrix4 {
        val aspect = if (height > 0f) width / height else 1f
        return Matrix4.perspective(fovDegrees, aspect, near = 10f, far = 15000f)
    }

    /**
     * Projects a 3D world coordinate into 2D Screen coordinates (X, Y) and View Depth (Z).
     * Returns null if point is behind the camera clipping plane.
     */
    fun project(
        worldPoint: Vector3D,
        viewProj: Matrix4,
        screenWidth: Float,
        screenHeight: Float
    ): ProjectedPoint? {
        // Transform point by View-Projection Matrix
        val m = viewProj.m
        val x = m[0] * worldPoint.x + m[1] * worldPoint.y + m[2] * worldPoint.z + m[3]
        val y = m[4] * worldPoint.x + m[5] * worldPoint.y + m[6] * worldPoint.z + m[7]
        val z = m[8] * worldPoint.x + m[9] * worldPoint.y + m[10] * worldPoint.z + m[11]
        val w = m[12] * worldPoint.x + m[13] * worldPoint.y + m[14] * worldPoint.z + m[15]

        if (w <= 0.1f) return null // Behind camera

        // NDC coordinates (-1 to 1)
        val ndcX = x / w
        val ndcY = y / w
        val ndcZ = z / w

        // Viewport screen transformation
        val screenX = (ndcX + 1f) * 0.5f * screenWidth
        val screenY = (1f - ndcY) * 0.5f * screenHeight

        return ProjectedPoint(Offset(screenX, screenY), depth = w)
    }
}

data class ProjectedPoint(
    val screenOffset: Offset,
    val depth: Float
)
