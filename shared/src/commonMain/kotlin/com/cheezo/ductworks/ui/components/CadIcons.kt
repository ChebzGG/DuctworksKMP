package com.cheezo.ductworks.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ductworkskmp.shared.generated.resources.*
import org.jetbrains.compose.resources.painterResource

/**
 * High-End Vector Iconography for Professional CAD & Architectural UI.
 * Backed by crisp SVG vector resources with color tinting support.
 * Replaces hardcoded canvas paths with modern, clean SVG icons.
 */
object CadIcons {

    @Composable
    fun Plan2D(
        modifier: Modifier = Modifier.size(16.dp),
        tint: Color = Color(0xFF0F172A)
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_plan_2d),
            contentDescription = "2D Plan",
            modifier = modifier,
            tint = tint
        )
    }

    @Composable
    fun Model3D(
        modifier: Modifier = Modifier.size(16.dp),
        tint: Color = Color(0xFF0F172A)
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_model_3d),
            contentDescription = "3D Model",
            modifier = modifier,
            tint = tint
        )
    }

    @Composable
    fun Settings(
        modifier: Modifier = Modifier.size(15.dp),
        tint: Color = Color(0xFF475569)
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_settings),
            contentDescription = "Settings",
            modifier = modifier,
            tint = tint
        )
    }

    @Composable
    fun Grid(
        modifier: Modifier = Modifier.size(15.dp),
        tint: Color = Color(0xFF475569)
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_grid),
            contentDescription = "Grid",
            modifier = modifier,
            tint = tint
        )
    }

    @Composable
    fun Magnet(
        modifier: Modifier = Modifier.size(15.dp),
        tint: Color = Color(0xFF475569)
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_magnet),
            contentDescription = "Snap",
            modifier = modifier,
            tint = tint
        )
    }

    @Composable
    fun Ortho(
        modifier: Modifier = Modifier.size(15.dp),
        tint: Color = Color(0xFF475569)
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_ortho),
            contentDescription = "Ortho",
            modifier = modifier,
            tint = tint
        )
    }

    @Composable
    fun Dimensions(
        modifier: Modifier = Modifier.size(15.dp),
        tint: Color = Color(0xFF475569)
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_dimensions),
            contentDescription = "Dimensions",
            modifier = modifier,
            tint = tint
        )
    }

    @Composable
    fun Area(
        modifier: Modifier = Modifier.size(15.dp),
        tint: Color = Color(0xFF475569)
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_area),
            contentDescription = "Area",
            modifier = modifier,
            tint = tint
        )
    }

    @Composable
    fun Undo(
        modifier: Modifier = Modifier.size(15.dp),
        tint: Color = Color(0xFF334155)
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_undo),
            contentDescription = "Undo",
            modifier = modifier,
            tint = tint
        )
    }

    @Composable
    fun Redo(
        modifier: Modifier = Modifier.size(15.dp),
        tint: Color = Color(0xFF334155)
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_redo),
            contentDescription = "Redo",
            modifier = modifier,
            tint = tint
        )
    }

    @Composable
    fun Plus(
        modifier: Modifier = Modifier.size(14.dp),
        tint: Color = Color(0xFF334155)
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_plus),
            contentDescription = "Plus",
            modifier = modifier,
            tint = tint
        )
    }

    @Composable
    fun Minus(
        modifier: Modifier = Modifier.size(14.dp),
        tint: Color = Color(0xFF334155)
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_minus),
            contentDescription = "Minus",
            modifier = modifier,
            tint = tint
        )
    }

    @Composable
    fun FitScreen(
        modifier: Modifier = Modifier.size(14.dp),
        tint: Color = Color(0xFF334155)
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_fit_screen),
            contentDescription = "Fit to Screen",
            modifier = modifier,
            tint = tint
        )
    }

    @Composable
    fun Properties(
        modifier: Modifier = Modifier.size(15.dp),
        tint: Color = Color(0xFF475569)
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_properties),
            contentDescription = "Properties",
            modifier = modifier,
            tint = tint
        )
    }

    @Composable
    fun RoomsTab(
        modifier: Modifier = Modifier.size(20.dp),
        tint: Color = Color(0xFF475569)
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_rooms),
            contentDescription = "Rooms",
            modifier = modifier,
            tint = tint
        )
    }

    @Composable
    fun OpeningsTab(
        modifier: Modifier = Modifier.size(20.dp),
        tint: Color = Color(0xFF475569)
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_openings),
            contentDescription = "Openings",
            modifier = modifier,
            tint = tint
        )
    }

    @Composable
    fun FurnitureTab(
        modifier: Modifier = Modifier.size(20.dp),
        tint: Color = Color(0xFF475569)
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_furniture),
            contentDescription = "Furniture",
            modifier = modifier,
            tint = tint
        )
    }

    @Composable
    fun HvacTab(
        modifier: Modifier = Modifier.size(20.dp),
        tint: Color = Color(0xFF475569)
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_hvac),
            contentDescription = "HVAC",
            modifier = modifier,
            tint = tint
        )
    }

    @Composable
    fun ToolsTab(
        modifier: Modifier = Modifier.size(20.dp),
        tint: Color = Color(0xFF475569)
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_tools),
            contentDescription = "Tools",
            modifier = modifier,
            tint = tint
        )
    }

    @Composable
    fun Close(
        modifier: Modifier = Modifier.size(14.dp),
        tint: Color = Color(0xFF64748B)
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_close),
            contentDescription = "Close",
            modifier = modifier,
            tint = tint
        )
    }

    @Composable
    fun FileTemplate(
        modifier: Modifier = Modifier.size(15.dp),
        tint: Color = Color(0xFF475569)
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_file_template),
            contentDescription = "Templates",
            modifier = modifier,
            tint = tint
        )
    }

    @Composable
    fun Trash(
        modifier: Modifier = Modifier.size(15.dp),
        tint: Color = Color(0xFF64748B)
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_trash),
            contentDescription = "Trash",
            modifier = modifier,
            tint = tint
        )
    }

    @Composable
    fun IsometricView(
        modifier: Modifier = Modifier.size(15.dp),
        tint: Color = Color(0xFF334155)
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_view_iso),
            contentDescription = "Isometric View",
            modifier = modifier,
            tint = tint
        )
    }

    @Composable
    fun TopView(
        modifier: Modifier = Modifier.size(15.dp),
        tint: Color = Color(0xFF334155)
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_view_top),
            contentDescription = "Top View",
            modifier = modifier,
            tint = tint
        )
    }

    @Composable
    fun FrontView(
        modifier: Modifier = Modifier.size(15.dp),
        tint: Color = Color(0xFF334155)
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_view_front),
            contentDescription = "Front View",
            modifier = modifier,
            tint = tint
        )
    }

    @Composable
    fun ResetTarget(
        modifier: Modifier = Modifier.size(15.dp),
        tint: Color = Color(0xFF334155)
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_view_reset),
            contentDescription = "Reset Target",
            modifier = modifier,
            tint = tint
        )
    }

    @Composable
    fun RotateCcw(
        modifier: Modifier = Modifier.size(15.dp),
        tint: Color = Color(0xFF475569)
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_rotate_ccw),
            contentDescription = "Rotate CCW",
            modifier = modifier,
            tint = tint
        )
    }

    @Composable
    fun RotateCw(
        modifier: Modifier = Modifier.size(15.dp),
        tint: Color = Color(0xFF475569)
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_rotate_cw),
            contentDescription = "Rotate CW",
            modifier = modifier,
            tint = tint
        )
    }

    @Composable
    fun FlipH(
        modifier: Modifier = Modifier.size(15.dp),
        tint: Color = Color(0xFF475569)
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_flip_h),
            contentDescription = "Flip Horizontal",
            modifier = modifier,
            tint = tint
        )
    }

    @Composable
    fun Duplicate(
        modifier: Modifier = Modifier.size(15.dp),
        tint: Color = Color(0xFF475569)
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_duplicate),
            contentDescription = "Duplicate",
            modifier = modifier,
            tint = tint
        )
    }

    @Composable
    fun LayerUp(
        modifier: Modifier = Modifier.size(15.dp),
        tint: Color = Color(0xFF475569)
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_layer_up),
            contentDescription = "Layer Up",
            modifier = modifier,
            tint = tint
        )
    }

    @Composable
    fun LayerDown(
        modifier: Modifier = Modifier.size(15.dp),
        tint: Color = Color(0xFF475569)
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_layer_down),
            contentDescription = "Layer Down",
            modifier = modifier,
            tint = tint
        )
    }

    @Composable
    fun SplitWall(
        modifier: Modifier = Modifier.size(15.dp),
        tint: Color = Color(0xFF475569)
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_split_wall),
            contentDescription = "Split Wall",
            modifier = modifier,
            tint = tint
        )
    }
}
