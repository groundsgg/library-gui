package gg.grounds.gui.pack

import gg.grounds.gui.theme.PackFormat
import gg.grounds.gui.theme.theme
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PackGeneratorTest {
    private val assets = createTempDirectory("assets")
    private val out = createTempDirectory("pack") / "out"

    /**
     * Writes opaque artwork. Opaque matters: the client trims fully transparent columns off the
     * right of a glyph before measuring its advance, so a blank image would advance 1px and the
     * generator would rightly reject a panel declaring anything else.
     */
    private fun png(name: String, width: Int, height: Int): String {
        val target = assets / name
        target.parent.createDirectories()
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        (0 until height).forEach { y ->
            (0 until width).forEach { x -> image.setRGB(x, y, 0xFF203040.toInt()) }
        }
        target.outputStream().buffered().use { ImageIO.write(image, "png", it) }
        return name
    }

    private fun fullTheme() =
        theme("grounds", PackFormat(88, minInclusive = 84, maxInclusive = 88)) {
            description = "Grounds GUI"
            panel("shop", png("panels/shop.png", 176, 166), 176, 166, offsetY = -6)
            icon("sword", png("icons/sword.png", 16, 16))
            tooltip(
                "gold",
                png("tooltips/gold_bg.png", 24, 24),
                png("tooltips/gold_frame.png", 24, 24),
            )
        }

    @Test
    fun `writes the file tree the client resolves against`() {
        val subject = fullTheme()
        writePack(subject, assets, out)
        val root = out / "assets" / "grounds"

        assertTrue((out / "pack.mcmeta").exists())
        assertTrue((root / "font" / "gui.json").exists())
        assertTrue((root / "textures" / "gui" / "panels" / "shop.png").exists())
        assertTrue((root / "textures" / "item" / "sword.png").exists())
        assertTrue((root / "models" / "item" / "sword.json").exists())
        assertTrue((root / "items" / "sword.json").exists())
        assertTrue(
            (root / "textures" / "gui" / "sprites" / "tooltip" / "gold_background.png").exists()
        )
        assertTrue(
            (root / "textures" / "gui" / "sprites" / "tooltip" / "gold_frame.png.mcmeta").exists()
        )
    }

    @Test
    fun `the font declares the space ladder and the panel bitmap`() {
        val subject = fullTheme()
        writePack(subject, assets, out)
        val font = (out / "assets" / "grounds" / "font" / "gui.json").readText()

        assertTrue("\"type\":\"space\"" in font, font)
        assertTrue("\"type\":\"bitmap\"" in font, font)
        assertTrue("\"file\":\"grounds:gui/panels/shop.png\"" in font, font)
        // offsetY -6 lifts the artwork six pixels above the title's top row.
        assertTrue("\"ascent\":13" in font, font)
        assertTrue("\"height\":166" in font, font)
        assertTrue("\\u%04x".format(subject.glyph("shop").codePointAt(0)) in font, font)
    }

    @Test
    fun `above format 64 the mcmeta uses min_format and max_format, never supported_formats`() {
        writePack(fullTheme(), assets, out)
        val mcmeta = (out / "pack.mcmeta").readText()
        assertTrue("\"pack_format\":88" in mcmeta, mcmeta)
        assertTrue("\"min_format\":84" in mcmeta, mcmeta)
        assertTrue("\"max_format\":88" in mcmeta, mcmeta)
        // The client refuses this key above format 64 and then cannot parse the metadata at all.
        assertTrue("supported_formats" !in mcmeta, mcmeta)
    }

    @Test
    fun `at or below format 64 the mcmeta keeps the older supported_formats shape`() {
        val subject =
            theme("grounds", PackFormat(46, minInclusive = 42, maxInclusive = 46)) {
                panel("shop", png("panels/shop.png", 176, 166), 176, 166)
            }
        writePack(subject, assets, out)
        val mcmeta = (out / "pack.mcmeta").readText()
        assertTrue("\"supported_formats\"" in mcmeta, mcmeta)
        assertTrue("\"min_inclusive\":42" in mcmeta, mcmeta)
        assertTrue("min_format" !in mcmeta, mcmeta)
    }

    @Test
    fun `a range straddling the shape boundary is refused rather than emitted wrong`() {
        assertFailsWith<IllegalArgumentException> {
            PackFormat(88, minInclusive = 60, maxInclusive = 88)
        }
    }

    @Test
    fun `artwork with a transparent right edge fails, naming the advance the client will use`() {
        // 176 wide but only the left 100 columns carry any pixels: the client trims the rest before
        // it measures, so the declared advance of 177 is 76px too generous.
        val name = "panels/soft.png"
        val target = assets / name
        target.parent.createDirectories()
        val image = BufferedImage(176, 166, BufferedImage.TYPE_INT_ARGB)
        (0 until 166).forEach { y ->
            (0 until 100).forEach { x -> image.setRGB(x, y, 0xFF203040.toInt()) }
        }
        target.outputStream().buffered().use { ImageIO.write(image, "png", it) }

        val subject = theme("grounds", PackFormat(88)) { panel("soft", name, 176, 166) }
        val failure = assertFailsWith<IllegalArgumentException> { writePack(subject, assets, out) }
        assertTrue("advance = 101" in failure.message.orEmpty(), failure.message.orEmpty())
    }

    @Test
    fun `a slot highlight overrides the vanilla sprite, not a themed one`() {
        val subject =
            theme("grounds", PackFormat(88)) {
                slotHighlight(png("hl/back.png", 24, 24), png("hl/front.png", 24, 24))
            }
        writePack(subject, assets, out)
        val vanilla = out / "assets" / "minecraft" / "textures" / "gui" / "sprites" / "container"

        // Under minecraft, not grounds: replacing the client's own sprite is the only way this
        // works, and the reason it cannot be scoped to one GUI.
        assertTrue((vanilla / "slot_highlight_back.png").exists())
        assertTrue((vanilla / "slot_highlight_front.png").exists())
        assertTrue(
            (out / "assets" / "grounds").listDirectoryEntries().none { "container" in it.name }
        )
    }

    @Test
    fun `a non-square slot highlight fails the build`() {
        val subject =
            theme("grounds", PackFormat(88)) {
                slotHighlight(png("hl/back.png", 24, 12), png("hl/front.png", 24, 24))
            }
        assertFailsWith<IllegalArgumentException> { writePack(subject, assets, out) }
    }

    @Test
    fun `a theme without a slot highlight touches nothing under minecraft`() {
        writePack(fullTheme(), assets, out)
        assertTrue(!(out / "assets" / "minecraft").exists())
    }

    @Test
    fun `a tooltip border too large for its sprite fails the build`() {
        val subject =
            theme("grounds", PackFormat(88)) {
                tooltip(
                    "tiny",
                    png("tooltips/a.png", 6, 6),
                    png("tooltips/b.png", 6, 6),
                    border = 4,
                )
            }
        assertFailsWith<IllegalArgumentException> { writePack(subject, assets, out) }
    }

    @Test
    fun `tooltip sprites are nine-sliced at the size of the real artwork`() {
        writePack(fullTheme(), assets, out)
        val mcmeta =
            (out /
                    "assets" /
                    "grounds" /
                    "textures" /
                    "gui" /
                    "sprites" /
                    "tooltip" /
                    "gold_background.png.mcmeta")
                .readText()
        assertTrue("\"type\":\"nine_slice\"" in mcmeta, mcmeta)
        assertTrue("\"width\":24" in mcmeta, mcmeta)
        assertTrue("\"border\":4" in mcmeta, mcmeta)
    }

    @Test
    fun `an icon gets both a model and the definition item_model resolves against`() {
        writePack(fullTheme(), assets, out)
        val root = out / "assets" / "grounds"
        assertTrue(
            "\"layer0\":\"grounds:item/sword\"" in
                (root / "models" / "item" / "sword.json").readText()
        )
        assertTrue("\"model\":\"grounds:item/sword\"" in (root / "items" / "sword.json").readText())
    }

    @Test
    fun `artwork that no longer matches its declaration fails the build`() {
        val subject =
            theme("grounds", PackFormat(88)) {
                panel("shop", png("panels/shop.png", 200, 166), 176, 166)
            }
        val failure = assertFailsWith<IllegalArgumentException> { writePack(subject, assets, out) }
        assertTrue("200x166" in failure.message.orEmpty(), failure.message.orEmpty())
    }

    @Test
    fun `a missing texture fails the build`() {
        val subject =
            theme("grounds", PackFormat(88)) { panel("shop", "panels/absent.png", 176, 166) }
        assertFailsWith<IllegalArgumentException> { writePack(subject, assets, out) }
    }

    @Test
    fun `a populated output directory is refused, so nothing stale survives`() {
        out.createDirectories()
        (out / "leftover.txt").writeText("from an older theme")
        assertFailsWith<IllegalArgumentException> { writePack(fullTheme(), assets, out) }
    }

    @Test
    fun `the same theme always zips to the same hash`() {
        val subject = fullTheme()
        writePack(subject, assets, out)
        val other = createTempDirectory("pack2") / "out"
        writePack(subject, assets, other)

        val first = zipPack(out, Path.of("$out.zip"))
        val second = zipPack(other, Path.of("$other.zip"))
        assertEquals(first, second)
        assertEquals(40, first.length)
    }

    @Test
    fun `changed artwork changes the hash, so clients refetch`() {
        writePack(fullTheme(), assets, out)
        val before = zipPack(out, Path.of("$out.zip"))

        val redrawn = createTempDirectory("assets2")
        val other = createTempDirectory("pack3") / "out"
        val subject =
            theme("grounds", PackFormat(88, minInclusive = 84, maxInclusive = 88)) {
                description = "Grounds GUI"
                panel("shop", "panels/shop.png", 176, 166, offsetY = -6)
                icon("sword", "icons/sword.png")
                tooltip("gold", "tooltips/gold_bg.png", "tooltips/gold_frame.png")
            }
        // Same sizes, different pixels: opaque throughout so the advance still matches, but red
        // rather than slate, which is exactly the "artist redrew it" case a hash has to notice.
        listOf(
                "panels/shop.png" to (176 to 166),
                "icons/sword.png" to (16 to 16),
                "tooltips/gold_bg.png" to (24 to 24),
                "tooltips/gold_frame.png" to (24 to 24),
            )
            .forEach { (name, size) ->
                val target = redrawn / name
                target.parent.createDirectories()
                val image = BufferedImage(size.first, size.second, BufferedImage.TYPE_INT_ARGB)
                (0 until size.second).forEach { y ->
                    (0 until size.first).forEach { x -> image.setRGB(x, y, 0xFFB03030.toInt()) }
                }
                target.outputStream().buffered().use { ImageIO.write(image, "png", it) }
            }
        writePack(subject, redrawn, other)

        assertNotEquals(before, zipPack(other, Path.of("$other.zip")))
    }
}
