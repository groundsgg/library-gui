package gg.grounds.gui.theme

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.ShadowColor
import net.kyori.adventure.text.format.Style

/**
 * The window title that draws [panelId]'s artwork behind the GUI, with [label] on top of it.
 *
 * The result is three pieces in one line: a jump to the panel's origin, the glyph itself, and a
 * jump back, so [label] lands where a plain title would have.
 *
 * [anchor] is where the screen puts its title, which is not the same everywhere — an anvil starts
 * at x=60, a crafting table at 29, and a whole family centres it. Pass the one for the screen being
 * opened; the default is what an ordinary container uses. The jump back has to cancel *both* the
 * outward jump and the glyph's own advance — cancelling only the advance would leave every label
 * displaced by [Panel.offsetX], which is 8px to the left by default.
 *
 * The glyph carries a fully transparent shadow. Vanilla draws a container title unshadowed, so
 * matching that keeps a themed title from rendering the artwork twice, one pixel off, wherever a
 * shadow would otherwise be inherited.
 *
 * Pass the result to `gui(player, title)`. Clients without the pack see the label alone plus a
 * placeholder box, so a GUI stays usable when the pack fails to load.
 */
fun Theme.title(
    panelId: String,
    label: Component = Component.empty(),
    anchor: TitleAnchor = TitleAnchor.DEFAULT,
): Component {
    val panel = panel(panelId)
    // The screen decides where the title starts; the panel only nudges from there.
    require(!anchor.centred || label.equals(Component.empty())) {
        "this screen centres its title on the rendered label, a width the server cannot compute — " +
            "a panel can only be placed on it with an empty label"
    }
    val origin = anchor.x ?: (anchor.imageWidth / 2)
    val out = panel.offsetX - origin
    val glyphs = Spaces.of(out) + glyph(panelId) + Spaces.of(-(out + panel.effectiveAdvance))
    val style =
        Style.style()
            .font(Key.key(namespace, font))
            // White, or the artwork comes out at a quarter brightness. Vanilla draws a container
            // title with a default colour of 0x404040, a style colour wins over that default, and
            // the text shader multiplies the glyph's texture by it — so an uncoloured panel is
            // silently tinted dark grey. The label below is left alone and keeps vanilla's colour.
            .color(NamedTextColor.WHITE)
            .shadowColor(ShadowColor.none())
            .build()
    // An unstyled root: siblings do not inherit from each other, so the label keeps the default
    // font while the glyph run keeps the theme's.
    return Component.empty().append(Component.text(glyphs, style)).append(label)
}
