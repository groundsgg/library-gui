package gg.grounds.gui.demo

import gg.grounds.gui.layout.Rect

/**
 * Where everything on the market screen is, stated once.
 *
 * This screen is drawn twice: the painter puts pixels into a panel, and the runtime points markers
 * at the same places. Until now each half carried its own copy of every number — a card at (7, 76)
 * written down in Python and again in Kotlin, its text column at 54 written down twice, four line
 * positions written down twice — and the only thing keeping them equal was that a mismatch is
 * visible if you happen to look at the right pixel. `DemoMarket` admitted as much in its own
 * comments.
 *
 * Everything below hangs off [CARD], so moving the card moves the text, the rule, the coin and the
 * preview with it. That is the whole point: the numbers that used to be independent are now
 * derived, and a layout change is one edit rather than nine that have to agree.
 */
object MarketLayout {
    /** Eight columns of offers; slot columns are fixed at 7 + 18c, so 0..7 spans the card's width. */
    val GRID: Rect = Rect.wells(0, 0, 7, 2)

    /** The spare column beside it: search, clear, help. */
    val CONTROLS: Rect = Rect.wells(8, 0, 8, 2)

    /**
     * The detail card, sized to clear the "Inventory" label below it.
     *
     * Its left and right edges are the grid's, which is why the grid is eight columns wide rather
     * than seven — anything else left the card visibly wider than the thing above it.
     */
    val CARD: Rect = Rect(GRID.x, 76, CONTROLS.right - GRID.x, 50)

    /** The interior, which a hover blanks before rewriting. */
    val CARD_INNER: Rect = CARD.inset(3)

    /** A slot-like well for the enlarged item, and the artwork inside it. */
    val PREVIEW_WELL: Rect = Rect(CARD.x + 4, CARD.y + 7, 36, 36)

    val PREVIEW: Rect = PREVIEW_WELL.inset(2)

    /** The column beside the preview that every line is written into. */
    val TEXT: Rect =
        Rect(
            PREVIEW_WELL.right + 7,
            CARD_INNER.y,
            CARD.right - 6 - (PREVIEW_WELL.right + 7),
            CARD_INNER.height,
        )

    /** The heading: the offer's name, or the control's. */
    val NAME: Rect = Rect(TEXT.x, CARD.y + 7, TEXT.width, GLYPH_HEIGHT)

    /** A hairline under it, as wide as the column. */
    val RULE: Rect = Rect(TEXT.x, CARD.y + 18, TEXT.width, 1)

    /** The supporting line, in the muted colour. */
    val NOTE: Rect = Rect(TEXT.x, CARD.y + 22, TEXT.width, GLYPH_HEIGHT)

    /** The coin, and the number beside it. */
    val COIN: Rect = Rect(TEXT.x - 1, CARD.y + 29, 16, 16)

    val PRICE: Rect = Rect(COIN.x + 18, CARD.y + 33, TEXT.right - (COIN.x + 18), GLYPH_HEIGHT)

    /**
     * A stock bar, sharing the price's line and filling the space to its right.
     *
     * Five pixels tall because it is a readout beside a number, not a headline. Its colour runs
     * along its own length in the sprite — a thing a palette tint cannot do, since a meter spends
     * the payload byte a tint would have used.
     */
    val BAR: Rect = Rect(PRICE.x + 24, PRICE.y + 1, TEXT.right - (PRICE.x + 24), 5)

    /** The groove behind it, drawn first so an empty bar still reads as a bar. */
    val TRACK: Rect = BAR

    /** Where the resting hint sits when nothing is hovered. */
    val HINT: Rect = Rect(TEXT.x, CARD.centreY - 4, TEXT.width, GLYPH_HEIGHT)

    init {
        // Cheap, and it has caught a card that ran into the player's inventory once already.
        listOf(PREVIEW_WELL, TEXT, NAME, RULE, NOTE, COIN, PRICE, BAR, HINT).forEach { part ->
            require(part in CARD) { "$part escapes the card $CARD" }
        }
    }
}

/** A vanilla glyph is seven pixels of ink tall, whatever the cell it was cut from claims. */
const val GLYPH_HEIGHT: Int = 7
