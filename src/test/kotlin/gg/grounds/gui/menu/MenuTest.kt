package gg.grounds.gui.menu

import gg.grounds.gui.bedrock.BedrockForms
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.minestom.server.MinecraftServer
import net.minestom.server.component.DataComponents
import net.minestom.server.item.Material

/**
 * The menu model is what both renderers read, so these pin the two translations of one entry: the
 * item a Java player sees and the button text a Bedrock player sees. A drift between them is a menu
 * that says different things on the two clients, which is the failure this layer exists to prevent.
 */
class MenuTest {

    private fun plain(component: Component): String =
        PlainTextComponentSerializer.plainText().serialize(component)

    private fun entries(block: MenuBuilder.() -> Unit): List<MenuEntry> =
        MenuBuilder().apply(block).build()

    @Test
    fun `entries keep the order they were declared in`() {
        val built = entries {
            entry("first") { label = Component.text("First") }
            entry("second") { label = Component.text("Second") }
            entry("third") { label = Component.text("Third") }
        }

        assertEquals(listOf("first", "second", "third"), built.map { it.id })
    }

    @Test
    fun `an entry defaults to available with its id as the label`() {
        val entry = entries { entry("lobby-3") }.single()

        assertEquals("lobby-3", plain(entry.label))
        assertEquals(EntryState.AVAILABLE, entry.state)
        assertEquals(Material.PAPER, entry.icon)
        assertEquals(emptyList(), entry.description)
    }

    @Test
    fun `the java item carries the label as its name and the description as lore`() {
        MinecraftServer.init()
        val entry =
            entries {
                    entry("bedwars") {
                        label = Component.text("BedWars")
                        description(Component.text("4 teams of 4"), Component.text("12 queued"))
                        icon = Material.RED_BED
                    }
                }
                .single()

        val item = Menu.itemFor(entry)

        assertEquals(Material.RED_BED, item.material())
        assertEquals("BedWars", plain(item.get(DataComponents.ITEM_NAME)!!))
        assertEquals(
            listOf("4 teams of 4", "12 queued"),
            item.get(DataComponents.LORE)!!.map(::plain),
        )
    }

    @Test
    fun `only a selected entry glows`() {
        MinecraftServer.init()
        val built = entries {
            entry("ranked") { state = EntryState.SELECTED }
            entry("casual") { state = EntryState.AVAILABLE }
            entry("closed") { state = EntryState.UNAVAILABLE }
        }

        val glowing = built.map { Menu.itemFor(it).get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE) }

        assertEquals(listOf(true, null, null), glowing)
    }

    @Test
    fun `the bedrock label puts the description on its own line`() {
        val entry =
            entries {
                    entry("duels") {
                        label = Component.text("Duels")
                        description(Component.text("12 playing"), Component.text("Ranked open"))
                    }
                }
                .single()

        assertEquals("Duels\n12 playing\nRanked open", plain(Menu.formLabel(entry)))
    }

    @Test
    fun `an entry without a description is its label alone`() {
        val entry = entries { entry("duels") { label = Component.text("Duels") } }.single()

        assertEquals("Duels", plain(Menu.formLabel(entry)))
    }

    @Test
    fun `entries built on their own carry the same declarations`() {
        val built = menuEntries {
            entry("a") { label = Component.text("A") }
            entry("b") { state = EntryState.SELECTED }
        }

        assertEquals(listOf("a", "b"), built.map { it.id })
        assertEquals(EntryState.SELECTED, built[1].state)
    }

    @Test
    fun `the simple form json matches cumulus field names`() {
        assertEquals(
            """{"type":"form","title":"Modes","content":"","buttons":[{"text":"BedWars"},{"text":"Duels"}]}""",
            BedrockForms.simpleJson("Modes", "", listOf("BedWars", "Duels")),
        )
    }

    @Test
    fun `a simple form with no buttons is still valid json`() {
        assertEquals(
            """{"type":"form","title":"Modes","content":"","buttons":[]}""",
            BedrockForms.simpleJson("Modes", "", emptyList()),
        )
    }

    @Test
    fun `button text is escaped rather than breaking the payload`() {
        val json = BedrockForms.simpleJson("t", "", listOf("He said \"hi\""))

        assertTrue(json.contains("""{"text":"He said \"hi\""}"""), json)
    }

    @Test
    fun `groups keep their order and their entries`() {
        val groups = menuGroups {
            group("blocks") {
                label = Component.text("Blocks")
                entry("wool")
                entry("clay")
            }
            group("weapons") { label = Component.text("Weapons") }
        }

        assertEquals(listOf("blocks", "weapons"), groups.map { it.id })
        assertEquals(listOf("wool", "clay"), groups.first().entries.map { it.id })
        assertTrue(groups.last().entries.isEmpty())
    }

    @Test
    fun `only the open tab glows`() {
        MinecraftServer.init()
        val group =
            menuGroups {
                    group("blocks") {
                        label = Component.text("Blocks")
                        icon = Material.WHITE_WOOL
                    }
                }
                .single()

        val open = Menu.tabItem(group, selected = true)
        val closed = Menu.tabItem(group, selected = false)

        assertEquals(Material.WHITE_WOOL, open.material())
        assertEquals("Blocks", plain(open.get(DataComponents.ITEM_NAME)!!))
        assertEquals(true, open.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE))
        assertNull(closed.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE))
    }

    @Test
    fun `a tab carries its description as lore`() {
        MinecraftServer.init()
        val group =
            menuGroups {
                    group("ranked") {
                        label = Component.text("Ranked")
                        description(Component.text("Rated"), Component.text("7 queued"))
                        icon = Material.DIAMOND
                    }
                }
                .single()

        val lore = Menu.tabItem(group, selected = false).get(DataComponents.LORE)!!.map(::plain)

        assertEquals(listOf("Rated", "7 queued"), lore)
        assertEquals("Ranked\nRated\n7 queued", plain(Menu.groupLabel(group)))
    }

    @Test
    fun `a group without a description is its label alone on bedrock`() {
        val group = menuGroups { group("casual") { label = Component.text("Casual") } }.single()

        assertEquals("Casual", plain(Menu.groupLabel(group)))
    }

    @Test
    fun `a menu cannot be grouped and flat at once`() {
        val builder =
            MenuBuilder().apply {
                entry("loose")
                group("blocks")
            }

        assertFailsWith<IllegalArgumentException> { builder.validate() }
    }
}
