package gg.grounds.gui.theme

import net.minestom.server.inventory.InventoryType

/**
 * Where a screen puts its title, and how big its window is.
 *
 * A panel is positioned relative to the title, so this is what decides where it lands — and it is
 * not the same for every screen. Most inherit the container default of (8, 6), but an anvil puts
 * its title at x=60, a crafting table at 29, a smithing table at 44 *and* y=15, and a whole family
 * centres it instead. Assuming 8 everywhere puts artwork in the wrong place on six screen types.
 *
 * All values are read from the 26.2 client's screen classes.
 *
 * @param x the title's left edge, or `null` when the client centres it on the rendered label
 * @param y the title's top row
 */
data class TitleAnchor(
    val x: Int?,
    val y: Int = TITLE_TOP_Y,
    val imageWidth: Int = CONTAINER_WIDTH,
    val imageHeight: Int = containerHeight(3),
) {
    /**
     * True when the client positions the title by measuring the label rather than by a constant.
     */
    val centred: Boolean
        get() = x == null

    companion object {
        /** What `AbstractContainerScreen` sets, and what most screens keep. */
        val DEFAULT: TitleAnchor = TitleAnchor(x = 8)
    }
}

/**
 * The anchor for a generic chest of [rows] rows.
 *
 * Every chest row count shares the same title position; only the window's height changes.
 */
fun chestAnchor(rows: Int): TitleAnchor {
    require(rows in 1..6) { "a chest has 1..6 rows, got $rows" }
    return TitleAnchor(x = 8, imageWidth = CONTAINER_WIDTH, imageHeight = containerHeight(rows))
}

/**
 * The anchor for a container type, from the 26.2 client.
 *
 * The centred ones are the interesting case: their title x is `(imageWidth - font.width(title)) /
 * 2`, which depends on the rendered width of the label — a number the server has no way to compute.
 * It is still usable, because a themed title's glyph run measures exactly zero: the jump out, the
 * glyph's own advance and the jump back cancel. So with an empty label the title lands at exactly
 * half the window, and [Theme.title] refuses anything else.
 */
fun anchorOf(type: InventoryType): TitleAnchor =
    when (type) {
        InventoryType.CHEST_1_ROW -> chestAnchor(1)
        InventoryType.CHEST_2_ROW -> chestAnchor(2)
        InventoryType.CHEST_3_ROW -> chestAnchor(3)
        InventoryType.CHEST_4_ROW -> chestAnchor(4)
        InventoryType.CHEST_5_ROW -> chestAnchor(5)
        InventoryType.CHEST_6_ROW -> chestAnchor(6)
        InventoryType.SHULKER_BOX -> TitleAnchor(x = 8, imageHeight = 167)
        InventoryType.HOPPER -> TitleAnchor(x = 8, imageHeight = 133)
        InventoryType.BEACON -> TitleAnchor(x = 8, imageWidth = 230, imageHeight = 219)
        InventoryType.MERCHANT -> TitleAnchor(x = 8, imageWidth = 276, imageHeight = 166)
        InventoryType.ANVIL -> TitleAnchor(x = 60, imageHeight = 166)
        InventoryType.CRAFTING -> TitleAnchor(x = 29, imageHeight = 166)
        InventoryType.SMITHING -> TitleAnchor(x = 44, y = 15, imageHeight = 166)
        // Centred by the client on the label's own width.
        InventoryType.FURNACE,
        InventoryType.SMOKER,
        InventoryType.BLAST_FURNACE,
        InventoryType.BREWING_STAND,
        InventoryType.CRAFTER_3X3,
        InventoryType.WINDOW_3X3 -> TitleAnchor(x = null, imageHeight = 166)
        // Everything else inherits AbstractContainerScreen's (8, 6) and the 176x166 default.
        else -> TitleAnchor(x = 8, imageHeight = 166)
    }
