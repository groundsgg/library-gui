package gg.grounds.gui.pack

import gg.grounds.gui.theme.PackFormat as GuiPackFormat
import gg.grounds.gui.theme.Theme
import gg.grounds.resourcepack.api.ContributionId
import gg.grounds.resourcepack.api.PackContribution
import gg.grounds.resourcepack.api.PackFormat as ResourcePackFormat
import gg.grounds.resourcepack.api.PackFormatRange as ResourcePackFormatRange
import gg.grounds.resourcepack.api.VanillaPathClaim
import java.nio.file.Path
import kotlin.io.path.isDirectory

fun GuiPackFormat.toResourcePackFormat(): ResourcePackFormat =
    ResourcePackFormat(
        format = format,
        range = ResourcePackFormatRange(minInclusive, maxInclusive),
    )

fun Theme.toPackContribution(assets: Path): PackContribution {
    requireShaderFormat()
    require(assets.isDirectory()) { "Theme '$namespace' asset root source path $assets is not a directory" }
    val plan = ThemePackPlan.from(this)
    return PackContribution(
        id = ContributionId.of("$namespace:gui"),
        supportedFormats = packFormat.toResourcePackFormat().range,
        entries = plan.materialize(assets),
        provides = plan.provides,
        vanillaClaims = plan.vanillaPaths.map(::VanillaPathClaim),
    )
}

private fun Theme.requireShaderFormat() {
    if (frames.isNotEmpty()) {
        require(
            packFormat.format == 88 &&
                packFormat.minInclusive == 88 &&
                packFormat.maxInclusive == 88
        ) {
            "Theme '$namespace' uses the Minecraft 26.2 text shader and must declare pack format range 88..88, but declares ${packFormat.minInclusive}..${packFormat.maxInclusive}."
        }
    }
}
