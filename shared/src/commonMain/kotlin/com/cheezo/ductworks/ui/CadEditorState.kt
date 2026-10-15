package com.cheezo.ductworks.ui

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import com.cheezo.ductworks.engine.CadEngine
import com.cheezo.ductworks.engine.threed.WallHeightMode3D
import com.cheezo.ductworks.model.*

enum class ViewMode(val titleRu: String) {
    MODE_2D("2D План"),
    MODE_3D("3D Вид")
}

enum class LeftNavTab(val titleRu: String, val icon: String) {
    ROOMS("Комнаты", "◻"),
    OPENINGS("Проемы", "🚪"),
    FURNITURE("Мебель", "🛋️"),
    TOOLS("Чертёж", "📐")
}

enum class CadTool(val titleRu: String, val hotkeyHint: String) {
    SELECT("Указатель / Выбор", "V"),
    DRAW_WALL("Чертить стены", "W"),
    RECTANGLE_ROOM("Комната 4×3м", "R"),
    L_SHAPED_ROOM("L-комната", "L"),
    SPLIT_WALL("Разрезать стену", "S"),
    MEASURE("Рулетка / Замер", "M"),
    DELETE("Удалить", "Del")
}

enum class MeasurementUnit(val symbol: String, val divisor: Float, val formatPattern: String) {
    METERS("м", 100f, "%.2f м"),
    CENTIMETERS("см", 1f, "%.0f см"),
    MILLIMETERS("мм", 0.1f, "%.0f мм")
}

sealed class DragSession {
    object None : DragSession()
    data class Vertex(val vertexId: String, val startPointer: Point2D) : DragSession()
    data class Wall(val wallId: String, val startPointer: Point2D) : DragSession()
    data class RoomDrag(val roomId: String, val startPointer: Point2D) : DragSession()
    data class Item(val itemId: String, val startPointer: Point2D) : DragSession()
    data class RotateItem(val itemId: String, val centerWorld: Point2D) : DragSession()
}

@Stable
class CadEditorState(
    val engine: CadEngine = CadEngine()
) {
    var document by mutableStateOf(engine.currentDocument)
        private set

    init {
        document = engine.ensurePlanarGraph()
    }

    var currentTool by mutableStateOf(CadTool.SELECT)

    // View Mode (2D CAD Floor Plan vs 3D Architectural Scene)
    var viewMode by mutableStateOf(ViewMode.MODE_2D)
    var wallHeightMode3D by mutableStateOf(WallHeightMode3D.FULL_HEIGHT)

    // Unified Left Navigation State
    var activeLeftNavTab by mutableStateOf<LeftNavTab?>(LeftNavTab.ROOMS)
    var isLeftDrawerOpen by mutableStateOf(true)
    var leftNavWidthPx by mutableStateOf(0)
    var rightSidebarWidthPx by mutableStateOf(0)

    fun toggleLeftNavTab(tab: LeftNavTab) {
        if (activeLeftNavTab == tab) {
            isLeftDrawerOpen = !isLeftDrawerOpen
        } else {
            activeLeftNavTab = tab
            isLeftDrawerOpen = true
        }
    }

    // Selection
    var selectedWallId by mutableStateOf<String?>(null)
    var selectedVertexId by mutableStateOf<String?>(null)
    var selectedRoomId by mutableStateOf<String?>(null)
    var selectedItemId by mutableStateOf<String?>(null)

    // Hover
    var hoveredWallId by mutableStateOf<String?>(null)
    var hoveredVertexId by mutableStateOf<String?>(null)
    var hoveredRoomId by mutableStateOf<String?>(null)
    var hoveredItemId by mutableStateOf<String?>(null)

    // Catalog state (Planner 5D Left Sidebar)
    var isCatalogOpen by mutableStateOf(true)
    var defaultWallHeightCm by mutableStateOf(270f)
    var activeCatalogCategory by mutableStateOf(CatalogCategory.DOORS_WINDOWS)
    var catalogSearchQuery by mutableStateOf("")

    // Active Drag Session
    var activeDragSession by mutableStateOf<DragSession>(DragSession.None)
        private set

    // Wall drawing state
    var drawingStartPoint by mutableStateOf<Point2D?>(null)
    var drawingStartVertexId by mutableStateOf<String?>(null)
    var initialChainVertexId by mutableStateOf<String?>(null)
    var initialChainPoint by mutableStateOf<Point2D?>(null)
    var currentCursorWorldPoint by mutableStateOf<Point2D?>(null)
    var activeSnap by mutableStateOf<SnapResult?>(null)

    val activeItemAlignGuides: List<com.cheezo.ductworks.engine.cad.InteriorPlanningEngine.SmartAlignGuide>
        get() = engine.activeItemAlignGuides

    // Measure state
    var measureStartPoint by mutableStateOf<Point2D?>(null)
    var measureEndPoint by mutableStateOf<Point2D?>(null)

    // Toggles
    var isGridVisible by mutableStateOf(true)
    var isGridSnapEnabled by mutableStateOf(true)
    var isOrthoEnabled by mutableStateOf(true)
    var showDimensions by mutableStateOf(true)
    var showRoomAreas by mutableStateOf(true)
    var showClearRoomDimensions by mutableStateOf(true)
    var showOpeningDimensionChains by mutableStateOf(true)
    var showFurnitureDistanceGuides by mutableStateOf(true)
    var showErgonomicsClearance by mutableStateOf(true)
    var showWallHatching by mutableStateOf(true)
    var showFloorTextures by mutableStateOf(true)
    var isPropertiesSidebarOpen by mutableStateOf(true)
    var gridStepCm by mutableStateOf(20f)
    var defaultWallThicknessCm by mutableStateOf(20f)
    var defaultWallType by mutableStateOf(WallType.INTERIOR)
    var measurementUnit by mutableStateOf(MeasurementUnit.MILLIMETERS)
    var dimensionTerminator by mutableStateOf(com.cheezo.ductworks.engine.cad.GostDimensionTerminator.ARROWS_GOST_20)

    val roomFloorMaterials = mutableStateMapOf<String, com.cheezo.ductworks.engine.cad.FloorPatternEngine.FloorMaterial>()

    val statusHint: String
        get() = when (currentTool) {
            CadTool.SELECT -> when {
                activeDragSession is DragSession.Wall -> "Тяните стену — смежные стены растягиваются ортогонально (Planner 5D)"
                activeDragSession is DragSession.Vertex -> "Тяните угол для изменения геометрии. Включена привязка 90° и выравнивание."
                selectedWallId != null -> "Стена выбрана. Тяните за тело стены для смещения, или за круглые концы для растяжки."
                selectedRoomId != null -> "Помещение выбрано. Тяните за центр для перемещения всей комнаты."
                else -> "Кликните или потяните стену, угол или комнату. Колесо мыши — зум."
            }
            CadTool.DRAW_WALL -> if (drawingStartPoint == null) {
                "Кликните ЛКМ для первой точки стены. Магнитная привязка активна."
            } else {
                "Кликните для следующего угла. Кликните на исходную точку для замыкания комнаты. Esc — выход."
            }
            CadTool.RECTANGLE_ROOM -> "Кликните в любом месте для вставки прямоугольной комнаты 4×3м."
            CadTool.L_SHAPED_ROOM -> "Кликните в любом месте для вставки L-образной комнаты."
            CadTool.SPLIT_WALL -> "Кликните по любой стене для разделения новым узлом."
            CadTool.MEASURE -> if (measureStartPoint == null) "Кликните первую точку замера" else "Кликните вторую точку замера"
            CadTool.DELETE -> "Кликните по стене или углу для удаления."
        }

    fun selectTool(tool: CadTool) {
        cancelCurrentOperation()
        currentTool = tool
    }

    fun clearSelection() {
        selectedWallId = null
        selectedVertexId = null
        selectedRoomId = null
        selectedItemId = null
    }

    fun selectWall(wallId: String) {
        clearSelection()
        selectedWallId = wallId
    }

    fun selectVertex(vertexId: String) {
        clearSelection()
        selectedVertexId = vertexId
    }

    fun selectRoom(roomId: String) {
        clearSelection()
        selectedRoomId = roomId
    }

    fun selectItem(itemId: String) {
        clearSelection()
        selectedItemId = itemId
    }

    fun cancelCurrentOperation() {
        if (activeDragSession !is DragSession.None) {
            document = engine.cancelDrag()
            activeDragSession = DragSession.None
        }
        drawingStartPoint = null
        drawingStartVertexId = null
        initialChainVertexId = null
        initialChainPoint = null
        measureStartPoint = null
        measureEndPoint = null
    }

    // --- Interactive Drag Lifecycle (60 FPS smooth dragging) ---

    fun startDragVertex(vertexId: String, currentPointerWorld: Point2D) {
        selectVertex(vertexId)
        engine.beginDrag()
        activeDragSession = DragSession.Vertex(vertexId, currentPointerWorld)
    }

    fun updateDragVertex(targetPos: Point2D) {
        val session = activeDragSession as? DragSession.Vertex ?: return
        document = engine.dragVertex(session.vertexId, targetPos)
    }

    fun startDragWall(wallId: String, currentPointerWorld: Point2D) {
        selectWall(wallId)
        engine.beginDrag()
        activeDragSession = DragSession.Wall(wallId, currentPointerWorld)
    }

    fun updateDragWall(currentPointerWorld: Point2D) {
        val session = activeDragSession as? DragSession.Wall ?: return
        val totalDelta = currentPointerWorld - session.startPointer
        document = engine.dragWall(session.wallId, totalDelta)
    }

    fun startDragRoom(roomId: String, currentPointerWorld: Point2D) {
        selectRoom(roomId)
        engine.beginDrag()
        activeDragSession = DragSession.RoomDrag(roomId, currentPointerWorld)
    }

    fun updateDragRoom(currentPointerWorld: Point2D) {
        val session = activeDragSession as? DragSession.RoomDrag ?: return
        val totalDelta = currentPointerWorld - session.startPointer
        document = engine.dragRoom(session.roomId, totalDelta)
    }

    fun startDragItem(itemId: String, currentPointerWorld: Point2D) {
        selectItem(itemId)
        engine.beginDrag()
        activeDragSession = DragSession.Item(itemId, currentPointerWorld)
    }

    fun updateDragItem(currentPointerWorld: Point2D) {
        val session = activeDragSession as? DragSession.Item ?: return
        val totalDelta = currentPointerWorld - session.startPointer
        document = engine.dragPlacedItem(session.itemId, totalDelta)
    }

    fun startRotateItem(itemId: String, centerWorld: Point2D) {
        selectItem(itemId)
        engine.beginDrag()
        activeDragSession = DragSession.RotateItem(itemId, centerWorld)
    }

    fun updateRotateItem(angleDegrees: Float) {
        val session = activeDragSession as? DragSession.RotateItem ?: return
        document = engine.rotatePlacedItemLive(session.itemId, angleDegrees)
    }

    fun addCatalogItemAt(catalogItem: CatalogItem, position: Point2D) {
        val item = PlacedItem.fromCatalog(catalogItem, position)
        document = engine.addPlacedItem(item)
        selectItem(item.id)
    }

    fun rotateSelectedItem(degrees: Float = 90f) {
        val id = selectedItemId ?: return
        document = engine.rotatePlacedItem(id, degrees)
    }

    var clipboardItem: PlacedItem? by mutableStateOf(null)

    fun copySelected() {
        val id = selectedItemId ?: return
        clipboardItem = document.items[id]
    }

    fun paste() {
        val item = clipboardItem ?: return
        val targetPos = currentCursorWorldPoint ?: (item.position + Point2D(30f, 30f))
        val newId = WallVertex.generateId("item")
        val pasted = item.copy(
            id = newId,
            position = targetPos
        )
        document = engine.addPlacedItem(pasted)
        selectItem(newId)
    }

    fun duplicateSelectedItem() {
        val id = selectedItemId ?: return
        val item = document.items[id] ?: return
        val newId = WallVertex.generateId("item")
        val duplicated = item.copy(
            id = newId,
            position = item.position + Point2D(30f, 30f)
        )
        document = engine.addPlacedItem(duplicated)
        selectItem(newId)
    }

    fun deleteSelectedItem() {
        val id = selectedItemId ?: return
        document = engine.deletePlacedItem(id)
        selectedItemId = null
    }

    fun flipSelectedItemHorizontal() {
        val id = selectedItemId ?: return
        val item = document.items[id] ?: return
        document = engine.updatePlacedItem(id, flipH = !item.flipHorizontal)
    }

    fun flipSelectedItemVertical() {
        val id = selectedItemId ?: return
        val item = document.items[id] ?: return
        document = engine.updatePlacedItem(id, flipV = !item.flipVertical)
    }

    fun bringSelectedItemToFront() {
        val id = selectedItemId ?: return
        val maxZ = document.items.values.maxOfOrNull { it.zIndex } ?: 0
        document = engine.updatePlacedItem(id, zIndex = maxZ + 1)
    }

    fun sendSelectedItemToBack() {
        val id = selectedItemId ?: return
        val minZ = document.items.values.minOfOrNull { it.zIndex } ?: 0
        document = engine.updatePlacedItem(id, zIndex = minZ - 1)
    }

    fun moveSelectedItemLayerUp() {
        val id = selectedItemId ?: return
        val item = document.items[id] ?: return
        document = engine.updatePlacedItem(id, zIndex = item.zIndex + 1)
    }

    fun moveSelectedItemLayerDown() {
        val id = selectedItemId ?: return
        val item = document.items[id] ?: return
        document = engine.updatePlacedItem(id, zIndex = item.zIndex - 1)
    }

    fun updateSelectedItemDimensions(
        widthCm: Float? = null,
        depthCm: Float? = null,
        heightCm: Float? = null,
        elevationCm: Float? = null
    ) {
        val id = selectedItemId ?: return
        document = engine.updatePlacedItem(
            itemId = id,
            widthCm = widthCm,
            depthCm = depthCm,
            heightCm = heightCm,
            elevationCm = elevationCm
        )
    }

    fun addCustomRectangularRoom(center: Point2D, widthCm: Float, heightCm: Float, name: String) {
        document = engine.addRectangularRoom(
            center = center,
            widthCm = widthCm,
            heightCm = heightCm,
            thicknessCm = defaultWallThicknessCm,
            wallType = defaultWallType,
            name = name
        )
        currentTool = CadTool.SELECT
    }

    fun addFurnishedRoom(
        center: Point2D,
        widthCm: Float,
        heightCm: Float,
        name: String,
        interiorCatalogIds: List<Pair<String, Point2D>> = emptyList()
    ) {
        document = engine.addRectangularRoom(
            center = center,
            widthCm = widthCm,
            heightCm = heightCm,
            thicknessCm = defaultWallThicknessCm,
            wallType = defaultWallType,
            name = name
        )
        for ((catId, offset) in interiorCatalogIds) {
            val catItem = CatalogItem.ALL_ITEMS.firstOrNull { it.id == catId } ?: continue
            val itemPos = center + offset
            val placed = PlacedItem.fromCatalog(catItem, itemPos)
            document = engine.addPlacedItem(placed)
        }
        currentTool = CadTool.SELECT
    }

    fun endDrag() {
        if (activeDragSession !is DragSession.None) {
            document = engine.commitDrag()
            activeDragSession = DragSession.None
        }
    }

    // --- Actions ---

    fun undo() {
        cancelCurrentOperation()
        document = engine.undo()
        validateSelections()
    }

    fun redo() {
        cancelCurrentOperation()
        document = engine.redo()
        validateSelections()
    }

    fun resetPlan() {
        cancelCurrentOperation()
        clearSelection()
        engine.reset()
        document = engine.currentDocument
    }

    fun loadSamplePlan() {
        cancelCurrentOperation()
        clearSelection()
        engine.reset(PlanDocument.createSamplePlan())
        document = engine.currentDocument
    }

    fun addWallPoint(point: Point2D, vertexId: String? = null) {
        val startPt = drawingStartPoint
        val startVId = drawingStartVertexId

        if (startPt == null) {
            drawingStartPoint = point
            drawingStartVertexId = vertexId
            initialChainPoint = point
            initialChainVertexId = vertexId
            return
        }

        // Check if user clicked on the same spot (double-click finish)
        if (startPt.distanceTo(point) < 8f) {
            cancelCurrentOperation()
            return
        }

        val initPt = initialChainPoint ?: startPt
        val isClosingToStart = (vertexId != null && vertexId == initialChainVertexId) ||
                (initialChainVertexId != null && vertexId == null && point.distanceTo(initPt) < maxOf(35f, defaultWallThicknessCm * 1.5f)) ||
                (initialChainVertexId == null && point.distanceTo(initPt) < maxOf(35f, defaultWallThicknessCm * 1.5f))

        val effectivePoint = if (isClosingToStart) initPt else point
        val effectiveEndVertexId = if (isClosingToStart) initialChainVertexId else vertexId

        val oldRoomCount = document.rooms.size

        document = engine.addWall(
            pStart = startPt,
            pEnd = effectivePoint,
            thicknessCm = defaultWallThicknessCm,
            wallType = defaultWallType,
            existingStartVertexId = startVId,
            existingEndVertexId = effectiveEndVertexId
        )

        // Capture initialChainVertexId from the created wall if this was the first wall of the chain
        if (initialChainVertexId == null) {
            val startVertex = engine.findNearestVertex(initPt, 25f)
            if (startVertex != null) {
                initialChainVertexId = startVertex.id
            }
        }

        val roomCountIncreased = document.rooms.size > oldRoomCount

        // Did we connect back to the start of this polyline chain, or did this wall close an enclosed room?
        if (isClosingToStart || roomCountIncreased) {
            cancelCurrentOperation()
            selectTool(CadTool.SELECT)
            return
        }

        // Continue polyline chain seamlessly from this new endpoint
        val endVertexId = effectiveEndVertexId ?: engine.findNearestVertex(effectivePoint, 25f)?.id
        drawingStartPoint = effectivePoint
        drawingStartVertexId = endVertexId
    }

    fun splitWallAt(wallId: String, point: Point2D) {
        document = engine.splitWall(wallId, point)
        clearSelection()
    }

    fun deleteSelected() {
        val wallId = selectedWallId
        val vertexId = selectedVertexId
        val itemId = selectedItemId
        when {
            itemId != null -> {
                document = engine.deletePlacedItem(itemId)
                selectedItemId = null
            }
            wallId != null -> {
                document = engine.deleteWall(wallId)
                selectedWallId = null
            }
            vertexId != null -> {
                document = engine.deleteVertex(vertexId)
                selectedVertexId = null
            }
        }
    }

    fun deleteItemAt(point: Point2D) {
        val nearbyVertex = engine.findNearestVertex(point, 20f)
        if (nearbyVertex != null) {
            document = engine.deleteVertex(nearbyVertex.id)
            return
        }
        val nearbyWall = engine.findNearestWall(point, 20f)
        if (nearbyWall != null) {
            document = engine.deleteWall(nearbyWall.first.id)
        }
    }

    fun addRectangleRoomAt(center: Point2D) {
        document = engine.addRectangularRoom(
            center = center,
            widthCm = 400f,
            heightCm = 300f,
            thicknessCm = defaultWallThicknessCm,
            wallType = defaultWallType
        )
        currentTool = CadTool.SELECT
    }

    fun addLShapedRoomAt(center: Point2D) {
        document = engine.addLShapedRoom(center)
        currentTool = CadTool.SELECT
    }

    fun resizeSelectedWall(newLengthCm: Float) {
        val wallId = selectedWallId ?: return
        document = engine.resizeWallLength(wallId, newLengthCm)
    }

    fun updateWallThickness(wallId: String, thicknessCm: Float) {
        document = engine.updateWall(wallId, thicknessCm = thicknessCm)
    }

    fun updateWallHeight(wallId: String, heightCm: Float) {
        document = engine.updateWall(wallId, heightCm = heightCm)
    }

    fun setAllWallsHeight(heightCm: Float) {
        defaultWallHeightCm = heightCm
        document = engine.updateAllWallsHeight(heightCm)
    }

    fun updateWallType(wallId: String, type: WallType) {
        document = engine.updateWall(wallId, wallType = type, thicknessCm = type.defaultThicknessCm)
    }

    fun updateRoomProperties(roomId: String, name: String, type: RoomType, ceilingHeightCm: Float) {
        document = engine.updateRoom(roomId, name = name, roomType = type, ceilingHeightCm = ceilingHeightCm)
    }

    private fun validateSelections() {
        if (selectedWallId != null && selectedWallId !in document.walls) {
            selectedWallId = null
        }
        if (selectedVertexId != null && selectedVertexId !in document.vertices) {
            selectedVertexId = null
        }
        if (selectedRoomId != null && document.rooms.none { it.id == selectedRoomId }) {
            selectedRoomId = null
        }
        if (selectedItemId != null && selectedItemId !in document.items) {
            selectedItemId = null
        }
    }

    fun formatLength(lengthCm: Float): String = when (measurementUnit) {
        MeasurementUnit.METERS -> {
            val meters = lengthCm / 100f
            "${(kotlin.math.round(meters * 100) / 100f)} м"
        }
        MeasurementUnit.CENTIMETERS -> "${lengthCm.toInt()} см"
        MeasurementUnit.MILLIMETERS -> "${(lengthCm * 10).toInt()} мм"
    }

    fun formatArea(areaM2: Float): String =
        "${(kotlin.math.round(areaM2 * 100) / 100f)} м²"

    fun formatVolume(volumeM3: Float): String =
        "${(kotlin.math.round(volumeM3 * 100) / 100f)} м³"
}
