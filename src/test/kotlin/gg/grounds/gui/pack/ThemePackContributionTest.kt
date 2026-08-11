package gg.grounds.gui.pack

import gg.grounds.gui.theme.PackFormat as GuiPackFormat
import gg.grounds.gui.theme.theme
import gg.grounds.resourcepack.api.ContributionId
import gg.grounds.resourcepack.api.PackFormatRange as ResourcePackFormatRange
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ThemePackContributionTest {
    @Test
    fun `converts the GUI pack format exactly`() {
        val actual = GuiPackFormat(88, minInclusive = 84, maxInclusive = 88).toResourcePackFormat()

        assertEquals(88, actual.format)
        assertEquals(ResourcePackFormatRange(84, 88), actual.range)
    }

    @Test
    fun `converts an empty theme into an empty contribution`() {
        val contribution =
            theme("example", GuiPackFormat(88)).toPackContribution(createTempDirectory("assets"))

        assertEquals(ContributionId.of("example:gui"), contribution.id)
        assertEquals(ResourcePackFormatRange(88, 88), contribution.supportedFormats)
        assertEquals(emptyList(), contribution.entries)
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
}
