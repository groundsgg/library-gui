package gg.grounds.gui.demo

import gg.grounds.gui.theme.frameMarker
import gg.grounds.gui.theme.containerHeight
import gg.grounds.gui.theme.text
import kotlin.test.Test

class DemoMarketTest {
    private val height = containerHeight(6)

    /**
     * Every frame the market can ask for has to be declared, and only opening the screen used to
     * find out. It threw on `market_outline_question`: the icon list was written out twice, the
     * third control reached the generator and the screen but not the theme, and a build that
     * compiled and a test suite that passed said nothing about it. Resolving each id here is the
     * cheapest stand-in for opening the GUI.
     */
    @Test
    fun `every frame the market composes is declared`() {
        val theme = DemoTheme.current()
        val ids =
            MARKET_CONTROLS.flatMap { (icon, _) ->
                // Both, because a control's hover blanks its slot and has to put the icon back —
                // and a missing redraw is a control that disappears while you point at it.
                listOf("market_outline_$icon", "market_icon_$icon")
            } +
                MARKET_TEXTURES.map { texture -> "market_item_$texture" } +
                MARKET_CARD_PARTS.map { part -> "market_$part" } +
                (0 until 54).map { slot -> "mk_cover_$slot" }
        ids.forEach { id -> theme.frameMarker(id, 8, 18, imageHeight = height) }
    }

    /**
     * Both families have to cover every printable character, because the search box hands the
     * screen whatever a player typed.
     *
     * One character at a time, not all of them in one string: ninety-five glyphs run 578px wide and
     * a marker carries its position in a signed byte, so that would fail on the range rather than
     * on a missing glyph — which is the library being right and the test being wrong.
     */
    @Test
    fun `the glyph set covers every printable character, in every tint`() {
        val theme = DemoTheme.current()
        listOf(null, DIM, GOLD).forEach { tint ->
            (32..126).forEach { code ->
                theme.text(GLYPHS, 8, 18, code.toChar().toString(), imageHeight = height, tint = tint)
            }
        }
    }

    /** And the catalogue's own copy, laid out where the card actually puts it. */
    @Test
    fun `every line the card writes fits inside it`() {
        val theme = DemoTheme.current()
        MARKET_LINES.forEach { body -> theme.text(GLYPHS, 54, 83, body, imageHeight = height) }
    }
}
