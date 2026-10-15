package com.cheezo.ductworks.model

import kotlin.math.*

/**
 * High-precision 2D Point and Vector in real-world coordinate space (centimeters).
 * 1.0 unit = 1.0 cm (e.g. 100.0 cm = 1.0 m, 10.0 mm = 1.0 cm).
 */
data class Point2D(
    val x: Float,
    val y: Float
) {
    operator fun plus(other: Point2D): Point2D = Point2D(x + other.x, y + other.y)
    operator fun minus(other: Point2D): Point2D = Point2D(x - other.x, y - other.y)
    operator fun times(scalar: Float): Point2D = Point2D(x * scalar, y * scalar)
    operator fun div(scalar: Float): Point2D = if (abs(scalar) > 1e-6f) Point2D(x / scalar, y / scalar) else Zero

    fun distanceTo(other: Point2D): Float {
        val dx = x - other.x
        val dy = y - other.y
        return sqrt(dx * dx + dy * dy)
    }

    fun distanceSquaredTo(other: Point2D): Float {
        val dx = x - other.x
        val dy = y - other.y
        return dx * dx + dy * dy
    }

    fun length(): Float = sqrt(x * x + y * y)

    fun lengthSquared(): Float = x * x + y * y

    fun normalized(): Point2D {
        val len = length()
        return if (len > 1e-5f) Point2D(x / len, y / len) else Point2D(1f, 0f)
    }

    /**
     * Counter-clockwise perpendicular normal vector (-y, x).
     */
    fun normal(): Point2D = Point2D(-y, x)

    /**
     * Clockwise perpendicular normal vector (y, -x).
     */
    fun normalCW(): Point2D = Point2D(y, -x)

    fun dot(other: Point2D): Float = x * other.x + y * other.y

    fun cross(other: Point2D): Float = x * other.y - y * other.x

    fun angleTo(other: Point2D): Float {
        val rad = atan2(other.y - y, other.x - x)
        var deg = Math.toDegrees(rad.toDouble()).toFloat()
        if (deg < 0) deg += 360f
        return deg
    }

    fun rotate(degrees: Float): Point2D {
        val rad = Math.toRadians(degrees.toDouble())
        val cosA = cos(rad).toFloat()
        val sinA = sin(rad).toFloat()
        return Point2D(x * cosA - y * sinA, x * sinA + y * cosA)
    }

    fun rotateAround(center: Point2D, degrees: Float): Point2D =
        (this - center).rotate(degrees) + center

    companion object {
        val Zero = Point2D(0f, 0f)
    }
}

/**
 * 2D Line Segment between [start] and [end].
 */
data class LineSegment(
    val start: Point2D,
    val end: Point2D
) {
    val length: Float get() = start.distanceTo(end)

    val vector: Point2D get() = end - start

    val direction: Point2D get() = vector.normalized()

    val normal: Point2D get() = direction.normal()

    val normalCW: Point2D get() = direction.normalCW()

    val midpoint: Point2D get() = Point2D((start.x + end.x) * 0.5f, (start.y + end.y) * 0.5f)

    val angleDegrees: Float get() {
        val rad = atan2(end.y - start.y, end.x - start.x)
        var deg = Math.toDegrees(rad.toDouble()).toFloat()
        if (deg < 0) deg += 360f
        return deg
    }

    /**
     * Calculates the projection parameter t in [0, 1] of point [p] along this segment.
     * 0.0 corresponds to [start], 1.0 corresponds to [end].
     */
    fun parameterOf(p: Point2D): Float {
        val segVec = end - start
        val segLenSq = segVec.lengthSquared()
        if (segLenSq < 1e-5f) return 0.5f
        return ((p - start).dot(segVec) / segLenSq).coerceIn(0f, 1f)
    }

    /**
     * Interpolates a point along this segment at parameter [t].
     */
    fun pointAt(t: Float): Point2D = start + (end - start) * t

    /**
     * Finds the closest point on this finite segment to [p].
     */
    fun closestPointTo(p: Point2D): Point2D {
        val segVec = end - start
        val segLenSq = segVec.lengthSquared()
        if (segLenSq < 1e-5f) return start

        val t = ((p - start).dot(segVec) / segLenSq).coerceIn(0f, 1f)
        return start + segVec * t
    }

    /**
     * Projects point [p] onto the infinite line containing this segment.
     */
    fun projectPointOntoInfiniteLine(p: Point2D): Point2D {
        val segVec = end - start
        val segLenSq = segVec.lengthSquared()
        if (segLenSq < 1e-5f) return start

        val t = (p - start).dot(segVec) / segLenSq
        return start + segVec * t
    }

    fun distanceTo(p: Point2D): Float = p.distanceTo(closestPointTo(p))

    fun intersects(other: LineSegment): Boolean {
        val p = start
        val r = end - start
        val q = other.start
        val s = other.end - other.start

        val rCrossS = r.cross(s)
        val qMinusP = q - p

        if (abs(rCrossS) < 1e-5f) return false

        val t = qMinusP.cross(s) / rCrossS
        val u = qMinusP.cross(r) / rCrossS

        return t in 0f..1f && u in 0f..1f
    }

    fun intersectionPoint(other: LineSegment): Point2D? {
        val p = start
        val r = end - start
        val q = other.start
        val s = other.end - other.start

        val rCrossS = r.cross(s)
        if (abs(rCrossS) < 1e-5f) return null

        val qMinusP = q - p
        val t = qMinusP.cross(s) / rCrossS
        val u = qMinusP.cross(r) / rCrossS

        if (t in 0f..1f && u in 0f..1f) {
            return p + r * t
        }
        return null
    }

    /**
     * Intersects the infinite lines passing through this segment and [other].
     * Returns null only if lines are parallel or collinear.
     */
    fun infiniteLineIntersectionPoint(other: LineSegment): Point2D? {
        val p = start
        val r = end - start
        val q = other.start
        val s = other.end - other.start

        val rCrossS = r.cross(s)
        if (abs(rCrossS) < 1e-5f) return null // Parallel

        val qMinusP = q - p
        val t = qMinusP.cross(s) / rCrossS
        return p + r * t
    }

    /**
     * Returns an offset segment shifted parallel along its normal by [distanceCm].
     */
    fun parallelOffset(distanceCm: Float): LineSegment {
        val offsetVec = normal * distanceCm
        return LineSegment(start + offsetVec, end + offsetVec)
    }
}

data class BoundingBox(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float
) {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY
    val center: Point2D get() = Point2D((minX + maxX) * 0.5f, (minY + maxY) * 0.5f)

    fun contains(p: Point2D): Boolean = p.x in minX..maxX && p.y in minY..maxY

    fun expanded(padding: Float): BoundingBox =
        BoundingBox(minX - padding, minY - padding, maxX + padding, maxY + padding)

    fun union(other: BoundingBox): BoundingBox = BoundingBox(
        minOf(minX, other.minX),
        minOf(minY, other.minY),
        maxOf(maxX, other.maxX),
        maxOf(maxY, other.maxY)
    )
}

object PolygonMath {
    /**
     * Shoelace formula for polygon area in square centimeters (cm²).
     */
    fun calculateAreaCm2(vertices: List<Point2D>): Float {
        if (vertices.size < 3) return 0f
        var sum = 0f
        for (i in vertices.indices) {
            val curr = vertices[i]
            val next = vertices[(i + 1) % vertices.size]
            sum += (curr.x * next.y) - (next.x * curr.y)
        }
        return abs(sum) * 0.5f
    }

    fun calculatePerimeterCm(vertices: List<Point2D>): Float {
        if (vertices.size < 2) return 0f
        var perimeter = 0f
        for (i in vertices.indices) {
            val curr = vertices[i]
            val next = vertices[(i + 1) % vertices.size]
            perimeter += curr.distanceTo(next)
        }
        return perimeter
    }

    fun calculateCentroid(vertices: List<Point2D>): Point2D {
        if (vertices.isEmpty()) return Point2D.Zero
        if (vertices.size == 1) return vertices[0]
        if (vertices.size == 2) return Point2D((vertices[0].x + vertices[1].x) * 0.5f, (vertices[0].y + vertices[1].y) * 0.5f)

        var cx = 0f
        var cy = 0f
        var signedArea = 0f

        for (i in vertices.indices) {
            val curr = vertices[i]
            val next = vertices[(i + 1) % vertices.size]
            val cross = (curr.x * next.y) - (next.x * curr.y)
            signedArea += cross
            cx += (curr.x + next.x) * cross
            cy += (curr.y + next.y) * cross
        }

        signedArea *= 0.5f
        if (abs(signedArea) < 1e-4f) {
            val avgX = vertices.map { it.x }.average().toFloat()
            val avgY = vertices.map { it.y }.average().toFloat()
            return Point2D(avgX, avgY)
        }

        cx /= (6f * signedArea)
        cy /= (6f * signedArea)
        return Point2D(cx, cy)
    }

    /**
     * Ray-casting algorithm for Point-in-Polygon test.
     */
    fun isPointInside(point: Point2D, vertices: List<Point2D>): Boolean {
        if (vertices.size < 3) return false
        var inside = false
        var j = vertices.size - 1
        for (i in vertices.indices) {
            val vi = vertices[i]
            val vj = vertices[j]
            if ((vi.y > point.y) != (vj.y > point.y) &&
                (point.x < (vj.x - vi.x) * (point.y - vi.y) / (vj.y - vi.y) + vi.x)
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }
}
