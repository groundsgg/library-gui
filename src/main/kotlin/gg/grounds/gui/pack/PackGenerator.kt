package gg.grounds.gui.pack

import gg.grounds.gui.theme.FRAME_GLYPH_BASE
import gg.grounds.gui.theme.FRAME_SPACE_BASE
import gg.grounds.gui.theme.LAST_PRE_MINOR_FORMAT
import gg.grounds.gui.theme.MeterAxis
import gg.grounds.gui.theme.Spaces
import gg.grounds.gui.theme.TITLE_ASCENT
import gg.grounds.gui.theme.Theme
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.outputStream
import kotlin.io.path.readBytes
import kotlin.io.path.writeText

/**
 * Writes [theme] as a resource pack tree under [out], reading artwork from [assets].
 *
 * Every declared size is checked against the file on disk, so artwork and code can never drift
 * apart unnoticed — a resized PNG fails the build instead of shifting a GUI by a few pixels on
 * players' screens.
 *
 * [out] must be absent or empty: point it at a build directory. Merging into a populated directory
 * would let a texture from a previous theme survive into the pack, and the resulting file is the
 * one thing every player downloads.
 */
fun writePack(theme: Theme, assets: Path, out: Path) {
    require(assets.isDirectory()) { "asset root $assets does not exist" }
    require(!out.exists() || (out.isDirectory() && out.listDirectoryEntries().isEmpty())) {
        "output directory $out must be absent or empty so nothing stale leaks into the pack"
    }

    val root = out / "assets" / theme.namespace
    root.createDirectories()

    writeMcmeta(theme, out)
    writeFont(theme, root)

    theme.panels.forEach { panel ->
        val image =
            install(assets, panel.texture, root / "textures" / "gui" / "panels" / "${panel.id}.png")
        require(image.width == panel.width && image.height == panel.height) {
            "panel '${panel.id}' declares ${panel.width}x${panel.height} but ${panel.texture} is " +
                "${image.width}x${image.height}"
        }
        val onScreen = clientAdvance(image, panel.scale)
        require(panel.effectiveAdvance == onScreen) {
            "panel '${panel.id}' advances $onScreen px on the client, not ${panel.effectiveAdvance} " +
                "— the client trims fully transparent columns off the right of a glyph before it " +
                "measures the advance. Declare advance = $onScreen, or fill the artwork's right edge."
        }
    }

    theme.icons
        .filter { it.empty }
        .forEach { icon ->
            // No texture, no model — the client is told there is nothing to draw at all.
            write(
                root / "items" / "${icon.id}.json",
                Json.obj("model" to Json.obj("type" to Json.string("minecraft:empty"))),
            )
        }

    theme.icons
        .filterNot { it.empty }
        .forEach { icon ->
            install(assets, icon.texture!!, root / "textures" / "item" / "${icon.id}.png")
            val model = "${theme.namespace}:item/${icon.id}"
            write(
                root / "models" / "item" / "${icon.id}.json",
                Json.obj(
                    "parent" to Json.string("minecraft:item/generated"),
                    "textures" to Json.obj("layer0" to Json.string(model)),
                ),
            )
            // The item model *definition* is what the item_model component resolves against; the
            // model
            // above is what it points at.
            write(
                root / "items" / "${icon.id}.json",
                Json.obj(
                    "model" to
                        Json.obj(
                            "type" to Json.string("minecraft:model"),
                            "model" to Json.string(model),
                        )
                ),
            )
        }

    if (theme.frames.isNotEmpty()) writeHoverFrames(theme, assets, out, root)

    if (theme.bundleFiller) writeBundleBlanks(out)

    theme.slotHighlight?.let { highlight ->
        // Deliberately not under the theme's namespace: this replaces a vanilla sprite, which is
        // the only reason it works at all — and the reason it applies to every container the player
        // opens, not just the themed ones.
        val vanilla = out / "assets" / "minecraft" / "textures" / "gui" / "sprites" / "container"
        listOf("slot_highlight_back" to highlight.back, "slot_highlight_front" to highlight.front)
            .forEach { (sprite, texture) ->
                val image = install(assets, texture, vanilla / "$sprite.png")
                // The client blits these into a 24x24 box; a non-square source would be stretched
                // out of shape rather than scaled.
                require(image.width == image.height) {
                    "slot highlight '$texture' is ${image.width}x${image.height}; the client draws " +
                        "it as a square, so the artwork has to be one"
                }
            }
    }

    val sprites = root / "textures" / "gui" / "sprites" / "tooltip"
    theme.tooltips.forEach { tooltip ->
        listOf("background" to tooltip.background, "frame" to tooltip.frame).forEach {
            (part, texture) ->
            val target = sprites / "${tooltip.id}_$part.png"
            val image = install(assets, texture, target)
            val width = image.width
            val height = image.height
            // Two borders have to fit inside the sprite, or the centre slice comes out negative and
            // the client draws the frame inside out. Only checkable here, where the size is known.
            require(tooltip.border * 2 <= minOf(width, height)) {
                "tooltip '${tooltip.id}' declares a ${tooltip.border}px border but '$texture' is " +
                    "only ${width}x$height; two borders must fit inside the sprite"
            }
            // Nine-slice, or the corners smear as the client stretches the sprite to the tooltip's
            // size — tooltips are sized by their text, never by us.
            write(
                Path.of("$target.mcmeta"),
                Json.obj(
                    "gui" to
                        Json.obj(
                            "scaling" to
                                Json.obj(
                                    "type" to Json.string("nine_slice"),
                                    "width" to Json.number(width),
                                    "height" to Json.number(height),
                                    "border" to Json.number(tooltip.border),
                                )
                        )
                ),
            )
        }
    }
}

/**
 * Zips [packDir] into [archive] and returns the archive's SHA-1, which is what
 * `Player.sendResourcePacks` needs alongside the download URL.
 *
 * Entries are sorted and stamped with a fixed timestamp, so the same theme always produces the same
 * bytes and therefore the same hash. Without that, every build would look like a new pack to every
 * client and re-download it.
 */
fun zipPack(packDir: Path, archive: Path): String {
    require(packDir.isDirectory()) { "pack directory $packDir does not exist" }
    archive.parent?.createDirectories()

    val files =
        Files.walk(packDir)
            .use { paths -> paths.filter { it.isRegularFile() }.toList() }
            .sortedBy { packDir.relativize(it).joinToString("/") }

    ZipOutputStream(archive.outputStream().buffered()).use { zip ->
        files.forEach { file ->
            val entry = ZipEntry(packDir.relativize(file).joinToString("/"))
            entry.setTimeLocal(ZIP_EPOCH)
            zip.putNextEntry(entry)
            zip.write(file.readBytes())
            zip.closeEntry()
        }
    }
    return sha1(archive)
}

/**
 * Blanks what an empty bundle's tooltip draws of its own accord.
 *
 * A bundle is the only carrier whose tooltip survives a carried stack, which is why a themed screen
 * might want one — but its tooltip paints a progress bar and says "Empty" underneath whatever the
 * theme put there. Both are silenced here: two transparent sprites and two blank language keys.
 *
 * Global, but narrowly so. The sprite paths sit under `container/bundle/` and are referenced from a
 * single client class, and the language keys belong to bundles alone. Nothing else changes.
 */
private fun writeBundleBlanks(out: Path) {
    val sprites =
        out / "assets" / "minecraft" / "textures" / "gui" / "sprites" / "container" / "bundle"
    sprites.createDirectories()
    listOf("bundle_progressbar_border" to 12, "bundle_progressbar_fill" to 6).forEach { (name, size)
        ->
        val blank = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        (sprites / "$name.png").outputStream().buffered().use { ImageIO.write(blank, "png", it) }
        // The client nine-slices these, so the metadata has to survive the blanking too.
        write(
            Path.of("${sprites / "$name.png"}.mcmeta"),
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
            ),
        )
    }
    // Language files merge per key, so this replaces exactly these two strings and nothing else.
    write(
        out / "assets" / "minecraft" / "lang" / "en_us.json",
        Json.obj(
            "item.minecraft.bundle.empty" to Json.string(""),
            "item.minecraft.bundle.empty.description" to Json.string(""),
        ),
    )
}

/** The DOS timestamp Gradle also stamps into reproducible archives. */
private val ZIP_EPOCH = LocalDateTime.of(1980, 2, 1, 0, 0, 0)

private fun writeMcmeta(theme: Theme, out: Path) {
    val format = theme.packFormat
    val fields = buildList {
        add("pack_format" to Json.number(format.format))
        add("description" to Json.string(theme.description))
        if (format.minInclusive > LAST_PRE_MINOR_FORMAT) {
            // Above the boundary these two are mandatory and `supported_formats` is rejected
            // outright. A bare int for max means "every minor of that major", which is what a
            // server wants — a pair would pin one minor and read as too old on the next.
            add("min_format" to Json.number(format.minInclusive))
            add("max_format" to Json.number(format.maxInclusive))
        } else {
            add(
                "supported_formats" to
                    Json.obj(
                        "min_inclusive" to Json.number(format.minInclusive),
                        "max_inclusive" to Json.number(format.maxInclusive),
                    )
            )
        }
    }
    write(out / "pack.mcmeta", Json.obj("pack" to Json.obj(*fields.toTypedArray())))
}

private fun writeFont(theme: Theme, root: Path) {
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
    write(
        root / "font" / "${theme.font}.json",
        Json.obj("providers" to Json.array(listOf(space) + bitmaps)),
    )
}

/** Height the frame glyphs are declared at; small, so their advance in the tooltip stays small. */
private const val FRAME_GLYPH_HEIGHT = 8

/** Identity the shader looks for in a marker sprite's data pixels. */
private val FRAME_ID_ARGB = (0xFF shl 24) or (0xFE shl 16) or (0x4E shl 8) or 0x2A

/**
 * Ships the hover frames: the overridden vertex shader, each outline wrapped in the data pixels the
 * shader reads, and a font whose every frame is followed by a space cancelling its advance.
 *
 * The wrapping is why an outline can be any shape and any size. A row is added above and below the
 * artwork carrying two pixels at each end — an identity the shader matches on, and the artwork's
 * own dimensions — mirrored into all four corners so whichever vertex the shader is processing can
 * read them. Those rows are cropped back off before the sprite is drawn.
 *
 * Cancelling the advance matters because the markers ride inside a real tooltip: a glyph that
 * consumed width would widen that tooltip for no visible reason.
 */
/**
 * Substitutes the theme's declared colours into the shader's palette.
 *
 * The bundled source carries a one-entry placeholder so it stays valid GLSL on its own — a shipped
 * pack always gets the real thing, and a theme that declares nothing keeps the placeholder, which
 * is harmless because nothing can then ask for a tint.
 */
private fun withPalette(source: String, theme: Theme): String {
    if (theme.colours.isEmpty()) return source
    val entries =
        theme.colours
            .sortedBy { it.name }
            .joinToString(", ") { colour ->
                val (r, g, b) = listOf(16, 8, 0).map { shift -> (colour.rgb shr shift) and 0xFF }
                "vec3(%.5f, %.5f, %.5f)".format(r / 255.0, g / 255.0, b / 255.0)
            }
    val size = theme.colours.size
    val replaced =
        source.replace(
            "const vec3 GROUNDS_PALETTE[1] = vec3[1](vec3(1.0));",
            "const vec3 GROUNDS_PALETTE[$size] = vec3[$size]($entries);",
        )
    check(replaced != source) { "the bundled text.vsh no longer carries the palette placeholder" }
    return replaced
}

/**
 * A file name for a sprite, from its texture path and whether it is a meter.
 *
 * The axis is part of it because it is written into the sprite's own data pixels — the same PNG
 * declared as a bar and as a plain frame are two different textures once shipped.
 */
private fun spriteName(texture: String, meter: MeterAxis?): String {
    val stem =
        texture.substringAfterLast('/').removeSuffix(".png").replace(Regex("[^a-z0-9_.-]"), "_")
    return if (meter == null) stem else "${stem}_${meter.name.lowercase()}"
}

private fun writeHoverFrames(theme: Theme, assets: Path, out: Path, root: Path) {
    val shader =
        checkNotNull(object {}.javaClass.getResourceAsStream("/gg/grounds/gui/pack/text.vsh")) {
            "the bundled core/text.vsh is missing from this library's resources"
        }
    (out / "assets" / "minecraft" / "shaders" / "core").createDirectories()
    (out / "assets" / "minecraft" / "shaders" / "core" / "text.vsh").writeText(
        withPalette(shader.bufferedReader().use { it.readText() }, theme)
    )

    val providers = mutableListOf<String>()
    val spaces = mutableListOf<Pair<String, String>>()

    // One glyph per distinct sprite rather than per frame. A screen whose empty tiles all sit on
    // flat panel face names a patch per slot and they are the same patch; shipping each of them
    // spends a file, a provider and a codepoint on a copy.
    // Named after the texture rather than after whichever frame happens to mention it first, so
    // reordering declarations cannot rename a file and make every cached pack stale.
    val spriteNames =
        theme.frameSprites.associateWith { (texture, meter) -> spriteName(texture, meter) }
    require(spriteNames.values.distinct().size == spriteNames.size) {
        "two frame textures reduce to the same file name: ${spriteNames.values.groupBy { it }.filterValues { it.size > 1 }.keys}"
    }

    theme.frameSprites.forEachIndexed { index, sprite ->
        val (texture, meter) = sprite
        val name = spriteNames.getValue(sprite)
        val frame = theme.frames.first { it.texture == texture && it.meter == meter }
        val source = assets.resolve(texture)
        require(source.isRegularFile()) { "frame texture '$texture' not found under $assets" }
        val art =
            source.inputStream().buffered().use { ImageIO.read(it) }
                ?: throw IllegalArgumentException("frame '${frame.id}' is not a readable image")
        require(art.width in 4..256 && art.height in 1..256) {
            "frame '${frame.id}' is ${art.width}x${art.height}; it must be at least 4 wide for the " +
                "data pixels and at most 256 in each direction, since the size is carried in a byte"
        }

        val wrapped = BufferedImage(art.width, art.height + 2, BufferedImage.TYPE_INT_ARGB)
        val canvas = wrapped.createGraphics()
        canvas.drawImage(art, 0, 1, null)
        canvas.dispose()
        // Blue was zero here and read by nobody; it now says whether this sprite is a meter.
        val size =
            (0xFF shl 24) or
                ((art.width - 1) shl 16) or
                ((art.height - 1) shl 8) or
                (meter?.code ?: 0)
        listOf(0, art.height + 1).forEach { row ->
            wrapped.setRGB(0, row, FRAME_ID_ARGB)
            wrapped.setRGB(art.width - 1, row, FRAME_ID_ARGB)
            wrapped.setRGB(1, row, size)
            wrapped.setRGB(art.width - 2, row, size)
        }

        val target = root / "textures" / "font" / "frame_$name.png"
        target.parent.createDirectories()
        target.outputStream().buffered().use { ImageIO.write(wrapped, "png", it) }

        providers +=
            Json.obj(
                "type" to Json.string("bitmap"),
                "file" to Json.string("${theme.namespace}:font/frame_$name.png"),
                "ascent" to Json.number(TITLE_ASCENT),
                "height" to Json.number(FRAME_GLYPH_HEIGHT),
                "chars" to
                    Json.array(
                        listOf(Json.string(String(Character.toChars(FRAME_GLYPH_BASE + index))))
                    ),
            )
        spaces +=
            String(Character.toChars(FRAME_SPACE_BASE + index)) to
                Json.number(-scaledAdvance(wrapped, FRAME_GLYPH_HEIGHT))
    }

    providers.add(
        0,
        Json.obj("type" to Json.string("space"), "advances" to Json.obj(*spaces.toTypedArray())),
    )
    write(root / "font" / "hoverframe.json", Json.obj("providers" to Json.array(providers)))
}

/** The advance the client computes for a bitmap glyph declared at [height]. */
private fun scaledAdvance(image: BufferedImage, height: Int): Int {
    var column = image.width - 1
    if (image.colorModel.hasAlpha()) {
        while (column >= 0) {
            if ((0 until image.height).any { y -> (image.getRGB(column, y) ushr 24) != 0 }) break
            column--
        }
    }
    val scale = height.toDouble() / image.height
    return Math.round((column + 1) * scale).toInt() + 1
}

/** Copies [texture] from [assets] to [target] and returns the image, for measuring. */
private fun install(assets: Path, texture: String, target: Path): BufferedImage {
    val source = assets.resolve(texture)
    require(source.isRegularFile()) { "texture '$texture' not found under $assets" }
    target.parent.createDirectories()
    source.copyTo(target, overwrite = true)
    return source.inputStream().buffered().use { ImageIO.read(it) }
        ?: throw IllegalArgumentException("texture '$texture' is not a readable image")
}

/**
 * The advance the client will give this artwork, reproducing what it does when it bakes a bitmap
 * glyph: scan columns from the right, stop at the first that holds a non-transparent pixel, scale
 * that width and add the 1px gap between glyphs.
 *
 * Fully transparent columns on the right are *not* part of the glyph as far as the client is
 * concerned, so artwork with a soft right edge advances less than its own width. That is the whole
 * reason the jump back cannot simply assume the declared size.
 *
 * Artwork without an alpha channel has nothing to trim, so it advances its full width.
 */
private fun clientAdvance(image: BufferedImage, scale: Int): Int {
    var column = image.width - 1
    if (image.colorModel.hasAlpha()) {
        while (column >= 0) {
            val opaque = (0 until image.height).any { y -> (image.getRGB(column, y) ushr 24) != 0 }
            if (opaque) break
            column--
        }
    }
    return (column + 1) * scale + 1
}

private fun write(target: Path, json: String) {
    target.parent.createDirectories()
    target.writeText(json)
}

private fun sha1(file: Path): String {
    val digest = MessageDigest.getInstance("SHA-1")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(1 shl 16)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/**
 * The vanilla paths this theme's pack will overwrite, relative to `assets/minecraft`.
 *
 * Everything else a pack writes lives under its own namespace and cannot collide with anything.
 * These cannot: they are the client's own files, one pack in a stack wins each of them outright,
 * and which one wins is decided by pack order rather than by anyone's intent.
 *
 * So a pack has to be able to say what it claims before it is shipped alongside another. Listing it
 * by hand does not work — the list was written down once as "the text shader and the slot
 * highlight" and was missing the bundle sprites and, more quietly, the language file, which is
 * replaced wholesale by any pack that touches a single key.
 *
 * Derived from the theme rather than from the output, so a caller can ask before generating and a
 * test can hold the two against each other.
 */
fun Theme.vanillaOverrides(): List<String> =
    ThemePackPlan.from(this).vanillaPaths.map { it.value.removePrefix("assets/minecraft/") }
