package com.cheezo.ductworks.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.cheezo.ductworks.ui.canvas.CadCanvas
import com.cheezo.ductworks.ui.components.CadBottomStatusBar
import com.cheezo.ductworks.ui.components.CadPropertiesSidebar
import com.cheezo.ductworks.ui.components.CadTopBar
import com.cheezo.ductworks.ui.components.UnifiedLeftNav
import com.cheezo.ductworks.ui.threed.CadViewport3D
import com.cheezo.ductworks.ui.threed.KorenderCameraState

@Composable
fun CadScreen(
    modifier: Modifier = Modifier
) {
    val editorState = remember { CadEditorState() }
    val viewport = remember { CadViewportState(initialZoom = 0.8f) }
    val camera3DState = remember { KorenderCameraState() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val focusRequester = remember { FocusRequester() }

    // Fit to screen helper
    val fitToScreen: () -> Unit = {
        if (canvasSize.width > 0 && canvasSize.height > 0) {
            val bounds = editorState.document.getBoundingBox(padding = 120f)
            viewport.fitToBounds(
                box = bounds,
                screenWidth = canvasSize.width.toFloat(),
                screenHeight = canvasSize.height.toFloat()
            )
        }
    }

    // Initial center on launch
    LaunchedEffect(canvasSize) {
        if (canvasSize.width > 0 && canvasSize.height > 0 && viewport.panOffset == Offset.Zero) {
            fitToScreen()
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFFFFFFF))
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.Escape -> {
                            editorState.cancelCurrentOperation()
                            editorState.clearSelection()
                            editorState.selectTool(CadTool.SELECT)
                            true
                        }
                        Key.Delete, Key.Backspace -> {
                            editorState.deleteSelected()
                            true
                        }
                        Key.C -> {
                            if (keyEvent.isCtrlPressed || keyEvent.isMetaPressed) {
                                editorState.copySelected()
                                true
                            } else false
                        }
                        Key.V -> {
                            if (keyEvent.isCtrlPressed || keyEvent.isMetaPressed) {
                                editorState.paste()
                                true
                            } else {
                                editorState.selectTool(CadTool.SELECT)
                                true
                            }
                        }
                        Key.W -> {
                            editorState.selectTool(CadTool.DRAW_WALL)
                            true
                        }
                        Key.R -> {
                            editorState.selectTool(CadTool.RECTANGLE_ROOM)
                            true
                        }
                        Key.L -> {
                            editorState.selectTool(CadTool.L_SHAPED_ROOM)
                            true
                        }
                        Key.S -> {
                            editorState.selectTool(CadTool.SPLIT_WALL)
                            true
                        }
                        Key.M -> {
                            editorState.selectTool(CadTool.MEASURE)
                            true
                        }
                        Key.Z -> {
                            if (keyEvent.isCtrlPressed || keyEvent.isMetaPressed) {
                                if (keyEvent.isShiftPressed) {
                                    editorState.redo()
                                } else {
                                    editorState.undo()
                                }
                                true
                            } else false
                        }
                        Key.Y -> {
                            if (keyEvent.isCtrlPressed || keyEvent.isMetaPressed) {
                                editorState.redo()
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // Top Toolbar
        CadTopBar(
            editorState = editorState,
            viewport = viewport,
            onFitToScreen = fitToScreen
        )

        // Middle workspace: Canvas fills full area in background; sidebars float as overlays without shifting the canvas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // 1. Center Interactive Canvas (Fills entire workspace - NEVER shifted by open sidebars)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .onSizeChanged { canvasSize = it }
            ) {
                when (editorState.viewMode) {
                    ViewMode.MODE_2D -> {
                        CadCanvas(
                            editorState = editorState,
                            viewport = viewport
                        )
                    }
                    ViewMode.MODE_3D -> {
                        CadViewport3D(
                            editorState = editorState,
                            cameraState = camera3DState
                        )
                    }
                }
            }

            // 2. Left Menu Overlay (UnifiedLeftNav anchored to Start)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .onSizeChanged { editorState.leftNavWidthPx = it.width }
            ) {
                UnifiedLeftNav(
                    editorState = editorState,
                    getCanvasCenterWorld = {
                        val cx = if (canvasSize.width > 0) canvasSize.width * 0.5f else 500f
                        val cy = if (canvasSize.height > 0) canvasSize.height * 0.5f else 400f
                        viewport.screenToWorld(Offset(cx, cy))
                    }
                )
            }

            // 3. Right Properties Sidebar Overlay (Smooth slide-in / slide-out animation like left drawer)
            androidx.compose.animation.AnimatedVisibility(
                visible = editorState.isPropertiesSidebarOpen,
                enter = androidx.compose.animation.slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth },
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 280, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                ) + androidx.compose.animation.fadeIn(
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 280)
                ),
                exit = androidx.compose.animation.slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth },
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 220, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                ) + androidx.compose.animation.fadeOut(
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 220)
                ),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .onSizeChanged { editorState.rightSidebarWidthPx = it.width }
            ) {
                CadPropertiesSidebar(
                    editorState = editorState
                )
            }
        }

        // Bottom Status Bar
        CadBottomStatusBar(
            editorState = editorState,
            viewport = viewport,
            camera3DState = camera3DState
        )
    }
}
