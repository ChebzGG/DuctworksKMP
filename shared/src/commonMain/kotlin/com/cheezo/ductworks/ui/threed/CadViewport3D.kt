package com.cheezo.ductworks.ui.threed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.*
import com.cheezo.ductworks.ui.CadEditorState
import com.zakgof.korender.Korender
import com.zakgof.korender.TouchEvent
import com.zakgof.korender.math.*

/**
 * State-of-the-Art Korender 3D Architectural Viewport.
 * Powered by hardware-accelerated OpenGL (Desktop) & OpenGL ES (Android):
 * - True hardware Z-buffer: zero visual clipping through walls, 100% depth accuracy
 * - Physically Based Rendering (PBR) studio lighting with warm sun and sky bounce
 * - Physical wall cutouts for doors & windows with translucent glass
 * - Multi-component Scandinavian designer sofa, coffee table with lower shelf, and full catalog
 * - Smooth orbit navigation (drag LMB), pan (drag RMB or Shift+LMB), and zoom (wheel / buttons)
 */
@Composable
fun CadViewport3D(
    editorState: CadEditorState,
    cameraState: KorenderCameraState = remember { KorenderCameraState() },
    modifier: Modifier = Modifier
) {
    // Auto-frame camera on building geometry on start or document change
    LaunchedEffect(editorState.document) {
        val bounds = editorState.document.getBoundingBox()
        val cx = bounds.center.x / 100f
        val cz = bounds.center.y / 100f
        val span = (maxOf(bounds.width, bounds.height, 450f) / 100f)
        cameraState.frameBuilding(cx, cz, span)
    }

    // Hook native desktop AWT mouse event listener (bypasses SwingPanel GLCanvas event consumption)
    AttachViewport3DInput(cameraState)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF1F5F9))
            .clipToBounds()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()

                        // 1. Колесико мыши: масштабирование (зум)
                        for (change in event.changes) {
                            val scrollDelta = change.scrollDelta
                            if (scrollDelta.y != 0f) {
                                val zoomFactor = if (scrollDelta.y < 0) 0.87f else 1.15f
                                cameraState.zoom(zoomFactor)
                                change.consume()
                            }
                        }

                        // 2. Многопальцевые жесты (мобильные устройства / тачпад)
                        if (event.changes.size >= 2) {
                            val p0 = event.changes[0]
                            val p1 = event.changes[1]
                            if (p0.pressed && p1.pressed) {
                                val prevDist = (p0.previousPosition - p1.previousPosition).getDistance()
                                val currDist = (p0.position - p1.position).getDistance()
                                if (prevDist > 10f && currDist > 10f) {
                                    val pinchScale = currDist / prevDist
                                    if (kotlin.math.abs(pinchScale - 1f) > 0.005f) {
                                        cameraState.zoom(1f / pinchScale)
                                    }
                                }
                                val d0 = p0.position - p0.previousPosition
                                val d1 = p1.position - p1.previousPosition
                                val avgDelta = (d0 + d1) * 0.5f
                                if (avgDelta != androidx.compose.ui.geometry.Offset.Zero) {
                                    cameraState.pan(avgDelta.x, avgDelta.y)
                                }
                                p0.consume()
                                p1.consume()
                            }
                            continue
                        }

                        // 3. Одиночный указатель (мышь или один палец)
                        val primary = event.changes.firstOrNull() ?: continue
                        if (primary.pressed) {
                            val delta = primary.position - primary.previousPosition
                            if (delta != androidx.compose.ui.geometry.Offset.Zero) {
                                // СКМ (Tertiary): перемещение камеры (Pan)
                                if (event.buttons.isTertiaryPressed) {
                                    cameraState.pan(delta.x, delta.y)
                                } else {
                                    // ЛКМ, ПКМ или касание одним пальцем: вращение камеры (Orbit)
                                    cameraState.orbit(delta.x, delta.y)
                                }
                            }
                            primary.consume()
                        }
                    }
                }
            }
    ) {
        Korender {
            background = ColorRGBA(0.94f, 0.95f, 0.97f, 1f)

            Frame {
                // Setup Camera & Near-Plane Precision Projection (near = 10cm)
                with(cameraState) {
                    applyCamera()
                }

                // Studio Lighting (Warm Key Sun with PCF Shadows + Sky Fill + Ambient)
                DirectionalLight(
                    direction = Vec3(0.55f, -1.10f, -0.65f).normalize(),
                    color = ColorRGB(1.05f, 1.02f, 0.98f) * 1.25f
                ) {
                    Cascade(
                        mapSize = 2048,
                        near = 0.1f,
                        far = 50f,
                        algorithm = hardwarePcf()
                    )
                }
                DirectionalLight(
                    direction = Vec3(-0.65f, -0.65f, 0.55f).normalize(),
                    color = ColorRGB(0.72f, 0.80f, 0.92f) * 1.10f
                )
                AmbientLight(ColorRGB.White * 0.45f)

                // Render Architectural Envelope, Cutaway Walls, Doors, Windows, and Furniture
                with(KorenderArchitectureBuilder) {
                    renderScene(
                        document = editorState.document,
                        heightMode = editorState.wallHeightMode3D,
                        selectedId = editorState.selectedItemId
                    )
                }
            }
        }
    }
}
