package gg.grounds.gui.pack

import gg.grounds.gui.theme.PackFormat
import gg.grounds.gui.theme.theme
import gg.grounds.resourcepack.api.PackDefinition
import gg.grounds.resourcepack.api.PackPolicy
import gg.grounds.resourcepack.api.VanillaPathPolicy.ALLOW_CLAIMED
import gg.grounds.resourcepack.builder.DirectoryPackWriter
import gg.grounds.resourcepack.builder.ResourcePackComposer
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.outputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemePackParityTest {
    @Test
    fun `minimal exact 88 theme writes the same assets through both public pack APIs`() =
        assertParity(theme("minimal", PackFormat(88)), createTempDirectory("minimal-assets"))

    @Test
    fun `full exact 88 theme writes the same assets through both public pack APIs`() {
        val assets = createTempDirectory("full-assets")
        val subject =
            theme("grounds", PackFormat(88)) {
                description = "Complete GUI"
                panel("shop", png(assets, "panels/shop.png", 176, 166), 176, 166)
                icon("sword", png(assets, "icons/sword.png", 16, 16))
                emptyIcon("blank")
                tooltip(
                    "gold",
                    png(assets, "tooltips/gold-background.png", 24, 24),
                    png(assets, "tooltips/gold-frame.png", 24, 24),
                )
                slotHighlight(
                    png(assets, "highlight/back.png", 24, 24),
                    png(assets, "highlight/front.png", 24, 24),
                )
                bundleFiller()
                frame("outline", png(assets, "frames/outline.png", 8, 8))
                colour("gold", 0xffcc00)
            }

        assertParity(subject, assets)
    }

    @Test
    @Suppress("DEPRECATION")
    fun `legacy facade signatures remain callable from Kotlin source`() {
        val assets = createTempDirectory("compat-assets")
        val output = createTempDirectory("compat-output").resolve("pack")
        val archive = output.resolveSibling("pack.zip")

        writePack(theme("compat", PackFormat(88)), assets, output)
        zipPack(output, archive)

        assertTrue(Files.exists(archive))
    }

    private fun assertParity(subject: gg.grounds.gui.theme.Theme, assets: Path) {
        val legacy = createTempDirectory("legacy-pack").resolve("pack")
        val composed = createTempDirectory("composed-pack").resolve("pack")

        @Suppress("DEPRECATION") writePack(subject, assets, legacy)
        val contribution = subject.toPackContribution(assets)
        val pack =
            ResourcePackComposer()
                .compose(
                    PackDefinition(
                        subject.description,
                        subject.packFormat.toResourcePackFormat(),
                        policy = PackPolicy(ALLOW_CLAIMED),
                    ),
                    listOf(contribution),
                )
        DirectoryPackWriter().write(pack, composed)

        assertFalse(contribution.entries.any { it.path.value == "pack.mcmeta" })
        assertTrue(Files.isRegularFile(legacy.resolve("pack.mcmeta")))
        assertTrue(Files.isRegularFile(composed.resolve("pack.mcmeta")))
        assertEquals(assetFiles(legacy), assetFiles(composed))
        assetFiles(legacy).forEach { relative ->
            assertContentEquals(
                Files.readAllBytes(legacy.resolve(relative)),
                Files.readAllBytes(composed.resolve(relative)),
                relative,
            )
        }
    }

    private fun assetFiles(root: Path): List<String> =
        Files.walk(root).use { paths ->
            paths
                .filter { it.isRegularFile() }
                .map { root.relativize(it).toString().replace('\\', '/') }
                .filter { it.startsWith("assets/") }
                .sorted()
                .toList()
        }

    private fun png(assets: Path, name: String, width: Int, height: Int): String {
        val target = assets.resolve(name)
        target.parent.createDirectories()
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        (0 until height).forEach { y ->
            (0 until width).forEach { x -> image.setRGB(x, y, 0xff203040.toInt()) }
        }
        target.outputStream().buffered().use { ImageIO.write(image, "png", it) }
        return name
    }
}
