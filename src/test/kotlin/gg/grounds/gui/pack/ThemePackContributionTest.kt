package gg.grounds.gui.pack

import gg.grounds.gui.theme.PackFormat as GuiPackFormat
import gg.grounds.gui.theme.theme
import gg.grounds.resourcepack.api.ByteArrayEntrySource
import gg.grounds.resourcepack.api.ContributionId
import gg.grounds.resourcepack.api.FileEntrySource
import gg.grounds.resourcepack.api.PackFormatRange as ResourcePackFormatRange
import gg.grounds.resourcepack.api.PackPath
import gg.grounds.resourcepack.api.RenderingCapability
import gg.grounds.resourcepack.testkit.assertVanillaClaimsMatch
import java.awt.image.BufferedImage
import java.lang.reflect.InvocationTargetException
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.div
import kotlin.io.path.outputStream
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ThemePackContributionTest {
    @Test
    fun `rejects duplicate complete planned paths`() {
        val duplicate =
            PlannedThemeEntry(PackPath.of("assets/grounds/font/gui.json")) { generatedText("font") }
        val constructor =
            ThemePackPlan::class.java.getDeclaredConstructor(List::class.java, Set::class.java)
                .apply { isAccessible = true }

        val invocation =
            assertFailsWith<InvocationTargetException> {
                constructor.newInstance(listOf(duplicate, duplicate), emptySet<RenderingCapability>())
            }

        assertTrue(
            "assets/grounds/font/gui.json" in invocation.cause?.message.orEmpty(),
            invocation.cause?.message.orEmpty(),
        )
    }

    @Test
    fun `generated PNG is byte backed and decodes without filesystem output`() {
        val sourceTree = createTempDirectory("assets")
        val before = tree(sourceTree)
        val image = BufferedImage(2, 3, BufferedImage.TYPE_INT_ARGB).apply {
            setRGB(1, 2, 0xFF102030.toInt())
        }

        val source = generatedPng(image)
        val decoded = source.openStream().use { ImageIO.read(it) }

        assertTrue(source is ByteArrayEntrySource)
        assertEquals(2, decoded.width)
        assertEquals(3, decoded.height)
        assertEquals(0xFF102030.toInt(), decoded.getRGB(1, 2))
        assertEquals(before, tree(sourceTree))
    }

    @Test
    fun `wraps a truncated PNG decode failure with theme and source context`() {
        val assets = createTempDirectory("assets")
        val texture = "panels/broken.png"
        val source = assets / texture
        source.parent.createDirectories()
        source.writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00))
        val subject = theme("grounds", GuiPackFormat(88)) { panel("shop", texture, 176, 166) }

        val failure = assertFailsWith<IllegalArgumentException> { subject.toPackContribution(assets) }

        assertTrue("grounds" in failure.message.orEmpty(), failure.message.orEmpty())
        assertTrue(texture in failure.message.orEmpty(), failure.message.orEmpty())
        assertTrue(source.toString() in failure.message.orEmpty(), failure.message.orEmpty())
        assertTrue(failure.cause != null)
    }

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
        assertEquals(
            """{"providers":[{"type":"space","advances":{"\ue000":1,"\ue001":-1,"\ue002":2,"\ue003":-2,"\ue004":4,"\ue005":-4,"\ue006":8,"\ue007":-8,"\ue008":16,"\ue009":-16,"\ue00a":32,"\ue00b":-32,"\ue00c":64,"\ue00d":-64,"\ue00e":128,"\ue00f":-128,"\ue010":256,"\ue011":-256,"\ue012":512,"\ue013":-512,"\ue014":1024,"\ue015":-1024}},{"type":"bitmap","file":"grounds:gui/panels/shop.png","ascent":13,"height":166,"chars":["\ue016"]}]}""",
            text(contribution, "assets/grounds/font/gui.json"),
        )
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

        val rootFailure = assertFailsWith<IllegalArgumentException> { missing.toPackContribution(missingRoot) }
        listOf(
            rootFailure,
            assertFailsWith<IllegalArgumentException> { missing.toPackContribution(assets) },
            assertFailsWith<IllegalArgumentException> { wrongSize.toPackContribution(assets) },
            assertFailsWith<IllegalArgumentException> { transparent.toPackContribution(assets) },
            assertFailsWith<IllegalArgumentException> { oversized.toPackContribution(assets) },
        ).forEach { failure ->
            assertTrue("grounds" in failure.message.orEmpty(), failure.message.orEmpty())
        }
        assertTrue(missingRoot.toString() in rootFailure.message.orEmpty(), rootFailure.message.orEmpty())
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
    fun `contribution owns every vanilla slot highlight and bundle asset`() {
        val assets = createTempDirectory("assets")
        val back = png(assets, "highlight/back.png", 8, 8)
        val front = png(assets, "highlight/front.png", 8, 8)
        val subject =
            theme("grounds", GuiPackFormat(88)) {
                slotHighlight("highlight/back.png", "highlight/front.png")
                bundleFiller()
            }

        val contribution = subject.toPackContribution(assets)

        assertVanillaClaimsMatch(contribution)
        assertEquals(
            setOf(
                "assets/minecraft/lang/en_us.json",
                "assets/minecraft/textures/gui/sprites/container/bundle/bundle_progressbar_border.png",
                "assets/minecraft/textures/gui/sprites/container/bundle/bundle_progressbar_border.png.mcmeta",
                "assets/minecraft/textures/gui/sprites/container/bundle/bundle_progressbar_fill.png",
                "assets/minecraft/textures/gui/sprites/container/bundle/bundle_progressbar_fill.png.mcmeta",
                "assets/minecraft/textures/gui/sprites/container/slot_highlight_back.png",
                "assets/minecraft/textures/gui/sprites/container/slot_highlight_front.png",
            ),
            contribution.vanillaClaims.map { it.path.value }.toSet(),
        )
        assertEquals(7, contribution.vanillaClaims.size)
        assertEquals(
            contribution.vanillaClaims
                .map { it.path.value.removePrefix("assets/minecraft/") }
                .sorted(),
            subject.vanillaOverrides(),
        )

        val vanillaEntries = contribution.entries.filter { it.path.isVanilla }
        assertEquals(7, vanillaEntries.size)
        assertTrue(
            vanillaEntries
                .filter { it.path.value.endsWith("slot_highlight_back.png") || it.path.value.endsWith("slot_highlight_front.png") }
                .all { it.source is FileEntrySource },
        )
        assertTrue(
            vanillaEntries
                .filterNot { it.path.value.endsWith("slot_highlight_back.png") || it.path.value.endsWith("slot_highlight_front.png") }
                .all { it.source is ByteArrayEntrySource },
        )
        assertEquals(back.toList(), bytes(contribution, "assets/minecraft/textures/gui/sprites/container/slot_highlight_back.png").toList())
        assertEquals(front.toList(), bytes(contribution, "assets/minecraft/textures/gui/sprites/container/slot_highlight_front.png").toList())
        assertEquals("{\"item.minecraft.bundle.empty\":\"\",\"item.minecraft.bundle.empty.description\":\"\"}", text(contribution, "assets/minecraft/lang/en_us.json"))
        assertEquals("{\"gui\":{\"scaling\":{\"type\":\"nine_slice\",\"width\":12,\"height\":12,\"border\":2}}}", text(contribution, "assets/minecraft/textures/gui/sprites/container/bundle/bundle_progressbar_border.png.mcmeta"))
        assertEquals("{\"gui\":{\"scaling\":{\"type\":\"nine_slice\",\"width\":6,\"height\":6,\"border\":2}}}", text(contribution, "assets/minecraft/textures/gui/sprites/container/bundle/bundle_progressbar_fill.png.mcmeta"))
        listOf(
            "assets/minecraft/textures/gui/sprites/container/bundle/bundle_progressbar_border.png" to 12,
            "assets/minecraft/textures/gui/sprites/container/bundle/bundle_progressbar_fill.png" to 6,
        ).forEach { (path, size) ->
            val image = bytes(contribution, path).inputStream().use(ImageIO::read)
            assertEquals(size, image.width)
            assertEquals(size, image.height)
            assertEquals(0, image.getRGB(0, 0))
        }
        val generated = createTempDirectory("pack")
        writePack(subject, assets, generated)
        vanillaEntries.forEach { entry ->
            assertTrue(
                Files.readAllBytes(generated.resolve(entry.path.value)).contentEquals(bytes(contribution, entry.path.value)),
                entry.path.value,
            )
        }
    }

    @Test
    fun `rejects a non-square slot highlight with source context`() {
        val assets = createTempDirectory("assets")
        png(assets, "highlight/back.png", 8, 7)
        png(assets, "highlight/front.png", 8, 8)
        val subject =
            theme("grounds", GuiPackFormat(88)) {
                slotHighlight("highlight/back.png", "highlight/front.png")
            }

        val failure = assertFailsWith<IllegalArgumentException> { subject.toPackContribution(assets) }

        assertTrue("grounds" in failure.message.orEmpty(), failure.message.orEmpty())
        assertTrue("highlight/back.png" in failure.message.orEmpty(), failure.message.orEmpty())
        assertTrue("8x7" in failure.message.orEmpty(), failure.message.orEmpty())
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
