package com.cheezo.ductworks.ui.threed

import androidx.compose.runtime.Composable

/**
 * Android implementation:
 * On Android, Korender uses GLSurfaceView inside AndroidView.
 * Multi-touch (1 finger orbit, 2 finger pinch-to-zoom & pan) is handled
 * via Compose pointer input overlay in CadViewport3D.
 */
@Composable
actual fun AttachViewport3DInput(cameraState: KorenderCameraState) {
    // No-op: Compose pointerInput in CadViewport3D handles touch events on Android
}
