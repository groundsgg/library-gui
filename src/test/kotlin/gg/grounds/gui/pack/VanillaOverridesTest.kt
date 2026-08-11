package gg.grounds.gui.pack

import gg.grounds.gui.theme.PackFormat
import gg.grounds.gui.theme.Theme
import gg.grounds.gui.theme.theme
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.createDirectories
import kotlin.io.path.isRegularFile
import kotlin.io.path.outputStream
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a pack claims of the client's own files, against what it actually writes.
 *
 * Everything under a pack's own namespace is private to it. These paths are not: one pack in a
 * stack wins each of them outright, and which one is decided by pack order. A platform shipping
 * several packs has to know who owns what before it ships them, and a list maintained by hand goes
 * stale — the first one written down said "the text shader and the slot highlight" and was missing
 * the bundle sprites and the language file.
 */
class VanillaOverridesTest {
    private val format = PackFormat(88)

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    private fun written(subject: Theme, assets: Path): List<String> {
        val out = Files.createTempDirectory("pack")
        writePack(subject, assets, out)
        val vanilla = out.resolve("assets/minecraft")
        if (!Files.exists(vanilla)) return emptyList()
        return vanilla
            .walk()
            .filter { it.isRegularFile() }
            .map { it.relativeTo(vanilla).toString().replace('\\', '/') }
            .sorted()
            .toList()
    }

    private fun assets(vararg textures: String): Path {
        val dir = Files.createTempDirectory("assets")
        textures.forEach { name ->
            val file = dir.resolve(name)
            file.parent.createDirectories()
            val image = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)
            file.outputStream().buffered().use { ImageIO.write(image, "png", it) }
        }
        return dir
    }

    @Test
    fun `a theme claiming nothing global writes nothing global`() {
        val subject = theme("grounds", format) { icon("coin", "icons/coin.png") }
        assertEquals(emptyList(), subject.vanillaOverrides())
        assertEquals(emptyList(), written(subject, assets("icons/coin.png")))
    }

    @Test
    fun `what the theme claims is exactly what lands under assets slash minecraft`() {
        val subject =
            theme("grounds", format) {
                frame("outline", "frame/outline.png")
                slotHighlight("hl/back.png", "hl/front.png")
                bundleFiller()
            }
        val expected = subject.vanillaOverrides()
        assertEquals(
            listOf(
                "lang/en_us.json",
                "shaders/core/text.vsh",
                "textures/gui/sprites/container/bundle/bundle_progressbar_border.png",
                "textures/gui/sprites/container/bundle/bundle_progressbar_border.png.mcmeta",
                "textures/gui/sprites/container/bundle/bundle_progressbar_fill.png",
                "textures/gui/sprites/container/bundle/bundle_progressbar_fill.png.mcmeta",
                "textures/gui/sprites/container/slot_highlight_back.png",
                "textures/gui/sprites/container/slot_highlight_front.png",
            ),
            expected,
        )
        assertEquals(8, expected.size)
        assertEquals(
            expected,
            written(subject, assets("frame/outline.png", "hl/back.png", "hl/front.png")),
        )
    }

    @Test
    fun `a frame theme retains its legacy shader override`() {
        val subject = theme("grounds", format) { frame("outline", "frame/outline.png") }

        assertEquals(listOf("shaders/core/text.vsh"), subject.vanillaOverrides())
        assertEquals(1, subject.vanillaOverrides().count { it == "shaders/core/text.vsh" })
        assertEquals(subject.vanillaOverrides(), written(subject, assets("frame/outline.png")))
    }

    @Test
    fun `each claim is conditional on the feature that needs it`() {
        val bundles = theme("grounds", format) { bundleFiller() }
        assertTrue(bundles.vanillaOverrides().none { it.startsWith("shaders/") })
        assertEquals(bundles.vanillaOverrides(), written(bundles, assets()))
    }
}
