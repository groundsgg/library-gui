package gg.grounds.gui.demo

import gg.grounds.gui.gui
import gg.grounds.gui.item
import gg.grounds.gui.theme.chestFrame
import gg.grounds.gui.theme.title
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.minestom.server.entity.Player
import net.minestom.server.item.Material

private const val ROWS = 3

/** The ring stands this far clear of the shape; the artwork is 2 * this larger to match. */
private const val FRAME_OUTSET = 2

/** The colour vanilla draws a container's own title in. */
private val TITLE_GREY = TextColor.color(0x404040)

/**
 * An empty grey window with one button in it, three slots by three.
 *
 * Nothing here looks like an inventory: the panel paints vanilla's own chrome over the container
 * texture, the slot hover box is overridden with transparent sprites, and the nine slots the button
 * covers hold items with a transparent model. What remains on screen is a window and a button.
 *
 * Those nine items still have to exist, and still have to have a tooltip. That is not decoration —
 * it is the mechanism. The client renders a tooltip only for the slot under the cursor and only for
 * that slot's own item, and the frame markers ride inside it. No item, no tooltip, no frame. So the
 * tooltip is made invisible rather than removed: a transparent skin, and its one line is the
 * markers, which the shader relocates out of it and whose advance is cancelled to zero.
 *
 * All nine carry the same markers, which is what makes them one button: the frame that appears is
 * the same frame wherever on the block the cursor sits.
 */
fun openHoverGui(player: Player) {
    val theme = DemoTheme.current()

    val label = Component.text("Auswählen", TITLE_GREY)

    gui(player, theme.title(DemoTheme.PANEL, label), rows = ROWS) {
        DemoTheme.SHAPES.forEach { shape ->
            // One outline per shape, shared by every slot that shape covers — which is what makes
            // a handful of slots behave as a single control. The artwork is the shape's own form,
            // so the triangle is outlined as a triangle rather than boxed.
            val frame = theme.chestFrame(shape.name, shape.cornerFrom, shape.cornerTo, ROWS, outset = FRAME_OUTSET)
            shape.slots.forEach { slot ->
                button(
                    slot,
                    item(Material.PAPER) {
                        // The name is the tooltip's only line, and it is nothing but markers.
                        name(frame)
                        itemModel = theme.itemModel(DemoTheme.BLANK)
                        tooltipStyle = theme.tooltipStyle(DemoTheme.BLANK)
                    },
                ) {
                    onClick {
                        player.sendMessage(
                            Component.text("Clicked the ${shape.name}.", NamedTextColor.AQUA)
                        )
                    }
                }
            }
        }
    }.open()
}
