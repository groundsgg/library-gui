package gg.grounds.gui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.ShadowColor

class ThemeTitleTest {
    private val subject =
        theme("grounds", PackFormat(88)) { panel("shop", "shop.png", 176, 166, offsetY = -6) }

    @Test
    fun `the title jumps to the panel origin, draws it, and jumps all the way back`() {
        val children = subject.title("shop", Component.text("Shop")).children()
        assertEquals(2, children.size)
        // -8 out, 177 consumed by the glyph, so 169 back lands the label exactly where a plain
        // title would sit. Cancelling only the advance would leave it 8px to the left.
        assertEquals(
            Spaces.of(TITLE_INSET) + subject.glyph("shop") + Spaces.of(-169),
            (children[0] as TextComponent).content(),
        )
    }

    @Test
    fun `the net cursor movement of a title prefix is zero`() {
        val advances = Spaces.advances()
        val prefix = (subject.title("shop").children()[0] as TextComponent).content()
        val moved =
            prefix.codePoints().toArray().sumOf { advances[String(Character.toChars(it))] ?: 0 }
        // The glyph itself is not in the advances map; it contributes its own 177.
        assertEquals(0, moved + 177)
    }

    @Test
    fun `the glyph run carries the theme font and a transparent shadow`() {
        val glyphs = subject.title("shop").children()[0]
        assertEquals(Key.key("grounds", "gui"), glyphs.style().font())
        assertEquals(ShadowColor.none(), glyphs.style().shadowColor())
    }

    @Test
    fun `the label is a sibling, so it keeps the default font`() {
        val label = Component.text("Shop")
        val title = subject.title("shop", label)
        assertEquals(label, title.children()[1])
        assertEquals(null, title.style().font())
    }

    @Test
    fun `an advance override changes only the jump back`() {
        val tuned =
            theme("grounds", PackFormat(88)) {
                panel("shop", "shop.png", 176, 166, offsetY = -6, advance = 120)
            }
        assertEquals(
            Spaces.of(TITLE_INSET) + tuned.glyph("shop") + Spaces.of(-112),
            (tuned.title("shop").children()[0] as TextComponent).content(),
        )
    }
}
