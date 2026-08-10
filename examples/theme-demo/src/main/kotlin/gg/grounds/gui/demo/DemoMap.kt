package gg.grounds.gui.demo

import gg.grounds.gui.gui
import gg.grounds.gui.item
import gg.grounds.gui.theme.anchorOf
import gg.grounds.gui.theme.title
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.util.concurrent.atomic.AtomicInteger
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.component.DataComponents
import net.minestom.server.entity.Player
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.Material
import net.minestom.server.map.framebuffers.Graphics2DFramebuffer

/**
 * Map ids handed out per screen, so two players never look at each other's picture.
 *
 * Nothing tracks or reuses them: this is a demo of a rendering path, and a server doing it for real
 * would pool them the way it pools any limited handle.
 */
private val nextMapId = AtomicInteger(1)

/**
 * An image the server draws at runtime, shown inside a GUI.
 *
 * Every other picture in this demo ships in the resource pack. That is the one hard limit of the
 * marker approach: a sprite has to exist before a client connects, so a player's own head, a chart
 * of something that changed a minute ago, or a preview of a build nobody has made yet cannot be
 * drawn at all.
 *
 * A map can. Its pixels arrive in a packet, so the server decides them per player and per moment.
 * Getting them onto a screen took reading the client rather than guessing:
 *
 * `MapRenderer.render` submits map contents through `RenderTypes.text(mapTexture)` — a map is drawn
 * by the *text* pipeline with its own texture standing in for the font atlas, which is why themed
 * text and map imagery turn out to be the same mechanism seen from two ends.
 *
 * And exactly one screen in the game draws map contents rather than a map *item*:
 * `CartographyTableScreen`, which calls `graphics.map(...)` for the map in its input slot. So a
 * container GUI that wants runtime imagery is a cartography table, and its layout is fixed — the
 * picture lands at (67, 13) in the window at 66x66, whatever anyone would prefer.
 *
 * Relocating it the way a marker relocates a sprite is possible in principle and is not done here.
 * The corner trick the shader uses reads `fract` of the texel coordinate, and the atlas inset that
 * makes that work for a glyph does not exist for a map: its UVs run exactly 0 to 1, so both corners
 * report the same thing. That needs its own branch, and it needs a client to check it against.
 */
fun openMapDemo(player: Player) {
    val theme = DemoTheme.current()
    val mapId = nextMapId.getAndIncrement()

    player.sendPacket(drawFor(player).preparePacket(mapId))

    gui(
        player,
        theme.title("screen_forge", Component.empty(), anchorOf(InventoryType.CARTOGRAPHY)),
        InventoryType.CARTOGRAPHY,
    ) {
        button(
            0,
            item(Material.FILLED_MAP) {
                name(Component.text("Drawn just now", NamedTextColor.WHITE))
                component(DataComponents.MAP_ID, mapId)
            },
        ) {
            onClick { player.sendMessage(pointsAt()) }
        }
    }
        .open()
}

/**
 * The picture itself, drawn per player because that is the whole point.
 *
 * Java2D onto a 128x128 buffer, which Minestom quantises to the map palette on the way out — 143
 * colours rather than sixteen million, so this is a poster rather than a photograph. Enough for a
 * chart, a silhouette or a name; not enough for a skin at full fidelity.
 */
private fun drawFor(player: Player): Graphics2DFramebuffer {
    val buffer = Graphics2DFramebuffer()
    val canvas = buffer.renderer
    canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    canvas.color = Color(0x1F, 0x1F, 0x1F)
    canvas.fillRect(0, 0, 128, 128)

    // A ring per letter of the name, so two players get visibly different pictures from the same
    // code — the cheapest way to show that the image is not in the pack.
    val name = player.username
    canvas.stroke = BasicStroke(2f)
    name.forEachIndexed { index, letter ->
        val spread = 8 + index * 6
        canvas.color = Color(Color.HSBtoRGB(letter.code / 32f, 0.55f, 0.9f))
        canvas.drawOval(64 - spread, 64 - spread, spread * 2, spread * 2)
    }

    canvas.color = Color(0xD5, 0xD5, 0xD5)
    canvas.drawString(name.take(16), 6, 120)
    canvas.color = Color(0x96, 0x9A, 0xA4)
    canvas.drawString("drawn by the server", 6, 14)
    return buffer
}

private fun pointsAt(): Component =
    Component.text("That picture is in no pack: ", NamedTextColor.GRAY)
        .append(
            Component.text(
                "the server drew it and sent it as map data, which is the one thing a marker cannot do.",
                NamedTextColor.WHITE,
            )
        )
