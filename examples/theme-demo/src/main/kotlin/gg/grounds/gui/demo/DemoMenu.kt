package gg.grounds.gui.demo

import gg.grounds.gui.gui
import gg.grounds.gui.item
import gg.grounds.gui.theme.Theme
import gg.grounds.gui.theme.containerHeight
import gg.grounds.gui.theme.frameMarker
import gg.grounds.gui.theme.title
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.entity.Player
import net.minestom.server.item.Material

private const val ROWS = 3

/** Vanilla's own button click. Free, and it is most of what makes a button feel like a button. */
private val CLICK = Sound.sound(Key.key("ui.button.click"), Sound.Source.MASTER, 0.4f, 1f)

/**
 * A button that occupies whole slots.
 *
 * Snapping to the slot grid is not a simplification, it is the requirement: the artwork is painted
 * into the panel and the hit area is a set of slots, so anything that does not align leaves a
 * button you can see but not press at its edges. Slot (c, r) owns the 18×18 well at
 * (7 + 18c, 17 + 18r), and a button is that rectangle widened across the columns it spans.
 */
private class MenuButton(
    val id: String,
    val label: String,
    val description: String,
    val frame: String,
    /** Width of the label sprite, so it can be centred without measuring text at runtime. */
    val labelWidth: Int,
    val c0: Int,
    val r0: Int,
    val c1: Int,
    val r1: Int,
) {
    val slots: List<Int> = (r0..r1).flatMap { r -> (c0..c1).map { c -> r * 9 + c } }
    val centre: Int = slots[slots.size / 2]

    /** The button's rectangle: the wells it covers. */
    val x: Int = 7 + 18 * c0
    val y: Int = 17 + 18 * r0

    val width: Int = 18 * (c1 - c0 + 1)

    /** Centred on the face, the way a vanilla button carries its own label. */
    val labelX: Int = x + (width - labelWidth) / 2
    val labelY: Int = y + 5
}

private val BUTTONS =
    listOf(
        MenuButton("shop", "Shop", "Buy blocks and gear", "small", 25, 1, 0, 3, 0),
        MenuButton("kits", "Kits", "Pick a loadout", "small", 19, 5, 0, 7, 0),
        MenuButton("play", "Play now", "Straight into a match", "wide", 44, 1, 1, 7, 1),
        MenuButton("settings", "Settings", "Language, sounds, HUD", "small", 41, 1, 2, 3, 2),
        MenuButton("profile", "Profile", "Stats and cosmetics", "small", 35, 5, 2, 7, 2),
    )

/**
 * A menu built out of buttons rather than out of slots.
 *
 * The button faces are vanilla's own `widget/button` sprite, nine-sliced by the generator to
 * whatever width the button spans, so the 3px bevel stays crisp at every size. Clicking plays
 * `ui.button.click`, which costs nothing and is most of what makes a button feel pressed.
 *
 * Hover is composited from **two layers**, and it has to be. A tooltip can carry several marker
 * glyphs, the shader relocates each one, and they draw in glyph order — so markers stack:
 *
 *  1. the highlighted button face, opaque, across the whole rectangle. Opaque on purpose: it is
 *     what hides vanilla's single-slot highlight box, which would otherwise appear in the middle
 *     of a seven-slot button and give the illusion away.
 *  2. the label again, on top, because layer 1 just covered it — so the button keeps its text.
 *
 * There is no tooltip at all: every slot's tooltip is transparent and holds nothing but markers,
 * so the label sits centred on the button where a vanilla button's label sits, rather than in a box
 * that follows the cursor. Which is why every slot holds an invisible item and the text lives in the
 * artwork instead:
 * an item rendered by the client would sit under layer 1 anyway. The items still have to exist —
 * a slot with no item gets no tooltip, and no tooltip means no markers.
 */
fun openMenu(player: Player) {
    val theme = DemoTheme.current()
    val height = containerHeight(ROWS)

    gui(player, theme.title("menu", Component.empty()), rows = ROWS) {
        // Every slot the buttons do not cover gets the same treatment, one layer instead of two:
        // a patch laid exactly over where the client draws its hover box. Outside the buttons this
        // panel is uniform, so one sprite serves every empty slot.
        //
        // By default the patch is bare panel face, so a hovered empty tile looks exactly like an
        // unhovered one. `/tint` swaps in the same patch with a hover tint blended in, and that
        // blend is the whole trick: a marker cannot be a translucent hover by itself, because
        // vanilla's box is drawn before any tooltip and a see-through layer would land on top of it
        // — white box plus tint. Covering opaquely is the only way to replace it, and once the
        // cover is opaque, a translucent layer over it is just a colour, so it gets computed in the
        // generator. Either way the vanilla sprite is untouched and the player's own inventory
        // keeps its normal highlight.
        val taken = BUTTONS.flatMap { it.slots }.toSet()
        (0 until ROWS * 9).filterNot(taken::contains).forEach { slot ->
            val cover =
                theme.frameMarker(
                    DemoTheme.menuTile(),
                    8 + 18 * (slot % 9),
                    18 + 18 * (slot / 9),
                    imageHeight = height,
                )
            button(
                slot,
                item(Material.BUNDLE) {
                    name(cover)
                    itemModel = theme.itemModel(DemoTheme.BLANK)
                    tooltipStyle = theme.tooltipStyle(DemoTheme.BLANK)
                },
            ) {}
        }

        BUTTONS.forEach { menuButton ->
            val hover =
                Component.empty()
                    // No cover layer underneath: the face below is 100% opaque across the whole
                    // button rectangle, so a patch inside it cannot change a pixel. Empty slots
                    // still need one, because there is no face there to do the covering.
                    .append(
                        theme.frameMarker(
                            "menu_face_${menuButton.frame}",
                            menuButton.x,
                            menuButton.y,
                            imageHeight = height,
                        )
                    )
                    .append(
                        theme.frameMarker(
                            "menu_label_${menuButton.id}",
                            menuButton.labelX,
                            menuButton.labelY,
                            imageHeight = height,
                        )
                    )

            menuButton.slots.forEach { slot ->
                button(slot, face(theme, menuButton, hover, readable = slot == menuButton.centre)) {
                    onClick {
                        player.playSound(CLICK)
                        player.sendMessage(
                            Component.text("${menuButton.label} — ", NamedTextColor.WHITE)
                                .append(Component.text(menuButton.description, NamedTextColor.GRAY))
                        )
                    }
                }
            }
        }
    }
        .open()
}

/**
 * One slot of a button: invisible, and carrying the hover stack.
 *
 * The middle slot gets a readable tooltip so the button has a name; the rest stay silent. Both are
 * invisible items, because the icon is painted into the panel and redrawn by the hover stack.
 */
private fun face(theme: Theme, menuButton: MenuButton, hover: Component, readable: Boolean) =
    item(Material.BUNDLE) {
        itemModel = theme.itemModel(DemoTheme.BLANK)
        if (readable) {
            name(hover)
            tooltipStyle = theme.tooltipStyle(DemoTheme.BLANK)
        } else {
            name(hover)
            tooltipStyle = theme.tooltipStyle(DemoTheme.BLANK)
        }
    }
