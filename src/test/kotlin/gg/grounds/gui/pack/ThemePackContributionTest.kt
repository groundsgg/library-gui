package gg.grounds.gui.pack

import gg.grounds.gui.theme.PackFormat as GuiPackFormat
import gg.grounds.gui.theme.theme
import gg.grounds.resourcepack.api.ByteArrayEntrySource
import gg.grounds.resourcepack.api.ContributionId
import gg.grounds.resourcepack.api.FileEntrySource
import gg.grounds.resourcepack.api.PackFormatRange as ResourcePackFormatRange
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.div
import kotlin.io.path.outputStream
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ThemePackContributionTest {
    @Test
    fun `materializes sorted typed entries for panels icons and tooltips without creating output`() {
        val assets = createTempDirectory("assets")
        val panel = png(assets, "panels/shop.png", 176, 166)
        val coin = png(assets, "icons/coin.png", 16, 16)
        val background = png(assets, "tooltips/gold_background.png", 24, 24)
        val frame = png(assets, "tooltips/gold_frame.png", 24, 24)
        val sourceTree = tree(assets)
        val contribution =
            theme("grounds", GuiPackFormat(88)) {
                    panel("shop", "panels/shop.png", 176, 166)
                    icon("coin", "icons/coin.png")
                    emptyIcon("blank")
                    tooltip("gold", "tooltips/gold_background.png", "tooltips/gold_frame.png")
                }
                .toPackContribution(assets)

        assertEquals(
            listOf(
                "assets/grounds/font/gui.json",
                "assets/grounds/items/blank.json",
                "assets/grounds/items/coin.json",
                "assets/grounds/models/item/coin.json",
                "assets/grounds/textures/gui/panels/shop.png",
                "assets/grounds/textures/gui/sprites/tooltip/gold_background.png",
                "assets/grounds/textures/gui/sprites/tooltip/gold_background.png.mcmeta",
                "assets/grounds/textures/gui/sprites/tooltip/gold_frame.png",
                "assets/grounds/textures/gui/sprites/tooltip/gold_frame.png.mcmeta",
                "assets/grounds/textures/item/coin.png",
            ),
            contribution.entries.map { it.path.value },
        )
        assertEquals(sourceTree, tree(assets))
        assertTrue(contribution.entries.single { it.path.value.endsWith("shop.png") }.source is FileEntrySource)
        assertTrue(contribution.entries.single { it.path.value.endsWith("coin.png") }.source is FileEntrySource)
        assertTrue(contribution.entries.single { it.path.value.endsWith("gold_background.png") }.source is FileEntrySource)
        assertTrue(contribution.entries.single { it.path.value.endsWith("gold_frame.png") }.source is FileEntrySource)
        assertTrue(contribution.entries.filter { it.path.value.endsWith(".json") || it.path.value.endsWith(".mcmeta") }.all { it.source is ByteArrayEntrySource })
        assertEquals(panel.toList(), bytes(contribution, "assets/grounds/textures/gui/panels/shop.png").toList())
        assertEquals(coin.toList(), bytes(contribution, "assets/grounds/textures/item/coin.png").toList())
        assertEquals(background.toList(), bytes(contribution, "assets/grounds/textures/gui/sprites/tooltip/gold_background.png").toList())
        assertEquals(frame.toList(), bytes(contribution, "assets/grounds/textures/gui/sprites/tooltip/gold_frame.png").toList())
        assertEquals("{\"model\":{\"type\":\"minecraft:empty\"}}", text(contribution, "assets/grounds/items/blank.json"))
        assertEquals("{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"grounds:item/coin\"}}", text(contribution, "assets/grounds/models/item/coin.json"))
        assertEquals("{\"model\":{\"type\":\"minecraft:model\",\"model\":\"grounds:item/coin\"}}", text(contribution, "assets/grounds/items/coin.json"))
        assertEquals("{\"gui\":{\"scaling\":{\"type\":\"nine_slice\",\"width\":24,\"height\":24,\"border\":4}}}", text(contribution, "assets/grounds/textures/gui/sprites/tooltip/gold_background.png.mcmeta"))
        assertEquals("{\"gui\":{\"scaling\":{\"type\":\"nine_slice\",\"width\":24,\"height\":24,\"border\":4}}}", text(contribution, "assets/grounds/textures/gui/sprites/tooltip/gold_frame.png.mcmeta"))
        assertTrue("\"file\":\"grounds:gui/panels/shop.png\"" in text(contribution, "assets/grounds/font/gui.json"))
    }

    @Test
    fun `reports namespace and source path for asset validation failures`() {
        val assets = createTempDirectory("assets")
        val missingRoot = createTempDirectory("missing") / "absent"
        val missing = theme("grounds", GuiPackFormat(88)) { panel("shop", "panels/missing.png", 176, 166) }
        val wrongSize = theme("grounds", GuiPackFormat(88)) { panel("shop", "panels/wrong.png", 176, 166) }
        png(assets, "panels/wrong.png", 175, 166)
        val transparent = theme("grounds", GuiPackFormat(88)) { panel("shop", "panels/soft.png", 176, 166) }
        png(assets, "panels/soft.png", 176, 166, opaqueWidth = 100)
        val oversized = theme("grounds", GuiPackFormat(88)) { tooltip("gold", "tooltips/a.png", "tooltips/b.png", border = 4) }
        png(assets, "tooltips/a.png", 6, 6)
        png(assets, "tooltips/b.png", 6, 6)

        listOf(
            assertFailsWith<IllegalArgumentException> { missing.toPackContribution(missingRoot) },
            assertFailsWith<IllegalArgumentException> { missing.toPackContribution(assets) },
            assertFailsWith<IllegalArgumentException> { wrongSize.toPackContribution(assets) },
            assertFailsWith<IllegalArgumentException> { transparent.toPackContribution(assets) },
            assertFailsWith<IllegalArgumentException> { oversized.toPackContribution(assets) },
        ).forEach { failure ->
            assertTrue("grounds" in failure.message.orEmpty(), failure.message.orEmpty())
        }
        assertTrue("panels/missing.png" in assertFailsWith<IllegalArgumentException> { missing.toPackContribution(assets) }.message.orEmpty())
        assertTrue("panels/wrong.png" in assertFailsWith<IllegalArgumentException> { wrongSize.toPackContribution(assets) }.message.orEmpty())
        assertTrue("panels/soft.png" in assertFailsWith<IllegalArgumentException> { transparent.toPackContribution(assets) }.message.orEmpty())
        assertTrue("tooltips/a.png" in assertFailsWith<IllegalArgumentException> { oversized.toPackContribution(assets) }.message.orEmpty())
    }

    @Test
    fun `converts the GUI pack format exactly`() {
        val actual = GuiPackFormat(88, minInclusive = 84, maxInclusive = 88).toResourcePackFormat()

        assertEquals(88, actual.format)
        assertEquals(ResourcePackFormatRange(84, 88), actual.range)
    }

    @Test
    fun `converts an empty theme into a contribution with its font`() {
        val contribution =
            theme("example", GuiPackFormat(88)).toPackContribution(createTempDirectory("assets"))

        assertEquals(ContributionId.of("example:gui"), contribution.id)
        assertEquals(ResourcePackFormatRange(88, 88), contribution.supportedFormats)
        assertEquals(listOf("assets/example/font/gui.json"), contribution.entries.map { it.path.value })
        assertEquals(emptySet(), contribution.vanillaClaims)
        assertEquals(emptySet(), contribution.provides)
        assertEquals(emptySet(), contribution.requires)
    }

    @Test
    fun `rejects a frame theme whose pack range includes an older shader format`() {
        val subject =
            theme("grounds", GuiPackFormat(88, minInclusive = 84, maxInclusive = 88)) {
                frame("outline", "frame/outline.png")
            }

        val failure =
            assertFailsWith<IllegalArgumentException> {
                subject.toPackContribution(createTempDirectory("assets"))
            }

        assertEquals(
            "Theme 'grounds' uses the Minecraft 26.2 text shader and must declare pack format range 88..88, but declares 84..88.",
            failure.message,
        )
    }

    private fun png(assets: Path, name: String, width: Int, height: Int, opaqueWidth: Int = width): ByteArray {
        val target = assets / name
        target.parent.createDirectories()
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        (0 until height).forEach { y ->
            (0 until opaqueWidth).forEach { x -> image.setRGB(x, y, 0xFF203040.toInt()) }
        }
        target.outputStream().buffered().use { ImageIO.write(image, "png", it) }
        return target.readBytes()
    }

    private fun bytes(contribution: gg.grounds.resourcepack.api.PackContribution, path: String): ByteArray =
        contribution.entries.single { it.path.value == path }.source.openStream().use { it.readBytes() }

    private fun text(contribution: gg.grounds.resourcepack.api.PackContribution, path: String): String =
        bytes(contribution, path).decodeToString()

    private fun tree(root: Path): List<String> =
        Files.walk(root).use { paths -> paths.map { root.relativize(it).toString() }.sorted().toList() }
}
