package com.cheezo.ductworks.engine.threed

import kotlin.math.*

/**
 * High-performance 3D Vector for CAD architectural projection, camera transformations, and lighting.
 */
data class Vector3D(
    val x: Float,
    val y: Float,
    val z: Float
) {
    operator fun plus(other: Vector3D): Vector3D = Vector3D(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vector3D): Vector3D = Vector3D(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Float): Vector3D = Vector3D(x * scalar, y * scalar, z * scalar)
    operator fun div(scalar: Float): Vector3D = if (scalar != 0f) Vector3D(x / scalar, y / scalar, z / scalar) else Zero
    operator fun unaryMinus(): Vector3D = Vector3D(-x, -y, -z)

    fun dot(other: Vector3D): Float = x * other.x + y * other.y + z * other.z

    fun cross(other: Vector3D): Vector3D = Vector3D(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )

    fun lengthSquared(): Float = x * x + y * y + z * z

    fun length(): Float = sqrt(lengthSquared())

    fun normalized(): Vector3D {
        val len = length()
        return if (len > 1e-6f) this / len else Vector3D(0f, 1f, 0f)
    }

    fun distanceTo(other: Vector3D): Float = (this - other).length()

    companion object {
        val Zero = Vector3D(0f, 0f, 0f)
        val Up = Vector3D(0f, 1f, 0f)
        val Right = Vector3D(1f, 0f, 0f)
        val Forward = Vector3D(0f, 0f, 1f)
    }
}

/**
 * Standard 4x4 Transformation Matrix for 3D Camera, View, and Projection pipeline.
 */
class Matrix4(
    val m: FloatArray = FloatArray(16)
) {
    init {
        if (m.all { it == 0f }) {
            // Default to Identity
            m[0] = 1f; m[5] = 1f; m[10] = 1f; m[15] = 1f
        }
    }

    operator fun get(row: Int, col: Int): Float = m[row * 4 + col]
    operator fun set(row: Int, col: Int, value: Float) { m[row * 4 + col] = value }

    operator fun times(other: Matrix4): Matrix4 {
        val result = FloatArray(16)
        for (r in 0..3) {
            for (c in 0..3) {
                var sum = 0f
                for (k in 0..3) {
                    sum += this[r, k] * other[k, c]
                }
                result[r * 4 + c] = sum
            }
        }
        return Matrix4(result)
    }

    fun transform(v: Vector3D): Vector3D {
        val x = m[0] * v.x + m[1] * v.y + m[2] * v.z + m[3]
        val y = m[4] * v.x + m[5] * v.y + m[6] * v.z + m[7]
        val z = m[8] * v.x + m[9] * v.y + m[10] * v.z + m[11]
        val w = m[12] * v.x + m[13] * v.y + m[14] * v.z + m[15]
        return if (abs(w) > 1e-6f) {
            Vector3D(x / w, y / w, z / w)
        } else {
            Vector3D(x, y, z)
        }
    }

    companion object {
        fun identity(): Matrix4 = Matrix4()

        fun translation(tx: Float, ty: Float, tz: Float): Matrix4 {
            val res = identity()
            res[0, 3] = tx
            res[1, 3] = ty
            res[2, 3] = tz
            return res
        }

        fun scale(sx: Float, sy: Float, sz: Float): Matrix4 {
            val res = identity()
            res[0, 0] = sx
            res[1, 1] = sy
            res[2, 2] = sz
            return res
        }

        fun rotationY(angleRad: Float): Matrix4 {
            val res = identity()
            val cosA = cos(angleRad)
            val sinA = sin(angleRad)
            res[0, 0] = cosA
            res[0, 2] = sinA
            res[2, 0] = -sinA
            res[2, 2] = cosA
            return res
        }

        fun rotationX(angleRad: Float): Matrix4 {
            val res = identity()
            val cosA = cos(angleRad)
            val sinA = sin(angleRad)
            res[1, 1] = cosA
            res[1, 2] = -sinA
            res[2, 1] = sinA
            res[2, 2] = cosA
            return res
        }

        fun rotationZ(angleRad: Float): Matrix4 {
            val res = identity()
            val cosA = cos(angleRad)
            val sinA = sin(angleRad)
            res[0, 0] = cosA
            res[0, 1] = -sinA
            res[1, 0] = sinA
            res[1, 1] = cosA
            return res
        }

        /**
         * Creates a standard LookAt View Matrix.
         */
        fun lookAt(eye: Vector3D, target: Vector3D, up: Vector3D): Matrix4 {
            val forward = (target - eye).normalized()
            val side = forward.cross(up).normalized()
            val realUp = side.cross(forward).normalized()

            val res = identity()
            res[0, 0] = side.x
            res[0, 1] = side.y
            res[0, 2] = side.z
            res[0, 3] = -side.dot(eye)

            res[1, 0] = realUp.x
            res[1, 1] = realUp.y
            res[1, 2] = realUp.z
            res[1, 3] = -realUp.dot(eye)

            res[2, 0] = -forward.x
            res[2, 1] = -forward.y
            res[2, 2] = -forward.z
            res[2, 3] = forward.dot(eye)

            res[3, 0] = 0f
            res[3, 1] = 0f
            res[3, 2] = 0f
            res[3, 3] = 1f

            return res
        }

        /**
         * Creates a perspective projection matrix.
         */
        fun perspective(fovDegrees: Float, aspect: Float, near: Float = 1f, far: Float = 20000f): Matrix4 {
            val fovRad = fovDegrees * (PI.toFloat() / 180f)
            val tanHalfFov = tan(fovRad * 0.5f)

            val res = Matrix4(FloatArray(16))
            res[0, 0] = 1f / (aspect * tanHalfFov)
            res[1, 1] = 1f / tanHalfFov
            res[2, 2] = -(far + near) / (far - near)
            res[2, 3] = -(2f * far * near) / (far - near)
            res[3, 2] = -1f
            res[3, 3] = 0f

            return res
        }
    }
}
