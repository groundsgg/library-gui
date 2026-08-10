package gg.grounds.gui.demo

import gg.grounds.gui.art.readSprite
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The painted sprites against the layout both halves are supposed to be reading.
 *
 * A shared description only helps if it is actually shared. This checks the sizes that would drift
 * first — the patch that blanks the card, the well behind the preview, the rule under the heading,
 * the enlarged item — because a sprite one pixel off its rectangle leaves a seam that reads as a
 * rendering bug rather than as a layout one.
 */
class MarketLayoutTest {
    private fun sizeOf(name: String): Pair<Int, Int> {
        val path = ART.resolve(name)
        assertTrue(path.exists(), "missing $name; run :examples:theme-demo:paintArt")
        val sprite = readSprite(path)
        return sprite.width to sprite.height
    }

    @Test
    fun `every card sprite is the size its rectangle says`() {
        assertEquals(
            MarketLayout.CARD_INNER.width to MarketLayout.CARD_INNER.height,
            sizeOf("frame/market_card.png"),
        )
        assertEquals(
            MarketLayout.PREVIEW_WELL.width to MarketLayout.PREVIEW_WELL.height,
            sizeOf("frame/market_well.png"),
        )
        assertEquals(
            MarketLayout.RULE.width to MarketLayout.RULE.height,
            sizeOf("frame/market_rule.png"),
        )
        assertEquals(
            MarketLayout.PREVIEW.width to MarketLayout.PREVIEW.height,
            sizeOf("frame/market_item_diamond_sword.png"),
        )
    }

    @Test
    fun `the grid and the card share their edges`() {
        // The reason the grid is eight columns rather than seven. Insetting it left the card
        // visibly wider than the thing above it, with nothing to justify the difference.
        assertEquals(MarketLayout.CARD.x, MarketLayout.GRID.x)
        assertEquals(MarketLayout.CARD.right, MarketLayout.CONTROLS.right)
    }

    @Test
    fun `the card clears the player inventory below it`() {
        // ChestMenu puts the inventory label just above inventoryTop = imageHeight - 83. A card
        // running into it is what the first cut of this screen did.
        val inventoryLabel = 114 + 6 * 18 - 83 - 10
        assertTrue(
            MarketLayout.CARD.bottom <= inventoryLabel,
            "card ends at ${MarketLayout.CARD.bottom}, label starts at $inventoryLabel",
        )
    }
}
