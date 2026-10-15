package com.cheezo.ductworks.model

enum class CatalogCategory(val titleRu: String, val iconSymbol: String) {
    ROOMS("Комнаты", "◻"),
    DOORS_WINDOWS("Двери и Окна", "🚪"),
    LIVING_ROOM("Гостиная", "🛋️"),
    BEDROOM("Спальня", "🛏️"),
    KITCHEN("Кухня и Столовая", "🍳"),
    BATHROOM("Санузел и Ванная", "🚿"),
    OFFICE("Кабинет и Офис", "💼")
}

enum class ItemType {
    // Openings
    DOOR_SINGLE,
    DOOR_DOUBLE,
    DOOR_SLIDING,
    DOOR_POCKET,
    DOOR_ENTRANCE,
    DOOR_SERVER_RACK,
    DOOR_TECH_DOUBLE,
    DOOR_FIREPROOF,
    WINDOW_CASHIER,
    OPENING_ARCH,
    OPENING_TECH_HATCH,
    WINDOW_SINGLE,
    WINDOW_DOUBLE,
    WINDOW_TRIPLE,
    WINDOW_PANORAMIC,
    WINDOW_CORNER,

    // Living Room
    SOFA_STRAIGHT,
    SOFA_CORNER,
    SOFA_U_SHAPED,
    ARMCHAIR,
    COFFEE_TABLE,
    COFFEE_TABLE_ROUND,
    TV_STAND,
    BOOKSHELF,
    FLOOR_LAMP,
    CARPET_RECT,

    // Bedroom
    BED_DOUBLE,
    BED_SINGLE,
    BED_BUNK,
    WARDROBE,
    WARDROBE_CORNER,
    NIGHTSTAND,
    DRESSER,

    // Kitchen & Dining
    KITCHEN_COUNTER,
    KITCHEN_ISLAND,
    DINING_TABLE_4,
    DINING_TABLE_6,
    DINING_TABLE_ROUND,
    REFRIGERATOR,
    REFRIGERATOR_SIDE_BY_SIDE,
    COOKING_STOVE,

    // Bathroom
    BATHTUB,
    BATHTUB_FREESTANDING,
    SHOWER_CABIN,
    TOILET,
    BIDET,
    WASHBASIN,
    WASHBASIN_DOUBLE,
    WASHING_MACHINE,

    // Office & Professional Equipment
    DESK_OFFICE,
    DESK_CORNER,
    OFFICE_CHAIR,
    FILING_CABINET,
    SERVER_RACK_42U,
    SERVER_CABINET_DOUBLE,
    PC_WORKSTATION,
    DISPATCH_CONSOLE,
    CONFERENCE_TABLE,
    METAL_RACK_STORAGE,
    RECEPTION_DESK,
    UPS_INDUSTRIAL,
    DATACENTER_COOLING,

    // Ventilation & HVAC
    HVAC_DIFFUSER_SUPPLY,
    HVAC_DIFFUSER_ROUND,
    HVAC_GRILLE_EXHAUST,
    HVAC_AIR_UNIT,
    HVAC_FAN,
    HVAC_DUCT_RECT,
    HVAC_AC_OUTDOOR,
    HVAC_AC_INDOOR_SPLIT
}

val ItemType.isOpening: Boolean
    get() = when (this) {
        ItemType.DOOR_SINGLE,
        ItemType.DOOR_DOUBLE,
        ItemType.DOOR_SLIDING,
        ItemType.DOOR_POCKET,
        ItemType.DOOR_ENTRANCE,
        ItemType.DOOR_SERVER_RACK,
        ItemType.DOOR_TECH_DOUBLE,
        ItemType.DOOR_FIREPROOF,
        ItemType.WINDOW_CASHIER,
        ItemType.OPENING_ARCH,
        ItemType.OPENING_TECH_HATCH,
        ItemType.WINDOW_SINGLE,
        ItemType.WINDOW_DOUBLE,
        ItemType.WINDOW_TRIPLE,
        ItemType.WINDOW_PANORAMIC,
        ItemType.WINDOW_CORNER -> true
        else -> false
    }

/**
 * Metadata definition of an item available in the architectural catalog.
 */
data class CatalogItem(
    val id: String,
    val nameRu: String,
    val category: CatalogCategory,
    val itemType: ItemType,
    val defaultWidthCm: Float,
    val defaultDepthCm: Float,
    val defaultHeightCm: Float = 80f,
    val defaultElevationCm: Float = 0f,
    val isWallMounted: Boolean = false,
    val svgResName: String = ""
) {
    companion object {
        val ALL_ITEMS: List<CatalogItem> = listOf(
            // --- Doors & Windows ---
            CatalogItem("door_single", "Дверь межкомнатная 90см", CatalogCategory.DOORS_WINDOWS, ItemType.DOOR_SINGLE, 90f, 15f, 205f, 0f, true, "elem_door_single"),
            CatalogItem("door_double", "Дверь двустворчатая 140см", CatalogCategory.DOORS_WINDOWS, ItemType.DOOR_DOUBLE, 140f, 15f, 205f, 0f, true, "elem_door_double"),
            CatalogItem("door_server_rack", "Дверь серверная с вентиляцией и СКУД", CatalogCategory.DOORS_WINDOWS, ItemType.DOOR_SERVER_RACK, 100f, 20f, 210f, 0f, true, "elem_door_entrance"),
            CatalogItem("door_tech_double", "Дверь технологическая двупольная 160см", CatalogCategory.DOORS_WINDOWS, ItemType.DOOR_TECH_DOUBLE, 160f, 20f, 215f, 0f, true, "elem_door_double"),
            CatalogItem("door_fireproof", "Дверь противопожарная металлическая EI60", CatalogCategory.DOORS_WINDOWS, ItemType.DOOR_FIREPROOF, 100f, 20f, 210f, 0f, true, "elem_door_entrance"),
            CatalogItem("door_sliding", "Дверь раздвижная купе", CatalogCategory.DOORS_WINDOWS, ItemType.DOOR_SLIDING, 120f, 12f, 205f, 0f, true, "elem_door_sliding"),
            CatalogItem("door_pocket", "Дверь скрытая в пенал", CatalogCategory.DOORS_WINDOWS, ItemType.DOOR_POCKET, 90f, 15f, 205f, 0f, true, "elem_door_pocket"),
            CatalogItem("door_entrance", "Дверь входная усиленная", CatalogCategory.DOORS_WINDOWS, ItemType.DOOR_ENTRANCE, 100f, 20f, 210f, 0f, true, "elem_door_entrance"),
            CatalogItem("opening_arch", "Портал / Арочный проем", CatalogCategory.DOORS_WINDOWS, ItemType.OPENING_ARCH, 120f, 25f, 220f, 0f, true, "elem_opening_arch"),
            CatalogItem("opening_tech_hatch", "Монтажный технологический проем", CatalogCategory.DOORS_WINDOWS, ItemType.OPENING_TECH_HATCH, 100f, 25f, 180f, 30f, true, "elem_opening_arch"),
            CatalogItem("window_single", "Окно 1-створчатое 90см", CatalogCategory.DOORS_WINDOWS, ItemType.WINDOW_SINGLE, 90f, 15f, 140f, 90f, true, "elem_window_single"),
            CatalogItem("window_double", "Окно 2-створчатое 140см", CatalogCategory.DOORS_WINDOWS, ItemType.WINDOW_DOUBLE, 140f, 15f, 140f, 90f, true, "elem_window_double"),
            CatalogItem("window_triple", "Окно 3-створчатое 200см", CatalogCategory.DOORS_WINDOWS, ItemType.WINDOW_TRIPLE, 200f, 15f, 140f, 90f, true, "elem_window_triple"),
            CatalogItem("window_cashier", "Окно оператора / кассовое со шторкой", CatalogCategory.DOORS_WINDOWS, ItemType.WINDOW_CASHIER, 90f, 15f, 110f, 90f, true, "elem_window_single"),
            CatalogItem("window_panoramic", "Окно панорамное в пол", CatalogCategory.DOORS_WINDOWS, ItemType.WINDOW_PANORAMIC, 240f, 18f, 230f, 0f, true, "elem_window_panoramic"),

            // --- Living Room ---
            CatalogItem("sofa_straight", "Диван прямой 3-местный", CatalogCategory.LIVING_ROOM, ItemType.SOFA_STRAIGHT, 220f, 90f, 85f, svgResName = "elem_sofa_straight"),
            CatalogItem("sofa_corner", "Диван угловой L-образный", CatalogCategory.LIVING_ROOM, ItemType.SOFA_CORNER, 260f, 160f, 85f, svgResName = "elem_sofa_corner"),
            CatalogItem("sofa_u_shaped", "Диван П-образный модульный", CatalogCategory.LIVING_ROOM, ItemType.SOFA_U_SHAPED, 320f, 200f, 85f, svgResName = "elem_sofa_u_shaped"),
            CatalogItem("armchair", "Кресло лаунж сканди", CatalogCategory.LIVING_ROOM, ItemType.ARMCHAIR, 90f, 85f, 82f, svgResName = "elem_armchair"),
            CatalogItem("coffee_table", "Столик журнальный дуб", CatalogCategory.LIVING_ROOM, ItemType.COFFEE_TABLE, 110f, 60f, 45f, svgResName = "elem_coffee_table"),
            CatalogItem("coffee_table_round", "Столик кофейный круглый", CatalogCategory.LIVING_ROOM, ItemType.COFFEE_TABLE_ROUND, 80f, 80f, 45f, svgResName = "elem_coffee_table_round"),
            CatalogItem("tv_stand", "ТВ-консоль медиа", CatalogCategory.LIVING_ROOM, ItemType.TV_STAND, 180f, 45f, 50f, svgResName = "elem_tv_stand"),
            CatalogItem("bookshelf", "Стеллаж библиотечный", CatalogCategory.LIVING_ROOM, ItemType.BOOKSHELF, 120f, 35f, 200f, svgResName = "elem_bookshelf"),
            CatalogItem("floor_lamp", "Торшер дизайнерский", CatalogCategory.LIVING_ROOM, ItemType.FLOOR_LAMP, 45f, 45f, 165f, svgResName = "elem_floor_lamp"),
            CatalogItem("carpet_rect", "Ковер геометрический", CatalogCategory.LIVING_ROOM, ItemType.CARPET_RECT, 240f, 170f, 2f, svgResName = "elem_carpet"),

            // --- Bedroom ---
            CatalogItem("bed_double", "Кровать King Size 200×210", CatalogCategory.BEDROOM, ItemType.BED_DOUBLE, 200f, 210f, 95f, svgResName = "elem_bed_double"),
            CatalogItem("bed_single", "Кровать односпальная 100×200", CatalogCategory.BEDROOM, ItemType.BED_SINGLE, 100f, 200f, 85f, svgResName = "elem_bed_single"),
            CatalogItem("bed_bunk", "Кровать 2-ярусная детская", CatalogCategory.BEDROOM, ItemType.BED_BUNK, 100f, 205f, 175f, svgResName = "elem_bed_bunk"),
            CatalogItem("wardrobe", "Шкаф гардеробный 3-дверный", CatalogCategory.BEDROOM, ItemType.WARDROBE, 180f, 60f, 220f, svgResName = "elem_wardrobe"),
            CatalogItem("wardrobe_corner", "Гардероб угловой вместительный", CatalogCategory.BEDROOM, ItemType.WARDROBE_CORNER, 130f, 130f, 220f, svgResName = "elem_wardrobe_corner"),
            CatalogItem("nightstand", "Тумба прикроватная", CatalogCategory.BEDROOM, ItemType.NIGHTSTAND, 50f, 45f, 50f, svgResName = "elem_nightstand"),
            CatalogItem("dresser", "Комод 4 ящика", CatalogCategory.BEDROOM, ItemType.DRESSER, 110f, 50f, 90f, svgResName = "elem_dresser"),

            // --- Kitchen & Dining ---
            CatalogItem("kitchen_counter", "Кухонный фронт с мойкой", CatalogCategory.KITCHEN, ItemType.KITCHEN_COUNTER, 240f, 60f, 88f, svgResName = "elem_kitchen_counter"),
            CatalogItem("kitchen_island", "Кухонный остров с баром", CatalogCategory.KITCHEN, ItemType.KITCHEN_ISLAND, 180f, 90f, 90f, svgResName = "elem_kitchen_island"),
            CatalogItem("dining_table_4", "Обеденный стол на 4 персоны", CatalogCategory.KITCHEN, ItemType.DINING_TABLE_4, 140f, 85f, 75f, svgResName = "elem_dining_table_4"),
            CatalogItem("dining_table_6", "Обеденный стол на 6 персон", CatalogCategory.KITCHEN, ItemType.DINING_TABLE_6, 200f, 90f, 75f, svgResName = "elem_dining_table_6"),
            CatalogItem("dining_table_round", "Стол круглый на 4 персоны", CatalogCategory.KITCHEN, ItemType.DINING_TABLE_ROUND, 110f, 110f, 75f, svgResName = "elem_dining_table_round"),
            CatalogItem("refrigerator", "Холодильник 2-камерный", CatalogCategory.KITCHEN, ItemType.REFRIGERATOR, 65f, 65f, 195f, svgResName = "elem_refrigerator"),
            CatalogItem("refrigerator_side", "Холодильник Side-by-Side", CatalogCategory.KITCHEN, ItemType.REFRIGERATOR_SIDE_BY_SIDE, 95f, 70f, 185f, svgResName = "elem_refrigerator_side"),
            CatalogItem("cooking_stove", "Плита индукция и духовка", CatalogCategory.KITCHEN, ItemType.COOKING_STOVE, 60f, 60f, 85f, svgResName = "elem_cooking_stove"),

            // --- Bathroom ---
            CatalogItem("bathtub", "Ванна акриловая 170×75", CatalogCategory.BATHROOM, ItemType.BATHTUB, 170f, 75f, 60f, svgResName = "elem_bathtub"),
            CatalogItem("bathtub_freestanding", "Ванна отдельностоящая овал", CatalogCategory.BATHROOM, ItemType.BATHTUB_FREESTANDING, 175f, 80f, 62f, svgResName = "elem_bathtub_freestanding"),
            CatalogItem("shower_cabin", "Душевое ограждение стекло", CatalogCategory.BATHROOM, ItemType.SHOWER_CABIN, 90f, 90f, 200f, svgResName = "elem_shower_cabin"),
            CatalogItem("toilet", "Унитаз с инсталляцией", CatalogCategory.BATHROOM, ItemType.TOILET, 38f, 56f, 82f, svgResName = "elem_toilet"),
            CatalogItem("bidet", "Биде подвесное", CatalogCategory.BATHROOM, ItemType.BIDET, 38f, 56f, 42f, svgResName = "elem_bidet"),
            CatalogItem("washbasin", "Раковина с тумбой 80см", CatalogCategory.BATHROOM, ItemType.WASHBASIN, 80f, 50f, 85f, svgResName = "elem_washbasin"),
            CatalogItem("washbasin_double", "Двойная раковина 140см", CatalogCategory.BATHROOM, ItemType.WASHBASIN_DOUBLE, 140f, 50f, 85f, svgResName = "elem_washbasin_double"),
            CatalogItem("washing_machine", "Стиральная машина 60см", CatalogCategory.BATHROOM, ItemType.WASHING_MACHINE, 60f, 60f, 85f, svgResName = "elem_washing_machine"),

            // --- Office & Professional Equipment ---
            CatalogItem("desk_office", "Стол рабочий с тумбой", CatalogCategory.OFFICE, ItemType.DESK_OFFICE, 140f, 70f, 75f, svgResName = "elem_desk_office"),
            CatalogItem("desk_corner", "Стол угловой компьютерный", CatalogCategory.OFFICE, ItemType.DESK_CORNER, 160f, 120f, 75f, svgResName = "elem_desk_corner"),
            CatalogItem("office_chair", "Кресло эргономичное сетка", CatalogCategory.OFFICE, ItemType.OFFICE_CHAIR, 65f, 65f, 100f, svgResName = "elem_office_chair"),
            CatalogItem("filing_cabinet", "Тумба офисная подкатная", CatalogCategory.OFFICE, ItemType.FILING_CABINET, 45f, 50f, 60f, svgResName = "elem_filing_cabinet"),
            CatalogItem("server_rack_42u", "Серверная стойка 19\" 42U", CatalogCategory.OFFICE, ItemType.SERVER_RACK_42U, 60f, 100f, 200f, svgResName = "elem_filing_cabinet"),
            CatalogItem("server_cabinet_double", "Серверный шкаф 2-секционный", CatalogCategory.OFFICE, ItemType.SERVER_CABINET_DOUBLE, 80f, 100f, 200f, svgResName = "elem_wardrobe"),
            CatalogItem("pc_workstation", "Рабочая станция IT (ПК + 2 монитора)", CatalogCategory.OFFICE, ItemType.PC_WORKSTATION, 160f, 80f, 75f, svgResName = "elem_desk_office"),
            CatalogItem("dispatch_console", "Консоль оператора / диспетчера", CatalogCategory.OFFICE, ItemType.DISPATCH_CONSOLE, 180f, 90f, 75f, svgResName = "elem_desk_corner"),
            CatalogItem("conference_table", "Стол переговоров на 8 персон", CatalogCategory.OFFICE, ItemType.CONFERENCE_TABLE, 280f, 120f, 75f, svgResName = "elem_dining_table_6"),
            CatalogItem("metal_rack_storage", "Стеллаж архивный металлический", CatalogCategory.OFFICE, ItemType.METAL_RACK_STORAGE, 100f, 45f, 200f, svgResName = "elem_bookshelf"),
            CatalogItem("reception_desk", "Стойка ресепшн угловая", CatalogCategory.OFFICE, ItemType.RECEPTION_DESK, 200f, 140f, 115f, svgResName = "elem_desk_corner"),
            CatalogItem("ups_industrial", "ИБП промышленный стоечный", CatalogCategory.OFFICE, ItemType.UPS_INDUSTRIAL, 60f, 80f, 140f, svgResName = "elem_filing_cabinet"),
            CatalogItem("datacenter_cooling", "Кондиционер прецизионный ЦОД", CatalogCategory.OFFICE, ItemType.DATACENTER_COOLING, 90f, 60f, 195f, svgResName = "elem_refrigerator")
        )
    }
}
