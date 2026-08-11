package gg.grounds.gui.pack

import gg.grounds.gui.theme.MeterAxis
import gg.grounds.gui.theme.PackFormat as GuiPackFormat
import gg.grounds.gui.theme.theme
import gg.grounds.resourcepack.api.ByteArrayEntrySource
import gg.grounds.resourcepack.api.ContributionId
import gg.grounds.resourcepack.api.FileEntrySource
import gg.grounds.resourcepack.api.PackFormatRange as ResourcePackFormatRange
import gg.grounds.resourcepack.api.PackPath
import gg.grounds.resourcepack.api.RenderingCapability
import gg.grounds.resourcepack.api.VanillaPathClaim
import gg.grounds.resourcepack.testkit.assertVanillaClaimsMatch
import java.awt.image.BufferedImage
import java.lang.reflect.InvocationTargetException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ThemePackContributionTest {
    @Test
    fun `shader palette bytes ignore a comma-decimal default locale`() {
        val assets = createTempDirectory("assets")
        png(assets, "frame/outline.png", 4, 1)
        val subject =
            theme("grounds", GuiPackFormat(88)) {
                frame("outline", "frame/outline.png")
                colour("blue", 0x123456)
            }
        val expected =
            bytes(subject.toPackContribution(assets), "assets/minecraft/shaders/core/text.vsh")
        val originalLocale = Locale.getDefault()

        val actual =
            try {
                Locale.setDefault(Locale.GERMANY)
                bytes(subject.toPackContribution(assets), "assets/minecraft/shaders/core/text.vsh")
            } finally {
                Locale.setDefault(originalLocale)
            }

        assertEquals(expected.toList(), actual.toList())
        assertTrue(actual.decodeToString().contains("vec3(0.07059, 0.20392, 0.33725)"))
    }

    @Test
    fun `rejects duplicate complete planned paths`() {
        val duplicate =
            PlannedThemeEntry(PackPath.of("assets/grounds/font/gui.json")) { generatedText("font") }
        val constructor =
            ThemePackPlan::class
                .java
                .getDeclaredConstructor(List::class.java, Set::class.java)
                .apply { isAccessible = true }

        val invocation =
            assertFailsWith<InvocationTargetException> {
                constructor.newInstance(
                    listOf(duplicate, duplicate),
                    emptySet<RenderingCapability>(),
                )
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
        val image =
            BufferedImage(2, 3, BufferedImage.TYPE_INT_ARGB).apply {
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
        source.writeBytes(
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00)
        )
        val subject = theme("grounds", GuiPackFormat(88)) { panel("shop", texture, 176, 166) }

        val failure =
            assertFailsWith<IllegalArgumentException> { subject.toPackContribution(assets) }

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
        assertTrue(
            contribution.entries.single { it.path.value.endsWith("shop.png") }.source
                is FileEntrySource
        )
        assertTrue(
            contribution.entries.single { it.path.value.endsWith("coin.png") }.source
                is FileEntrySource
        )
        assertTrue(
            contribution.entries.single { it.path.value.endsWith("gold_background.png") }.source
                is FileEntrySource
        )
        assertTrue(
            contribution.entries.single { it.path.value.endsWith("gold_frame.png") }.source
                is FileEntrySource
        )
        assertTrue(
            contribution.entries
                .filter { it.path.value.endsWith(".json") || it.path.value.endsWith(".mcmeta") }
                .all { it.source is ByteArrayEntrySource }
        )
        assertEquals(
            panel.toList(),
            bytes(contribution, "assets/grounds/textures/gui/panels/shop.png").toList(),
        )
        assertEquals(
            coin.toList(),
            bytes(contribution, "assets/grounds/textures/item/coin.png").toList(),
        )
        assertEquals(
            background.toList(),
            bytes(contribution, "assets/grounds/textures/gui/sprites/tooltip/gold_background.png")
                .toList(),
        )
        assertEquals(
            frame.toList(),
            bytes(contribution, "assets/grounds/textures/gui/sprites/tooltip/gold_frame.png")
                .toList(),
        )
        assertEquals(
            "{\"model\":{\"type\":\"minecraft:empty\"}}",
            text(contribution, "assets/grounds/items/blank.json"),
        )
        assertEquals(
            "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"grounds:item/coin\"}}",
            text(contribution, "assets/grounds/models/item/coin.json"),
        )
        assertEquals(
            "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"grounds:item/coin\"}}",
            text(contribution, "assets/grounds/items/coin.json"),
        )
        assertEquals(
            "{\"gui\":{\"scaling\":{\"type\":\"nine_slice\",\"width\":24,\"height\":24,\"border\":4}}}",
            text(
                contribution,
                "assets/grounds/textures/gui/sprites/tooltip/gold_background.png.mcmeta",
            ),
        )
        assertEquals(
            "{\"gui\":{\"scaling\":{\"type\":\"nine_slice\",\"width\":24,\"height\":24,\"border\":4}}}",
            text(contribution, "assets/grounds/textures/gui/sprites/tooltip/gold_frame.png.mcmeta"),
        )
        assertEquals(
            """{"providers":[{"type":"space","advances":{"\ue000":1,"\ue001":-1,"\ue002":2,"\ue003":-2,"\ue004":4,"\ue005":-4,"\ue006":8,"\ue007":-8,"\ue008":16,"\ue009":-16,"\ue00a":32,"\ue00b":-32,"\ue00c":64,"\ue00d":-64,"\ue00e":128,"\ue00f":-128,"\ue010":256,"\ue011":-256,"\ue012":512,"\ue013":-512,"\ue014":1024,"\ue015":-1024}},{"type":"bitmap","file":"grounds:gui/panels/shop.png","ascent":13,"height":166,"chars":["\ue016"]}]}""",
            text(contribution, "assets/grounds/font/gui.json"),
        )
    }

    @Test
    fun `reports namespace and source path for asset validation failures`() {
        val assets = createTempDirectory("assets")
        val missingRoot = createTempDirectory("missing") / "absent"
        val missing =
            theme("grounds", GuiPackFormat(88)) { panel("shop", "panels/missing.png", 176, 166) }
        val wrongSize =
            theme("grounds", GuiPackFormat(88)) { panel("shop", "panels/wrong.png", 176, 166) }
        png(assets, "panels/wrong.png", 175, 166)
        val transparent =
            theme("grounds", GuiPackFormat(88)) { panel("shop", "panels/soft.png", 176, 166) }
        png(assets, "panels/soft.png", 176, 166, opaqueWidth = 100)
        val oversized =
            theme("grounds", GuiPackFormat(88)) {
                tooltip("gold", "tooltips/a.png", "tooltips/b.png", border = 4)
            }
        png(assets, "tooltips/a.png", 6, 6)
        png(assets, "tooltips/b.png", 6, 6)

        val rootFailure =
            assertFailsWith<IllegalArgumentException> { missing.toPackContribution(missingRoot) }
        listOf(
                rootFailure,
                assertFailsWith<IllegalArgumentException> { missing.toPackContribution(assets) },
                assertFailsWith<IllegalArgumentException> { wrongSize.toPackContribution(assets) },
                assertFailsWith<IllegalArgumentException> {
                    transparent.toPackContribution(assets)
                },
                assertFailsWith<IllegalArgumentException> { oversized.toPackContribution(assets) },
            )
            .forEach { failure ->
                assertTrue("grounds" in failure.message.orEmpty(), failure.message.orEmpty())
            }
        assertTrue(
            missingRoot.toString() in rootFailure.message.orEmpty(),
            rootFailure.message.orEmpty(),
        )
        assertTrue(
            "panels/missing.png" in
                assertFailsWith<IllegalArgumentException> { missing.toPackContribution(assets) }
                    .message
                    .orEmpty()
        )
        assertTrue(
            "panels/wrong.png" in
                assertFailsWith<IllegalArgumentException> { wrongSize.toPackContribution(assets) }
                    .message
                    .orEmpty()
        )
        assertTrue(
            "panels/soft.png" in
                assertFailsWith<IllegalArgumentException> { transparent.toPackContribution(assets) }
                    .message
                    .orEmpty()
        )
        assertTrue(
            "tooltips/a.png" in
                assertFailsWith<IllegalArgumentException> { oversized.toPackContribution(assets) }
                    .message
                    .orEmpty()
        )
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
        assertEquals(
            listOf("assets/example/font/gui.json"),
            contribution.entries.map { it.path.value },
        )
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
                .filter {
                    it.path.value.endsWith("slot_highlight_back.png") ||
                        it.path.value.endsWith("slot_highlight_front.png")
                }
                .all { it.source is FileEntrySource }
        )
        assertTrue(
            vanillaEntries
                .filterNot {
                    it.path.value.endsWith("slot_highlight_back.png") ||
                        it.path.value.endsWith("slot_highlight_front.png")
                }
                .all { it.source is ByteArrayEntrySource }
        )
        assertEquals(
            back.toList(),
            bytes(
                    contribution,
                    "assets/minecraft/textures/gui/sprites/container/slot_highlight_back.png",
                )
                .toList(),
        )
        assertEquals(
            front.toList(),
            bytes(
                    contribution,
                    "assets/minecraft/textures/gui/sprites/container/slot_highlight_front.png",
                )
                .toList(),
        )
        assertEquals(
            "{\"item.minecraft.bundle.empty\":\"\",\"item.minecraft.bundle.empty.description\":\"\"}",
            text(contribution, "assets/minecraft/lang/en_us.json"),
        )
        assertEquals(
            "{\"gui\":{\"scaling\":{\"type\":\"nine_slice\",\"width\":12,\"height\":12,\"border\":2}}}",
            text(
                contribution,
                "assets/minecraft/textures/gui/sprites/container/bundle/bundle_progressbar_border.png.mcmeta",
            ),
        )
        assertEquals(
            "{\"gui\":{\"scaling\":{\"type\":\"nine_slice\",\"width\":6,\"height\":6,\"border\":2}}}",
            text(
                contribution,
                "assets/minecraft/textures/gui/sprites/container/bundle/bundle_progressbar_fill.png.mcmeta",
            ),
        )
        listOf(
                "assets/minecraft/textures/gui/sprites/container/bundle/bundle_progressbar_border.png" to
                    12,
                "assets/minecraft/textures/gui/sprites/container/bundle/bundle_progressbar_fill.png" to
                    6,
            )
            .forEach { (path, size) ->
                val image = bytes(contribution, path).inputStream().use(ImageIO::read)
                assertEquals(size, image.width)
                assertEquals(size, image.height)
                assertEquals(0, image.getRGB(0, 0))
            }
        val generated = createTempDirectory("pack")
        writePack(subject, assets, generated)
        vanillaEntries.forEach { entry ->
            assertTrue(
                Files.readAllBytes(generated.resolve(entry.path.value))
                    .contentEquals(bytes(contribution, entry.path.value)),
                entry.path.value,
            )
        }
    }

    @Test
    fun `materializes the frame shader marker assets and rendering capability`() {
        val assets = createTempDirectory("assets")
        png(assets, "frame/Outline!.png", 4, 2, pixels = mapOf(1 to 0xFF102030.toInt()))
        val subject =
            theme("grounds", GuiPackFormat(88)) {
                frame("outline", "frame/Outline!.png")
                glyphs("digits", "digit_", mapOf(48 to 6))
                slice("button", "outline", "outline", "outline", capWidth = 2, middleWidth = 4)
                colour("blue", 0x123456)
            }

        val contribution = subject.toPackContribution(assets)
        val shaderPath = "assets/minecraft/shaders/core/text.vsh"
        val shader = text(contribution, shaderPath)
        val bundledShader =
            checkNotNull(javaClass.getResourceAsStream("/gg/grounds/gui/pack/text.vsh")) {
                    "test shader resource is missing"
                }
                .use { it.readBytes().decodeToString() }
        val marker =
            bytes(contribution, "assets/grounds/textures/font/frame__utline_.png")
                .inputStream()
                .use(ImageIO::read)

        assertEquals(
            setOf(RenderingCapability("grounds:text-marker-shader", 1)),
            contribution.provides,
        )
        assertEquals(emptySet(), contribution.requires)
        assertTrue(VanillaPathClaim(PackPath.of(shaderPath)) in contribution.vanillaClaims)
        assertIs<ByteArrayEntrySource>(
            contribution.entries.single { it.path.value == shaderPath }.source
        )
        assertTrue("const vec3 GROUNDS_PALETTE[1] = vec3[1](vec3(1.0));" !in shader)
        assertTrue(
            "const vec3 GROUNDS_PALETTE[1] = vec3[1](vec3(0.07059, 0.20392, 0.33725));" in shader
        )
        assertEquals(
            bundledShader
                .replace(
                    "const vec3 GROUNDS_PALETTE[1] = vec3[1](vec3(1.0));",
                    "const vec3 GROUNDS_PALETTE[1] = vec3[1](vec3(0.07059, 0.20392, 0.33725));",
                )
                .toByteArray()
                .toList(),
            bytes(contribution, shaderPath).toList(),
        )
        assertEquals(
            "{\"providers\":[{\"type\":\"space\",\"advances\":{\"\\uf400\":-9}},{\"type\":\"bitmap\",\"file\":\"grounds:font/frame__utline_.png\",\"ascent\":7,\"height\":8,\"chars\":[\"\\uf000\"]}]}",
            text(contribution, "assets/grounds/font/hoverframe.json"),
        )
        assertTrue(contribution.entries.all { it.source is ByteArrayEntrySource })
        assertEquals(4, marker.width)
        assertEquals(4, marker.height)
        assertEquals(0xFFFE4E2A.toInt(), marker.getRGB(0, 0))
        assertEquals(0xFF030100.toInt(), marker.getRGB(1, 0))
        assertEquals(0xFF102030.toInt(), marker.getRGB(1, 1))
        assertEquals(0xFFFE4E2A.toInt(), marker.getRGB(3, 3))
    }

    @Test
    fun `a frame combined with every global feature claims eight paths exactly once`() {
        val assets = createTempDirectory("assets")
        png(assets, "frame/outline.png", 4, 2)
        png(assets, "highlight/back.png", 8, 8)
        png(assets, "highlight/front.png", 8, 8)
        val subject =
            theme("grounds", GuiPackFormat(88)) {
                frame("outline", "frame/outline.png")
                slotHighlight("highlight/back.png", "highlight/front.png")
                bundleFiller()
            }

        val contribution = subject.toPackContribution(assets)

        assertEquals(8, contribution.vanillaClaims.size)
        assertEquals(8, contribution.vanillaClaims.map { it.path }.toSet().size)
        assertVanillaClaimsMatch(contribution)
    }

    @Test
    fun `a frame-free contribution does not provide the text marker shader capability`() {
        val contribution =
            theme("grounds", GuiPackFormat(88)).toPackContribution(createTempDirectory("assets"))

        assertEquals(emptySet(), contribution.provides)
    }

    @Test
    fun `frame assets are byte-identical to legacy output in stable deduplicated order`() {
        val assets = createTempDirectory("assets")
        png(assets, "frames/shared.png", 4, 1)
        png(assets, "frames/left.png", 5, 2)
        png(assets, "frames/middle.png", 6, 3)
        png(assets, "frames/vertical.png", 7, 4)
        val before = tree(assets)
        val subject =
            theme("grounds", GuiPackFormat(88)) {
                frame("glyph_65", "frames/shared.png")
                frame("also_shared", "frames/shared.png")
                frame("slice_left", "frames/left.png")
                frame("slice_middle", "frames/middle.png", MeterAxis.HORIZONTAL)
                frame("slice_right", "frames/vertical.png", MeterAxis.VERTICAL)
                glyphs("letters", "glyph_", mapOf(65 to 6))
                slice("button", "slice_left", "slice_middle", "slice_right", 2, 6)
                colour("zinc", 0xA0B0C0)
                colour("amber", 0x102030)
            }

        val contribution = subject.toPackContribution(assets)
        val legacy = createTempDirectory("legacy")
        writePack(subject, assets, legacy)
        val typed =
            contribution.entries
                .filter {
                    it.path.value == "assets/minecraft/shaders/core/text.vsh" ||
                        it.path.value == "assets/grounds/font/hoverframe.json" ||
                        it.path.value.startsWith("assets/grounds/textures/font/frame_")
                }
                .associate {
                    it.path.value to it.source.openStream().use { input -> input.readBytes() }
                }
        val written =
            Files.walk(legacy).use { paths ->
                paths
                    .filter { Files.isRegularFile(it) }
                    .map { legacy.relativize(it).toString().replace('\\', '/') }
                    .filter {
                        it == "assets/minecraft/shaders/core/text.vsh" ||
                            it == "assets/grounds/font/hoverframe.json" ||
                            it.startsWith("assets/grounds/textures/font/frame_")
                    }
                    .sorted()
                    .toList()
                    .associateWith { Files.readAllBytes(legacy.resolve(it)) }
            }

        assertEquals(before, tree(assets))
        assertEquals(written.keys.toList(), typed.keys.sorted())
        written.forEach { (path, expected) ->
            assertEquals(expected.toList(), typed.getValue(path).toList(), path)
        }
        assertEquals(6, typed.size)
        assertEquals(
            0xFF050201.toInt(),
            decoded(typed.getValue("assets/grounds/textures/font/frame_middle_horizontal.png"))
                .getRGB(1, 0),
        )
        assertEquals(
            0xFF060302.toInt(),
            decoded(typed.getValue("assets/grounds/textures/font/frame_vertical_vertical.png"))
                .getRGB(1, 0),
        )
        assertTrue(
            text(contribution, "assets/grounds/font/hoverframe.json").contains("frame_left.png")
        )
    }

    @Test
    fun `rejects frame texture names that sanitize to the same path`() {
        val subject =
            theme("grounds", GuiPackFormat(88)) {
                frame("first", "frames/a!.png")
                frame("second", "frames/a?.png")
            }

        val failure =
            assertFailsWith<IllegalArgumentException> {
                subject.toPackContribution(createTempDirectory("assets"))
            }

        assertTrue("a_" in failure.message.orEmpty(), failure.message.orEmpty())
    }

    @Test
    fun `validates frame dimensions and image diagnostics without writing assets`() {
        val assets = createTempDirectory("assets")
        png(assets, "frames/min.png", 4, 1)
        png(assets, "frames/max.png", 256, 256)
        png(assets, "frames/narrow.png", 3, 1)
        png(assets, "frames/wide.png", 257, 1)
        png(assets, "frames/tall.png", 4, 257)
        listOf("frames/min.png", "frames/max.png").forEach { texture ->
            theme("grounds", GuiPackFormat(88)) {
                    frame("frame_${texture.substringAfterLast('/').substringBefore('.')}", texture)
                }
                .toPackContribution(assets)
        }
        listOf("frames/narrow.png", "frames/wide.png", "frames/tall.png").forEach { texture ->
            val failure =
                assertFailsWith<IllegalArgumentException> {
                    theme("grounds", GuiPackFormat(88)) { frame("bad", texture) }
                        .toPackContribution(assets)
                }
            assertTrue("grounds" in failure.message.orEmpty(), failure.message.orEmpty())
            assertTrue(texture in failure.message.orEmpty(), failure.message.orEmpty())
            assertTrue(
                assets.resolve(texture).toString() in failure.message.orEmpty(),
                failure.message.orEmpty(),
            )
        }
        val missing =
            assertFailsWith<IllegalArgumentException> {
                theme("grounds", GuiPackFormat(88)) { frame("missing", "frames/missing.png") }
                    .toPackContribution(assets)
            }
        assertTrue("grounds" in missing.message.orEmpty(), missing.message.orEmpty())
        assertTrue("frames/missing.png" in missing.message.orEmpty(), missing.message.orEmpty())
        assertTrue(
            assets.resolve("frames/missing.png").toString() in missing.message.orEmpty(),
            missing.message.orEmpty(),
        )
        val broken = assets.resolve("frames/broken.png")
        broken.parent.createDirectories()
        broken.writeBytes(
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00)
        )
        val afterInputs = tree(assets)
        val unreadable =
            assertFailsWith<IllegalArgumentException> {
                theme("grounds", GuiPackFormat(88)) { frame("broken", "frames/broken.png") }
                    .toPackContribution(assets)
            }
        assertTrue("grounds" in unreadable.message.orEmpty(), unreadable.message.orEmpty())
        assertTrue(
            "frames/broken.png" in unreadable.message.orEmpty(),
            unreadable.message.orEmpty(),
        )
        assertTrue(broken.toString() in unreadable.message.orEmpty(), unreadable.message.orEmpty())
        assertTrue(unreadable.cause != null)
        assertEquals(afterInputs, tree(assets))
    }

    @Test
    fun `palette ordering is name-stable and an empty palette retains the bundled placeholder`() {
        val assets = createTempDirectory("assets")
        png(assets, "frames/outline.png", 4, 1)
        val ordered =
            theme("grounds", GuiPackFormat(88)) {
                frame("outline", "frames/outline.png")
                colour("zinc", 0xA0B0C0)
                colour("amber", 0x102030)
            }
        val shader =
            text(ordered.toPackContribution(assets), "assets/minecraft/shaders/core/text.vsh")
        val empty =
            text(
                theme("grounds", GuiPackFormat(88)) { frame("outline", "frames/outline.png") }
                    .toPackContribution(assets),
                "assets/minecraft/shaders/core/text.vsh",
            )

        assertTrue(
            shader.indexOf("0.06275, 0.12549, 0.18824") <
                shader.indexOf("0.62745, 0.69020, 0.75294")
        )
        assertTrue("const vec3 GROUNDS_PALETTE[1] = vec3[1](vec3(1.0));" in empty)
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

        val failure =
            assertFailsWith<IllegalArgumentException> { subject.toPackContribution(assets) }

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

    @Test
    @Suppress("DEPRECATION")
    fun `legacy writer rejects a frame theme whose pack range includes an older shader format`() {
        val assets = createTempDirectory("assets")
        png(assets, "frame/outline.png", 8, 8)
        val subject =
            theme("grounds", GuiPackFormat(88, minInclusive = 84, maxInclusive = 88)) {
                frame("outline", "frame/outline.png")
            }

        val failure =
            assertFailsWith<IllegalArgumentException> {
                writePack(subject, assets, createTempDirectory("pack").resolve("out"))
            }

        assertEquals(
            "Theme 'grounds' uses the Minecraft 26.2 text shader and must declare pack format range 88..88, but declares 84..88.",
            failure.message,
        )
    }

    private fun png(
        assets: Path,
        name: String,
        width: Int,
        height: Int,
        opaqueWidth: Int = width,
        pixels: Map<Int, Int> = emptyMap(),
    ): ByteArray {
        val target = assets / name
        target.parent.createDirectories()
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        (0 until height).forEach { y ->
            (0 until opaqueWidth).forEach { x -> image.setRGB(x, y, 0xFF203040.toInt()) }
        }
        pixels.forEach { (index, pixel) -> image.setRGB(index % width, index / width, pixel) }
        target.outputStream().buffered().use { ImageIO.write(image, "png", it) }
        return target.readBytes()
    }

    private fun bytes(
        contribution: gg.grounds.resourcepack.api.PackContribution,
        path: String,
    ): ByteArray =
        contribution.entries
            .single { it.path.value == path }
            .source
            .openStream()
            .use { it.readBytes() }

    private fun text(
        contribution: gg.grounds.resourcepack.api.PackContribution,
        path: String,
    ): String = bytes(contribution, path).decodeToString()

    private fun decoded(bytes: ByteArray): BufferedImage = bytes.inputStream().use(ImageIO::read)

    private fun tree(root: Path): List<String> =
        Files.walk(root).use { paths ->
            paths.map { root.relativize(it).toString() }.sorted().toList()
        }
}
