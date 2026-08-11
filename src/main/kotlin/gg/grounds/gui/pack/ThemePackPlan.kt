package gg.grounds.gui.pack

import gg.grounds.gui.theme.Panel
import gg.grounds.gui.theme.Spaces
import gg.grounds.gui.theme.Theme
import gg.grounds.resourcepack.api.PackEntry
import gg.grounds.resourcepack.api.PackEntrySource
import gg.grounds.resourcepack.api.PackPath
import gg.grounds.resourcepack.api.RenderingCapability
import java.nio.file.Path
import kotlin.io.path.isDirectory

internal data class PlannedThemeEntry(val path: PackPath, val source: (Path) -> PackEntrySource)

internal class ThemePackPlan
private constructor(val entries: List<PlannedThemeEntry>, val provides: Set<RenderingCapability>) {
    init {
        val duplicate =
            entries.groupingBy { it.path }.eachCount().entries.firstOrNull { it.value > 1 }
        check(duplicate == null) { "Duplicate planned theme asset path ${duplicate?.key?.value}" }
    }

    val vanillaPaths: List<PackPath> = entries.map { it.path }.filter { it.isVanilla }.sorted()

    fun materialize(assets: Path): List<PackEntry> {
        require(assets.isDirectory()) { "Theme asset root source path $assets is not a directory" }
        return entries.map { planned -> PackEntry(planned.path, planned.source(assets)) }
    }

    companion object {
        fun from(theme: Theme): ThemePackPlan {
            val entries =
                buildList {
                        add(text(theme, "font/${theme.font}.json", fontJson(theme)))
                        theme.panels.forEach { panel ->
                            add(
                                image(
                                    theme,
                                    "textures/gui/panels/${panel.id}.png",
                                    panel.texture,
                                ) { picture ->
                                    validatePanel(theme, panel, picture)
                                }
                            )
                        }
                        theme.icons.forEach { icon ->
                            if (icon.empty) {
                                add(text(theme, "items/${icon.id}.json", emptyIconJson()))
                            } else {
                                val model = "${theme.namespace}:item/${icon.id}"
                                add(
                                    text(theme, "models/item/${icon.id}.json", iconModelJson(model))
                                )
                                add(text(theme, "items/${icon.id}.json", iconDefinitionJson(model)))
                                add(
                                    image(
                                        theme,
                                        "textures/item/${icon.id}.png",
                                        checkNotNull(icon.texture),
                                    )
                                )
                            }
                        }
                        theme.tooltips.forEach { tooltip ->
                            listOf("background" to tooltip.background, "frame" to tooltip.frame)
                                .forEach { (part, texture) ->
                                    val target =
                                        "textures/gui/sprites/tooltip/${tooltip.id}_${part}.png"
                                    add(
                                        image(theme, target, texture) { picture ->
                                            validateTooltip(
                                                theme,
                                                tooltip.id,
                                                texture,
                                                tooltip.border,
                                                picture,
                                            )
                                        }
                                    )
                                    add(
                                        PlannedThemeEntry(path(theme, "$target.mcmeta")) { assets ->
                                            val picture =
                                                checkedImage(theme, assets, texture) { image ->
                                                    validateTooltip(
                                                        theme,
                                                        tooltip.id,
                                                        texture,
                                                        tooltip.border,
                                                        image,
                                                    )
                                                }
                                            generatedText(
                                                tooltipMcmeta(
                                                    picture.width,
                                                    picture.height,
                                                    tooltip.border,
                                                )
                                            )
                                        }
                                    )
                                }
                        }
                        if (theme.bundleFiller) {
                            listOf(
                                    "bundle_progressbar_border" to 12,
                                    "bundle_progressbar_fill" to 6,
                                )
                                .forEach { (name, size) ->
                                    val target = "textures/gui/sprites/container/bundle/$name.png"
                                    add(
                                        PlannedThemeEntry(vanillaPath(target)) {
                                            generatedBlankPng(size)
                                        }
                                    )
                                    add(
                                        PlannedThemeEntry(vanillaPath("$target.mcmeta")) {
                                            generatedText(bundleMcmeta(size))
                                        }
                                    )
                                }
                            add(
                                PlannedThemeEntry(vanillaPath("lang/en_us.json")) {
                                    generatedText(bundleLanguageJson())
                                }
                            )
                        }
                        theme.slotHighlight?.let { highlight ->
                            add(
                                vanillaImage(
                                    theme,
                                    "textures/gui/sprites/container/slot_highlight_back.png",
                                    highlight.back,
                                )
                            )
                            add(
                                vanillaImage(
                                    theme,
                                    "textures/gui/sprites/container/slot_highlight_front.png",
                                    highlight.front,
                                )
                            )
                        }
                    }
                    .sortedBy { it.path }
            return ThemePackPlan(entries, emptySet())
        }

        private fun path(theme: Theme, value: String): PackPath =
            PackPath.of("assets/${theme.namespace}/$value")

        private fun vanillaPath(value: String): PackPath = PackPath.of("assets/minecraft/$value")

        private fun text(theme: Theme, value: String, contents: String): PlannedThemeEntry =
            PlannedThemeEntry(path(theme, value)) { generatedText(contents) }

        private fun image(
            theme: Theme,
            value: String,
            texture: String,
            validate: (java.awt.image.BufferedImage) -> Unit = {},
        ): PlannedThemeEntry =
            PlannedThemeEntry(path(theme, value)) { assets ->
                checkedImageFile(theme, assets, texture, validate)
            }

        private fun vanillaImage(theme: Theme, value: String, texture: String): PlannedThemeEntry =
            PlannedThemeEntry(vanillaPath(value)) { assets ->
                checkedImageFile(theme, assets, texture) { image ->
                    validateSlotHighlight(theme, texture, image)
                }
            }
    }
}

private fun validateSlotHighlight(
    theme: Theme,
    texture: String,
    image: java.awt.image.BufferedImage,
) {
    require(image.width == image.height) {
        "Theme '${theme.namespace}' slot highlight source path '$texture' is ${image.width}x${image.height}; the client draws it as a square, so the artwork has to be one"
    }
}

private fun validatePanel(theme: Theme, panel: Panel, image: java.awt.image.BufferedImage) {
    require(image.width == panel.width && image.height == panel.height) {
        "Theme '${theme.namespace}' panel '${panel.id}' source path '${panel.texture}' declares ${panel.width}x${panel.height} but is ${image.width}x${image.height}"
    }
    val onScreen = themeClientAdvance(image, panel.scale)
    require(panel.effectiveAdvance == onScreen) {
        "Theme '${theme.namespace}' panel '${panel.id}' source path '${panel.texture}' advances $onScreen px on the client, not ${panel.effectiveAdvance} — the client trims fully transparent columns off the right of a glyph before it measures the advance. Declare advance = $onScreen, or fill the artwork's right edge."
    }
}

private fun validateTooltip(
    theme: Theme,
    id: String,
    texture: String,
    border: Int,
    image: java.awt.image.BufferedImage,
) {
    require(border * 2 <= minOf(image.width, image.height)) {
        "Theme '${theme.namespace}' tooltip '$id' source path '$texture' declares a ${border}px border but is only ${image.width}x${image.height}; two borders must fit inside the sprite"
    }
}

private fun fontJson(theme: Theme): String {
    val space =
        Json.obj(
            "type" to Json.string("space"),
            "advances" to
                Json.obj(
                    *Spaces.advances()
                        .map { (glyph, px) -> glyph to Json.number(px) }
                        .toTypedArray()
                ),
        )
    val bitmaps =
        theme.panels
            .sortedBy { it.id }
            .map { panel ->
                Json.obj(
                    "type" to Json.string("bitmap"),
                    "file" to Json.string("${theme.namespace}:gui/panels/${panel.id}.png"),
                    "ascent" to Json.number(panel.ascent),
                    "height" to Json.number(panel.drawnHeight),
                    "chars" to Json.array(listOf(Json.string(theme.glyph(panel.id)))),
                )
            }
    return Json.obj("providers" to Json.array(listOf(space) + bitmaps))
}

private fun emptyIconJson(): String =
    Json.obj("model" to Json.obj("type" to Json.string("minecraft:empty")))

private fun iconModelJson(model: String): String =
    Json.obj(
        "parent" to Json.string("minecraft:item/generated"),
        "textures" to Json.obj("layer0" to Json.string(model)),
    )

private fun iconDefinitionJson(model: String): String =
    Json.obj(
        "model" to Json.obj("type" to Json.string("minecraft:model"), "model" to Json.string(model))
    )

private fun tooltipMcmeta(width: Int, height: Int, border: Int): String =
    Json.obj(
        "gui" to
            Json.obj(
                "scaling" to
                    Json.obj(
                        "type" to Json.string("nine_slice"),
                        "width" to Json.number(width),
                        "height" to Json.number(height),
                        "border" to Json.number(border),
                    )
            )
    )

private fun bundleMcmeta(size: Int): String =
    Json.obj(
        "gui" to
            Json.obj(
                "scaling" to
                    Json.obj(
                        "type" to Json.string("nine_slice"),
                        "width" to Json.number(size),
                        "height" to Json.number(size),
                        "border" to Json.number(2),
                    )
            )
    )

private fun bundleLanguageJson(): String =
    Json.obj(
        "item.minecraft.bundle.empty" to Json.string(""),
        "item.minecraft.bundle.empty.description" to Json.string(""),
    )
