package com.cheezo.ductworks.ui.threed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import java.awt.AWTEvent
import java.awt.Point
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.MouseEvent

/**
 * Desktop JVM implementation:
 * Korender on Desktop embeds an LWJGL GLCanvas inside SwingPanel.
 * As a native heavyweight AWT component, GLCanvas receives and handles
 * OS mouse events before ComposeScene can see them.
 *
 * This hook registers a global AWTEventListener while CadViewport3D is mounted,
 * intercepting mouse drags on the 3D canvas:
 * - LMB drag -> Orbit camera
 * - RMB drag -> Orbit camera
 * - MMB drag -> Pan camera (canvas movement)
 */
@Composable
actual fun AttachViewport3DInput(cameraState: KorenderCameraState) {
    DisposableEffect(cameraState) {
        var activeDragButton = 0 // 1: LMB, 2: MMB, 3: RMB
        var lastPoint: Point? = null

        fun isViewportComponent(comp: java.awt.Component?): Boolean {
            if (comp == null) return false
            var current: java.awt.Component? = comp
            while (current != null) {
                val name = current.javaClass.name
                if (name.contains("canvas", ignoreCase = true) ||
                    name.contains("korender", ignoreCase = true) ||
                    name.contains("GLCanvas", ignoreCase = true) ||
                    current is java.awt.Canvas
                ) {
                    return true
                }
                current = current.parent
            }
            return false
        }

        val listener = AWTEventListener { event ->
            if (event !is MouseEvent) return@AWTEventListener
            val comp = event.component

            when (event.id) {
                MouseEvent.MOUSE_PRESSED -> {
                    if (isViewportComponent(comp)) {
                        activeDragButton = event.button
                        lastPoint = event.locationOnScreen
                    }
                }

                MouseEvent.MOUSE_DRAGGED -> {
                    // Fallback in case press was received before state initialized
                    if (activeDragButton == 0 && isViewportComponent(comp)) {
                        val mods = event.modifiersEx
                        activeDragButton = when {
                            (mods and MouseEvent.BUTTON2_DOWN_MASK) != 0 -> MouseEvent.BUTTON2
                            (mods and MouseEvent.BUTTON3_DOWN_MASK) != 0 -> MouseEvent.BUTTON3
                            (mods and MouseEvent.BUTTON1_DOWN_MASK) != 0 -> MouseEvent.BUTTON1
                            else -> 0
                        }
                        lastPoint = event.locationOnScreen
                    }

                    if (activeDragButton != 0 && lastPoint != null) {
                        val current = event.locationOnScreen ?: return@AWTEventListener
                        val dx = (current.x - lastPoint!!.x).toFloat()
                        val dy = (current.y - lastPoint!!.y).toFloat()
                        lastPoint = current

                        when (activeDragButton) {
                            MouseEvent.BUTTON1, MouseEvent.BUTTON3 -> {
                                // LMB or RMB: Orbit camera
                                cameraState.orbit(dx, dy)
                            }
                            MouseEvent.BUTTON2 -> {
                                // MMB: Pan camera
                                cameraState.pan(dx, dy)
                            }
                        }
                    }
                }

                MouseEvent.MOUSE_RELEASED -> {
                    activeDragButton = 0
                    lastPoint = null
                }
            }
        }

        val mask = AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK
        Toolkit.getDefaultToolkit().addAWTEventListener(listener, mask)

        onDispose {
            Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
            activeDragButton = 0
            lastPoint = null
        }
    }
}
