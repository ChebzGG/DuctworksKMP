package com.cheezo.ductworks.ui.threed

import androidx.compose.runtime.Composable

/**
 * Platform-specific input hooks to capture viewport events
 * (such as native AWT heavyweight Canvas events on Desktop).
 */
@Composable
expect fun AttachViewport3DInput(cameraState: KorenderCameraState)
