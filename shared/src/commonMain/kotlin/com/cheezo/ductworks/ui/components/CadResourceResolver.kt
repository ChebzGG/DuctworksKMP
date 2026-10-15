package com.cheezo.ductworks.ui.components

import ductworkskmp.shared.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource

/**
 * Maps architectural item and UI icon identifier names to compiled Compose Multiplatform SVG DrawableResources.
 */
object CadResourceResolver {

    fun getItemDrawable(svgResName: String): DrawableResource? = when (svgResName) {
        "elem_door_single" -> Res.drawable.elem_door_single
        "elem_door_double" -> Res.drawable.elem_door_double
        "elem_door_sliding" -> Res.drawable.elem_door_sliding
        "elem_door_pocket" -> Res.drawable.elem_door_pocket
        "elem_door_entrance" -> Res.drawable.elem_door_entrance
        "elem_opening_arch" -> Res.drawable.elem_opening_arch

        "elem_window_single" -> Res.drawable.elem_window_single
        "elem_window_double" -> Res.drawable.elem_window_double
        "elem_window_triple" -> Res.drawable.elem_window_triple
        "elem_window_panoramic" -> Res.drawable.elem_window_panoramic
        "elem_window_corner" -> Res.drawable.elem_window_corner

        "elem_sofa_straight" -> Res.drawable.elem_sofa_straight
        "elem_sofa_corner" -> Res.drawable.elem_sofa_corner
        "elem_sofa_u_shaped" -> Res.drawable.elem_sofa_u_shaped
        "elem_armchair" -> Res.drawable.elem_armchair
        "elem_coffee_table" -> Res.drawable.elem_coffee_table
        "elem_coffee_table_round" -> Res.drawable.elem_coffee_table_round
        "elem_tv_stand" -> Res.drawable.elem_tv_stand
        "elem_bookshelf" -> Res.drawable.elem_bookshelf
        "elem_floor_lamp" -> Res.drawable.elem_floor_lamp
        "elem_carpet" -> Res.drawable.elem_carpet

        "elem_bed_double" -> Res.drawable.elem_bed_double
        "elem_bed_single" -> Res.drawable.elem_bed_single
        "elem_bed_bunk" -> Res.drawable.elem_bed_bunk
        "elem_wardrobe" -> Res.drawable.elem_wardrobe
        "elem_wardrobe_corner" -> Res.drawable.elem_wardrobe_corner
        "elem_nightstand" -> Res.drawable.elem_nightstand
        "elem_dresser" -> Res.drawable.elem_dresser

        "elem_kitchen_counter" -> Res.drawable.elem_kitchen_counter
        "elem_kitchen_island" -> Res.drawable.elem_kitchen_island
        "elem_dining_table_4" -> Res.drawable.elem_dining_table_4
        "elem_dining_table_6" -> Res.drawable.elem_dining_table_6
        "elem_dining_table_round" -> Res.drawable.elem_dining_table_round
        "elem_refrigerator" -> Res.drawable.elem_refrigerator
        "elem_refrigerator_side" -> Res.drawable.elem_refrigerator_side
        "elem_cooking_stove" -> Res.drawable.elem_cooking_stove

        "elem_bathtub" -> Res.drawable.elem_bathtub
        "elem_bathtub_freestanding" -> Res.drawable.elem_bathtub_freestanding
        "elem_shower_cabin" -> Res.drawable.elem_shower_cabin
        "elem_toilet" -> Res.drawable.elem_toilet
        "elem_bidet" -> Res.drawable.elem_bidet
        "elem_washbasin" -> Res.drawable.elem_washbasin
        "elem_washbasin_double" -> Res.drawable.elem_washbasin_double
        "elem_washing_machine" -> Res.drawable.elem_washing_machine

        "elem_desk_office" -> Res.drawable.elem_desk_office
        "elem_desk_corner" -> Res.drawable.elem_desk_corner
        "elem_office_chair" -> Res.drawable.elem_office_chair
        "elem_filing_cabinet" -> Res.drawable.elem_filing_cabinet

        "elem_hvac_diffuser_supply" -> Res.drawable.elem_hvac_diffuser_supply
        "elem_hvac_diffuser_round" -> Res.drawable.elem_hvac_diffuser_round
        "elem_hvac_grille_exhaust" -> Res.drawable.elem_hvac_grille_exhaust
        "elem_hvac_air_unit" -> Res.drawable.elem_hvac_air_unit
        "elem_hvac_fan" -> Res.drawable.elem_hvac_fan
        "elem_hvac_duct_rect" -> Res.drawable.elem_hvac_duct_rect
        "elem_hvac_ac_outdoor" -> Res.drawable.elem_hvac_ac_outdoor
        "elem_hvac_ac_indoor" -> Res.drawable.elem_hvac_ac_indoor

        else -> null
    }

    fun getIconDrawable(iconName: String): DrawableResource? = when (iconName) {
        "ic_plan_2d" -> Res.drawable.ic_plan_2d
        "ic_model_3d" -> Res.drawable.ic_model_3d
        "ic_grid" -> Res.drawable.ic_grid
        "ic_magnet" -> Res.drawable.ic_magnet
        "ic_ortho" -> Res.drawable.ic_ortho
        "ic_dimensions" -> Res.drawable.ic_dimensions
        "ic_area" -> Res.drawable.ic_area
        "ic_undo" -> Res.drawable.ic_undo
        "ic_redo" -> Res.drawable.ic_redo
        "ic_fit_screen" -> Res.drawable.ic_fit_screen
        "ic_properties" -> Res.drawable.ic_properties
        "ic_rooms" -> Res.drawable.ic_rooms
        "ic_openings" -> Res.drawable.ic_openings
        "ic_furniture" -> Res.drawable.ic_furniture
        "ic_hvac" -> Res.drawable.ic_hvac
        "ic_tools" -> Res.drawable.ic_tools
        "ic_close" -> Res.drawable.ic_close
        "ic_trash" -> Res.drawable.ic_trash
        "ic_file_template" -> Res.drawable.ic_file_template
        "ic_view_iso" -> Res.drawable.ic_view_iso
        "ic_view_top" -> Res.drawable.ic_view_top
        "ic_view_front" -> Res.drawable.ic_view_front
        "ic_view_reset" -> Res.drawable.ic_view_reset
        "ic_minus" -> Res.drawable.ic_minus
        "ic_plus" -> Res.drawable.ic_plus
        else -> null
    }

    /**
     * Resolves and remembers SVG Painters for all catalog items in Compose runtime.
     */
    @androidx.compose.runtime.Composable
    fun rememberItemPainters(): Map<String, androidx.compose.ui.graphics.painter.Painter> {
        val map = androidx.compose.runtime.remember { mutableMapOf<String, androidx.compose.ui.graphics.painter.Painter>() }
        for (item in com.cheezo.ductworks.model.CatalogItem.ALL_ITEMS) {
            val res = getItemDrawable(item.svgResName)
            if (res != null) {
                map[item.svgResName] = org.jetbrains.compose.resources.painterResource(res)
            }
        }
        return map
    }
}

