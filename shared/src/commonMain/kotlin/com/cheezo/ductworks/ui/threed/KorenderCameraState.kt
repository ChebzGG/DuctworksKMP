package com.cheezo.ductworks.ui.threed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import com.zakgof.korender.CameraDeclaration
import com.zakgof.korender.math.Vec3
import com.zakgof.korender.scope.FrameScope
import kotlin.math.*

enum class CameraPreset3D {
    ISOMETRIC,
    TOP_DOWN,
    FRONT
}

/**
 * 3D Orbit & Pan Camera Controller for Korender GPU Viewport.
 * Works strictly in meters (1 unit = 1 meter).
 */
class KorenderCameraState {

    var targetX by mutableFloatStateOf(0f)
    var targetY by mutableFloatStateOf(0.6f)
    var targetZ by mutableFloatStateOf(0f)

    var distance by mutableFloatStateOf(11.0f)
    var yawDegrees by mutableFloatStateOf(45f)
    var pitchDegrees by mutableFloatStateOf(38f)

    fun orbit(deltaPixelsX: Float, deltaPixelsY: Float) {
        yawDegrees = (yawDegrees - deltaPixelsX * 0.40f) % 360f
        if (yawDegrees < 0f) yawDegrees += 360f
        pitchDegrees = (pitchDegrees + deltaPixelsY * 0.40f).coerceIn(5f, 85f)
    }

    fun pan(deltaPixelsX: Float, deltaPixelsY: Float) {
        val yawRad = yawDegrees * (PI.toFloat() / 180f)
        val sideX = cos(yawRad)
        val sideZ = -sin(yawRad)
        val fwdX = sin(yawRad)
        val fwdZ = cos(yawRad)

        val panSpeed = distance * 0.0016f
        targetX += (-deltaPixelsX * sideX * panSpeed) - (deltaPixelsY * fwdX * panSpeed)
        targetZ += (-deltaPixelsX * sideZ * panSpeed) - (deltaPixelsY * fwdZ * panSpeed)
    }

    fun zoom(factor: Float) {
        distance = (distance * factor).coerceIn(2.0f, 60.0f)
    }

    fun frameBuilding(centerX: Float, centerZ: Float, spanMeters: Float) {
        targetX = centerX
        targetY = 0.6f
        targetZ = centerZ
        distance = (spanMeters * 1.8f).coerceIn(6.0f, 40.0f)
    }

    fun setPreset(preset: CameraPreset3D) {
        when (preset) {
            CameraPreset3D.ISOMETRIC -> {
                yawDegrees = 45f
                pitchDegrees = 35.26f
            }
            CameraPreset3D.TOP_DOWN -> {
                yawDegrees = 0f
                pitchDegrees = 85f
            }
            CameraPreset3D.FRONT -> {
                yawDegrees = 0f
                pitchDegrees = 15f
            }
        }
    }

    fun getEyePosition(): Vec3 {
        val yawRad = yawDegrees * (PI.toFloat() / 180f)
        val pitchRad = pitchDegrees * (PI.toFloat() / 180f)
        val cosPitch = cos(pitchRad)
        val sinPitch = sin(pitchRad)

        val eyeX = targetX + distance * cosPitch * sin(yawRad)
        val eyeY = targetY + distance * sinPitch
        val eyeZ = targetZ + distance * cosPitch * cos(yawRad)
        return Vec3(eyeX, eyeY, eyeZ)
    }

    fun FrameScope.applyCamera() {
        val eye = getEyePosition()
        val target = Vec3(targetX, targetY, targetZ)
        val toTarget = target - eye
        val dir = if (toTarget.length() > 0.001f) toTarget.normalize() else Vec3(0f, -1f, 0f)

        // Orthonormal up vector calculation
        val defaultUp = Vec3(0f, 1f, 0f)
        val right = dir % defaultUp
        val actualUp = if (right.length() > 0.001f) {
            (right.normalize() % dir).normalize()
        } else {
            // When looking straight down or up
            Vec3(0f, 0f, -1f)
        }

        camera = camera(eye, dir, actualUp)

        // High-Precision Architectural Frustum Projection:
        // near = 0.1m (10cm) prevents close wall clipping
        // far = 600m
        val near = 0.10f
        val fovRad = 48f * (PI.toFloat() / 180f)
        val frustumHeight = 2.0f * near * tan(fovRad * 0.5f)
        val aspect = if (height > 0) width.toFloat() / height.toFloat() else 1f
        val frustumWidth = frustumHeight * aspect

        projection = projection(frustumWidth, frustumHeight, near, 600f, frustum())
    }

    fun screenPointToRay(screenX: Float, screenY: Float, width: Int, height: Int): Pair<Vec3, Vec3> {
        val eye = getEyePosition()
        val target = Vec3(targetX, targetY, targetZ)
        val toTarget = target - eye
        val dir = if (toTarget.length() > 0.001f) toTarget.normalize() else Vec3(0f, -1f, 0f)

        val defaultUp = Vec3(0f, 1f, 0f)
        val rightCandidate = dir % defaultUp
        val right = if (rightCandidate.length() > 0.001f) rightCandidate.normalize() else Vec3(1f, 0f, 0f)
        val actualUp = (right % dir).normalize()

        val fovRad = 48f * (PI.toFloat() / 180f)
        val tanHalfFov = tan(fovRad * 0.5f)
        val aspect = if (height > 0) width.toFloat() / height.toFloat() else 1f

        val ndcX = (screenX - width * 0.5f) / (width * 0.5f)
        val ndcY = (height * 0.5f - screenY) / (height * 0.5f)

        val rayDir = (dir + right * (ndcX * aspect * tanHalfFov) + actualUp * (ndcY * tanHalfFov)).normalize()
        return Pair(eye, rayDir)
    }
}
