package com.cheezo.ductworks

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Ductworks CAD — 2D Floor Planner & Ventilation Engine",
        state = rememberWindowState(width = 1360.dp, height = 860.dp)
    ) {
        App()
    }
}