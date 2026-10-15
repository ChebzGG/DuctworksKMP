package com.cheezo.ductworks.model

import kotlin.math.roundToInt

enum class RoomType(
    val titleRu: String,
    val defaultColorHex: Long,
    val recommendedAirExchangeRatePerHour: Float // Required air changes per hour for Phase 2 ventilation
) {
    LIVING_ROOM("Гостиная", 0xFFEBF3FB, 1.0f),
    BEDROOM("Спальня", 0xFFF1F8E9, 1.0f),
    KITCHEN("Кухня", 0xFFFFF8E1, 3.0f),
    BATHROOM("Санузел / Ванная", 0xFFE0F7FA, 4.0f),
    CORRIDOR("Прихожая / Коридор", 0xFFF5F5F5, 0.8f),
    OFFICE("Кабинет", 0xFFEDE7F6, 1.2f),
    STORAGE("Кладовая / Тех.зона", 0xFFECEFF1, 0.5f),
    CUSTOM("Помещение", 0xFFF3E5F5, 1.0f)
}

/**
 * A detected or user-defined room enclosed by walls.
 */
data class Room(
    val id: String = WallVertex.generateId("room"),
    val name: String = "Комната",
    val roomType: RoomType = RoomType.CUSTOM,
    val vertexIds: List<String>,
    val ceilingHeightCm: Float = 270f,
    val customColorHex: Long? = null
) {
    fun getPolygonPoints(vertexMap: Map<String, WallVertex>): List<Point2D> =
        vertexIds.mapNotNull { vertexMap[it]?.position }

    fun calculateAreaCm2(vertexMap: Map<String, WallVertex>): Float =
        PolygonMath.calculateAreaCm2(getPolygonPoints(vertexMap))

    fun calculateAreaM2(vertexMap: Map<String, WallVertex>): Float =
        calculateAreaCm2(vertexMap) / 10000f

    fun calculatePerimeterM(vertexMap: Map<String, WallVertex>): Float =
        PolygonMath.calculatePerimeterCm(getPolygonPoints(vertexMap)) / 100f

    fun calculateCentroid(vertexMap: Map<String, WallVertex>): Point2D =
        PolygonMath.calculateCentroid(getPolygonPoints(vertexMap))

    /**
     * Volume in cubic meters (m³). Essential for HVAC air physics.
     */
    fun calculateVolumeM3(vertexMap: Map<String, WallVertex>): Float =
        calculateAreaM2(vertexMap) * (ceilingHeightCm / 100f)

    /**
     * Required fresh airflow in m³/h based on room type and volume (Phase 2 readiness).
     */
    fun calculateRequiredAirflowM3h(vertexMap: Map<String, WallVertex>): Float {
        val volume = calculateVolumeM3(vertexMap)
        return (volume * roomType.recommendedAirExchangeRatePerHour * 10f).roundToInt() / 10f
    }
}
