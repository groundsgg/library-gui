package gg.grounds.gui.theme

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.ShadowColor
import net.kyori.adventure.text.format.Style

/**
 * The window title that draws [panelId]'s artwork behind the GUI, with [label] on top of it.
 *
 * The result is three pieces in one line: a jump to the panel's origin, the glyph itself, and a
 * jump back, so [label] lands where a plain title would have. The jump back has to cancel *both*
 * the outward jump and the glyph's own advance — cancelling only the advance would leave every
 * label displaced by [Panel.offsetX], which is 8px to the left by default.
 *
 * The glyph carries a fully transparent shadow. Vanilla draws a container title unshadowed, so
 * matching that keeps a themed title from rendering the artwork twice, one pixel off, wherever a
 * shadow would otherwise be inherited.
 *
 * Pass the result to `gui(player, title)`. Clients without the pack see the label alone plus a
 * placeholder box, so a GUI stays usable when the pack fails to load.
 */
fun Theme.title(panelId: String, label: Component = Component.empty()): Component {
    val panel = panel(panelId)
    val glyphs =
        Spaces.of(panel.offsetX) +
            glyph(panelId) +
            Spaces.of(-(panel.offsetX + panel.effectiveAdvance))
    val style = Style.style().font(Key.key(namespace, font)).shadowColor(ShadowColor.none()).build()
    // An unstyled root: siblings do not inherit from each other, so the label keeps the default
    // font while the glyph run keeps the theme's.
    return Component.empty().append(Component.text(glyphs, style)).append(label)
}
