package gg.grounds.gui.layout

/**
 * Where a slot's well is drawn, in window pixels. A slot's item area sits one pixel inside it.
 *
 * Every layout in this library is pinned to these two numbers and to [SLOT_PITCH], which is why
 * they live here rather than at each call site.
 */
const val SLOT_ORIGIN_X: Int = 7

const val SLOT_ORIGIN_Y: Int = 17

const val SLOT_PITCH: Int = 18

/** The 16x16 a slot's item is drawn in — and exactly the extent of vanilla's hover box. */
const val ITEM_AREA: Int = 16

fun slotWellX(column: Int): Int = SLOT_ORIGIN_X + SLOT_PITCH * column

fun slotWellY(row: Int): Int = SLOT_ORIGIN_Y + SLOT_PITCH * row

fun slotItemX(column: Int): Int = slotWellX(column) + 1

fun slotItemY(row: Int): Int = slotWellY(row) + 1

/**
 * A rectangle in window pixels, shared by whoever paints it and whoever points a marker at it.
 *
 * This exists because those were two different people. A themed screen is drawn once into a panel
 * and addressed again at runtime by markers, and until now each half carried its own copy of every
 * coordinate — a card at (7, 76) written down twice, its text column at 54 written down twice, and
 * nothing keeping them equal except that a mismatch is visible if you happen to look.
 *
 * Deliberately not a layout engine. There is no flow, no flex and no measurement pass, because a
 * container's geometry is fixed by the client: slots are 18px apart whatever anyone would prefer.
 * What was missing was a name for a rectangle, not an algorithm for choosing one.
 */
data class Rect(val x: Int, val y: Int, val width: Int, val height: Int) {
    init {
        require(width >= 0 && height >= 0) { "a rect cannot be ${width}x$height" }
    }

    val right: Int
        get() = x + width

    val bottom: Int
        get() = y + height

    val centreX: Int
        get() = x + width / 2

    val centreY: Int
        get() = y + height / 2

    /** The same rectangle pulled in on every side, for a border or a padding. */
    fun inset(by: Int): Rect = inset(by, by, by, by)

    fun inset(left: Int, top: Int, right: Int, bottom: Int): Rect =
        Rect(x + left, y + top, width - left - right, height - top - bottom)

    /** Moved without resizing. */
    fun offset(dx: Int, dy: Int): Rect = copy(x = x + dx, y = y + dy)

    /** A rectangle of this size anchored at this one's top-left, for a sprite placed inside it. */
    fun sized(width: Int, height: Int): Rect = Rect(x, y, width, height)

    /**
     * The [index]th row of [height] pixels, stepping [pitch] each time.
     *
     * Lines of text in a card are the reason this exists: writing four y positions out is four
     * chances to fat-finger one, and the fourth is the one nobody notices.
     */
    fun row(index: Int, height: Int, pitch: Int = height): Rect =
        Rect(x, y + index * pitch, width, height)

    /** Centres a thing of [width] horizontally inside this rectangle. */
    fun centredX(width: Int): Int = x + (this.width - width) / 2

    operator fun contains(other: Rect): Boolean =
        other.x >= x && other.y >= y && other.right <= right && other.bottom <= bottom

    companion object {
        /**
         * The block of wells covering slots ([col0], [row0]) to ([col1], [row1]).
         *
         * A control drawn on the slot grid has to align with it exactly: artwork that does not
         * leaves a button you can see but cannot press at its edges.
         */
        fun wells(col0: Int, row0: Int, col1: Int, row1: Int): Rect =
            Rect(
                slotWellX(col0),
                slotWellY(row0),
                SLOT_PITCH * (col1 - col0 + 1),
                SLOT_PITCH * (row1 - row0 + 1),
            )

        /** The item areas of the same block — where the client draws items and its hover box. */
        fun items(col0: Int, row0: Int, col1: Int, row1: Int): Rect =
            Rect(
                slotItemX(col0),
                slotItemY(row0),
                SLOT_PITCH * (col1 - col0) + ITEM_AREA,
                SLOT_PITCH * (row1 - row0) + ITEM_AREA,
            )

        /** One slot's item area, which is what a per-slot marker is placed at. */
        fun slot(slot: Int): Rect = items(slot % 9, slot / 9, slot % 9, slot / 9)
    }
}
