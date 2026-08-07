package gg.grounds.gui.demo

import gg.grounds.gui.anvilInput
import gg.grounds.gui.button
import gg.grounds.gui.confirmGui
import gg.grounds.gui.cycleButton
import gg.grounds.gui.gui
import gg.grounds.gui.head
import gg.grounds.gui.item
import gg.grounds.gui.layout
import gg.grounds.gui.pagedGui
import gg.grounds.gui.theme.title
import gg.grounds.gui.toggleButton
import java.time.Duration
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.entity.Player
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.Material
import net.minestom.server.timer.TaskSchedule

/**
 * One entry in the storybook: a name, an icon for the index, a line saying what it is for, and the
 * screen it opens.
 */
private class Story(
    val title: String,
    val icon: Material,
    val note: String,
    val open: (Player) -> Unit,
)

private fun text(value: String, color: NamedTextColor = NamedTextColor.GRAY) =
    Component.text(value, color)

/** Grey filler that says which slot it is sitting in. */
private fun slotMarker(index: Int) =
    item(Material.GRAY_STAINED_GLASS_PANE) {
        name(Component.text("slot $index", NamedTextColor.WHITE))
        lore(text("click anywhere to go back", NamedTextColor.DARK_GRAY))
    }

/**
 * Every container type the protocol defines, opened with each slot labelled by its index.
 *
 * The point is the *layout*: a brewing stand's five slots are not in a row, an anvil's three are
 * not interchangeable, and a lectern has exactly one and no player inventory at all. Seeing them
 * filled and numbered is faster than reading the table.
 *
 * Any click returns to the index, so no slot has to be sacrificed to a back button — which matters
 * here, because sacrificing one would misrepresent the very layout the story exists to show.
 */
private fun containerStory(type: InventoryType): Story =
    Story(
        title = type.name.lowercase(),
        icon = Material.CHEST,
        note = "${type.getSize()} slots, window type ${type.getWindowType()}",
    ) { player ->
        gui(
            player,
            Component.text("${type.name.lowercase()} — click to go back", NamedTextColor.DARK_GRAY),
            type,
        ) {
            (0 until type.getSize()).forEach { slot ->
                button(slot, slotMarker(slot)) { onClick { openStorybook(player) } }
            }
        }
            .open()
    }

/** A back button for the stories that own their slots and can spare one. */
private fun backTo(player: Player) =
    button(
        item(Material.ARROW) {
            name(Component.text("Back to the index", NamedTextColor.YELLOW))
        }
    ) {
        onClick { openStorybook(player) }
    }

private fun libraryStories(): List<Story> =
    listOf(
        Story("layout", Material.PAPER, "shape first, contents second — no slot maths") { player ->
            gui(player, text("layout"), rows = 3) {
                layout("#########", "#  X Y  #", "####B####") {
                    place('#', item(Material.GRAY_STAINED_GLASS_PANE) { name(text("border")) })
                    place('X', item(Material.DIAMOND) { name(text("X")) })
                    place('Y', item(Material.EMERALD) { name(text("Y")) })
                    place('B', item(Material.ARROW) { name(text("back")) }) {
                        onClick { openStorybook(player) }
                    }
                }
            }
                .open()
        },
        Story("signals", Material.REDSTONE, "effect re-runs when a signal it read changes") { player
            ->
            gui(player, text("signals"), rows = 3) {
                var clicks by signal(0)
                effect {
                    button(13, item(Material.EMERALD) { name(text("clicked $clicks times")) }) {
                        onClick { clicks += 1 }
                    }
                }
                button(22, backTo(player).item) { onClick { openStorybook(player) } }
            }
                .open()
        },
        Story("pagination", Material.BOOK, "same window, no flicker between pages") { player ->
            pagedGui(
                player,
                title = { page, pages -> text("page ${page + 1} of $pages") },
                items = (1..40).toList(),
                rows = 3,
                render = { value ->
                    button(item(Material.PAPER) { name(text("entry $value")) }) {
                        onClick { openStorybook(player) }
                    }
                },
            ) {
                navigation()
            }
                .open()
        },
        Story("toggle + cycle", Material.LEVER, "buttons backed by signals") { player ->
            gui(player, text("toggle and cycle"), rows = 3) {
                toggleButton(
                    11,
                    off = item(Material.GRAY_DYE) { name(text("off")) },
                    on = item(Material.LIME_DYE) { name(text("on")) },
                )
                cycleButton(
                    15,
                    values = listOf("low", "medium", "high"),
                    render = { value: String -> item(Material.COMPARATOR) { name(text(value)) } },
                )
                button(22, backTo(player).item) { onClick { openStorybook(player) } }
            }
                .open()
        },
        Story("confirm", Material.BARRIER, "onCancel also covers closing without choosing") { player
            ->
            confirmGui(
                player,
                Component.text("Delete everything?", NamedTextColor.RED),
                onConfirm = { openStorybook(player) },
                onCancel = { openStorybook(player) },
            )
                .open()
        },
        Story("anvil input", Material.ANVIL, "text typed by the player") { player ->
            anvilInput(player, text("Type a name")) { typed ->
                player.sendMessage(Component.text("you typed: $typed", NamedTextColor.AQUA))
                openStorybook(player)
            }
        },
        Story("player head", Material.PLAYER_HEAD, "heads for friends, parties, leaderboards") {
            player ->
            gui(player, text("player head"), rows = 3) {
                button(13, head(player) { name(text(player.username)) }) {
                    onClick { openStorybook(player) }
                }
                button(22, backTo(player).item) { onClick { openStorybook(player) } }
            }
                .open()
        },
        Story("cooldown", Material.CLOCK, "per slot, so re-renders do not reset it") { player ->
            gui(player, text("cooldown"), rows = 3) {
                button(13, item(Material.CLOCK) { name(text("half a second between clicks")) }) {
                    cooldown = Duration.ofMillis(500)
                    onClick { player.sendMessage(text("accepted", NamedTextColor.GREEN)) }
                }
                button(22, backTo(player).item) { onClick { openStorybook(player) } }
            }
                .open()
        },
        Story("animation", Material.MAGMA_CREAM, "frames must differ or the packet is skipped") {
            player ->
            gui(player, text("animation"), rows = 3) {
                animate(
                    13,
                    TaskSchedule.tick(10),
                    listOf(
                        item(Material.RED_WOOL) { name(text("red")) },
                        item(Material.YELLOW_WOOL) { name(text("yellow")) },
                        item(Material.LIME_WOOL) { name(text("green")) },
                    ),
                )
                button(22, backTo(player).item) { onClick { openStorybook(player) } }
            }
                .open()
        },
        Story("themed panel", Material.PAINTING, "artwork behind the window, via the title") {
            player ->
            val theme = DemoTheme.current()
            gui(player, theme.title(DemoTheme.PANEL, text("themed panel")), rows = 3) {
                button(22, backTo(player).item) { onClick { openStorybook(player) } }
            }
                .open()
        },
        Story("item model", Material.GOLD_NUGGET, "item_model replaces what a slot renders") {
            player ->
            val theme = DemoTheme.current()
            gui(player, text("item model"), rows = 3) {
                button(
                    11,
                    item(Material.PAPER) {
                        name(text("paper with a coin's model"))
                        itemModel = theme.itemModel(DemoTheme.ICON)
                    },
                ) {}
                button(15, item(Material.PAPER) { name(text("plain paper")) }) {}
                button(22, backTo(player).item) { onClick { openStorybook(player) } }
            }
                .open()
        },
        Story("tooltip styles", Material.OAK_SIGN, "the sanctioned per-item hover effect") { player
            ->
            val theme = DemoTheme.current()
            gui(player, text("tooltip styles"), rows = 3) {
                button(
                    10,
                    item(Material.GOLD_INGOT) {
                        name(text("gold frame"))
                        tooltipStyle = theme.tooltipStyle(DemoTheme.TOOLTIP)
                    },
                ) {}
                button(
                    13,
                    item(Material.IRON_INGOT) {
                        name(text("steel frame"))
                        tooltipStyle = theme.tooltipStyle(DemoTheme.TOOLTIP_TOOL)
                    },
                ) {}
                button(16, item(Material.STICK) { name(text("vanilla")) }) {}
                button(22, backTo(player).item) { onClick { openStorybook(player) } }
            }
                .open()
        },
        Story("hidden tooltip", Material.STRUCTURE_VOID, "hover shows nothing at all") { player ->
            gui(player, text("hidden tooltip"), rows = 3) {
                button(
                    11,
                    item(Material.IRON_AXE) {
                        name(text("silent"))
                        hideTooltip = true
                    },
                ) {}
                button(15, item(Material.IRON_AXE) { name(text("normal")) }) {}
                button(22, backTo(player).item) { onClick { openStorybook(player) } }
            }
                .open()
        },
        Story("hover frame", Material.TARGET, "shader-driven, scoped to one region") { player ->
            openHoverGui(player)
        },
        Story("slot glow", Material.GLOWSTONE_DUST, "vanilla sprite override — global") { player ->
            gui(player, text("slot glow: /glow toggles it"), rows = 3) {
                (0 until 27).forEach { slot ->
                    button(slot, item(Material.LIGHT_GRAY_STAINED_GLASS_PANE) { name(text(" ")) }) {
                        onClick { openStorybook(player) }
                    }
                }
            }
                .open()
        },
    )

private val stories: List<Story> by lazy {
    libraryStories() + InventoryType.values().map(::containerStory)
}

/**
 * The index: every element the library can build, and every container type the protocol defines,
 * each one page away.
 *
 * Paged on purpose — forty entries do not fit a screen, and the index demonstrating the pagination
 * it is indexing seemed worth more than a second flat menu.
 */
fun openStorybook(player: Player) {
    pagedGui(
        player,
        title = { page, pages -> Component.text("Storybook ${page + 1}/$pages", NamedTextColor.DARK_GRAY) },
        items = stories,
        rows = 6,
        render = { story ->
            button(
                item(story.icon) {
                    name(Component.text(story.title, NamedTextColor.WHITE))
                    lore(text(story.note, NamedTextColor.DARK_GRAY))
                }
            ) {
                onClick { story.open(player) }
            }
        },
    ) {
        navigation()
    }
        .open()
}
