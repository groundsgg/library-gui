package gg.grounds.gui.art

import gg.grounds.gui.layout.ITEM_AREA
import gg.grounds.gui.layout.Rect
import gg.grounds.gui.layout.slotItemX
import gg.grounds.gui.layout.slotItemY
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.OutputStream
import java.nio.file.Path
import java.util.zip.CRC32
import java.util.zip.Deflater
import javax.imageio.ImageIO
import kotlin.io.path.createDirectories
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile
import kotlin.io.path.outputStream

/**
 * Building the artwork a theme is made of.
 *
 * The library used to package pixels without ever producing one: every panel, patch, contour and
 * glyph in the demo comes out of a nine-hundred-line generator that lives beside it, so anyone
 * adopting this library got the runtime half and had to rebuild the other from scratch. These are
 * the four operations that half actually rests on.
 *
 * They work on [BufferedImage] rather than on a sprite type of their own, because the pack
 * generator already does and a second representation would only exist to be converted.
 */

/** Reads a PNG, failing with the path rather than with a null. */
fun readSprite(path: Path): BufferedImage {
    require(path.isRegularFile()) { "no sprite at $path" }
    return path.inputStream().buffered().use { ImageIO.read(it) }
        ?: throw IllegalArgumentException("$path is not an image ImageIO can read")
}

/**
 * Writes a PNG, creating the directory it goes in.
 *
 * Encoded here rather than through ImageIO, for size. ImageIO gives no way to ask for maximum
 * deflate, and the difference is not academic: the demo's market panel came out at 2300 bytes
 * against 1479 for the same pixels, across three hundred files that all ship in the pack a client
 * downloads.
 *
 * Straight RGBA8 with no row filtering, which is what a generator producing flat pixel art wants —
 * filters pay off on photographs and cost bytes on runs of one colour.
 */
fun BufferedImage.writeSprite(path: Path) {
    path.parent?.createDirectories()

    val raw = ByteArray(height * (1 + width * 4))
    var at = 0
    for (row in 0 until height) {
        raw[at++] = 0
        for (column in 0 until width) {
            val argb = getRGB(column, row)
            raw[at++] = (argb shr 16).toByte()
            raw[at++] = (argb shr 8).toByte()
            raw[at++] = argb.toByte()
            raw[at++] = (argb ushr 24).toByte()
        }
    }

    val header =
        ByteArrayOutputStream().also {
            DataOutputStream(it).apply {
                writeInt(width)
                writeInt(height)
                write(byteArrayOf(8, 6, 0, 0, 0))
            }
        }

    path.outputStream().buffered().use { out ->
        out.write(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10))
        out.writeChunk("IHDR", header.toByteArray())
        out.writeChunk("IDAT", deflate(raw))
        out.writeChunk("IEND", ByteArray(0))
    }
}

private fun deflate(data: ByteArray): ByteArray {
    val deflater = Deflater(Deflater.BEST_COMPRESSION)
    return try {
        deflater.setInput(data)
        deflater.finish()
        val out = ByteArrayOutputStream(data.size / 2)
        val buffer = ByteArray(8192)
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer))
        }
        out.toByteArray()
    } finally {
        deflater.end()
    }
}

private fun OutputStream.writeChunk(tag: String, body: ByteArray) {
    val payload = tag.toByteArray(Charsets.US_ASCII) + body
    DataOutputStream(this).apply {
        writeInt(body.size)
        write(payload)
        writeInt(CRC32().also { it.update(payload) }.value.toInt())
    }
}

private const val ALPHA_MASK = 0xFF shl 24

private fun canvas(width: Int, height: Int): BufferedImage {
    require(width > 0 && height > 0) { "a sprite is at least 1x1, got ${width}x$height" }
    return BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
}

/** The part of this sprite [rect] covers, as its own image. */
fun BufferedImage.crop(rect: Rect): BufferedImage = crop(rect.x, rect.y, rect.width, rect.height)

/** A rectangle of this sprite, as its own image. */
fun BufferedImage.crop(x: Int, y: Int, width: Int, height: Int): BufferedImage {
    require(x >= 0 && y >= 0 && x + width <= this.width && y + height <= this.height) {
        "crop ${width}x$height at ($x, $y) leaves a ${this.width}x${this.height} sprite"
    }
    val out = canvas(width, height)
    for (row in 0 until height) {
        for (column in 0 until width) {
            out.setRGB(column, row, getRGB(x + column, y + row))
        }
    }
    return out
}

/**
 * Nearest-neighbour enlargement, which is the only kind pixel art survives.
 *
 * Anything that interpolates turns a two-pixel bevel into a gradient, and a GUI drawn at one scale
 * beside a GUI drawn at another is the most obvious tell there is.
 */
fun BufferedImage.scaled(factor: Int): BufferedImage {
    require(factor >= 1) { "scale factor is at least 1, got $factor" }
    if (factor == 1) return this
    val out = canvas(width * factor, height * factor)
    for (row in 0 until out.height) {
        for (column in 0 until out.width) {
            out.setRGB(column, row, getRGB(column / factor, row / factor))
        }
    }
    return out
}

/**
 * Lifts every pixel toward white by [alpha] out of 255, keeping its own alpha.
 *
 * A hover that tints a patch cannot be a translucent layer: the client draws its own box before any
 * tooltip, so a see-through marker lands on top of it and shows both. Covering opaquely is the only
 * way to replace it, and once the cover is opaque the tint is just a colour — computable here.
 */
fun BufferedImage.lightened(alpha: Int): BufferedImage {
    require(alpha in 0..255) { "alpha is 0..255, got $alpha" }
    val out = canvas(width, height)
    for (row in 0 until height) {
        for (column in 0 until width) {
            val argb = getRGB(column, row)
            val lifted =
                listOf(16, 8, 0).fold(0) { acc, shift ->
                    val channel = (argb shr shift) and 0xFF
                    acc or (((channel + (255 - channel) * alpha / 255) and 0xFF) shl shift)
                }
            out.setRGB(column, row, (argb and ALPHA_MASK) or lifted)
        }
    }
    return out
}

/**
 * Draws this sprite at an arbitrary size the way the client's own nine-slice does.
 *
 * Corners are copied one to one, edges are tiled along their axis and the centre fills the rest, so
 * a button stretched across seven slots keeps a crisp bevel instead of a smeared one. Scaling it
 * instead is the difference between a button that looks native and one that looks like a texture of
 * a button.
 */
fun BufferedImage.nineSlice(width: Int, height: Int, border: Int): BufferedImage {
    require(border > 0) { "border is at least 1, got $border" }
    require(this.width > 2 * border && this.height > 2 * border) {
        "a ${this.width}x${this.height} sprite has no middle band left at border $border"
    }

    fun source(index: Int, span: Int, size: Int): Int =
        when {
            index < border -> index
            index >= span - border -> size - (span - index)
            else -> border + (index - border) % (size - 2 * border)
        }

    val out = canvas(width, height)
    for (row in 0 until height) {
        for (column in 0 until width) {
            out.setRGB(
                column,
                row,
                getRGB(source(column, width, this.width), source(row, height, this.height)),
            )
        }
    }
    return out
}

/** One band of a contour: how far out from the artwork, and how opaque there. */
data class Ring(val radius: Int, val alpha: Int)

/**
 * The outline of this sprite's own silhouette, drawn white so the palette can colour it.
 *
 * Dilating the alpha mask and subtracting the mask leaves exactly the pixels that touch the
 * artwork, so the outline follows the shape rather than boxing it. Rings are painted widest first,
 * so a nearer band overwrites a farther one and a bright core sits inside a softer halo.
 *
 * The result is [inset] pixels larger on every side, because a shape reaching its own edge has no
 * room for a halo otherwise — position the marker at minus that inset to put the artwork back where
 * it was.
 */
fun BufferedImage.contour(
    rings: List<Ring> = listOf(Ring(2, 110), Ring(1, 255)),
    inset: Int = rings.maxOf { it.radius },
): BufferedImage {
    require(rings.isNotEmpty()) { "a contour needs at least one ring" }
    require(rings.all { it.radius > 0 && it.alpha in 1..255 }) {
        "rings need a radius and an alpha"
    }

    val mask = HashSet<Long>()
    for (row in 0 until height) {
        for (column in 0 until width) {
            if (getRGB(column, row) ushr 24 != 0) {
                mask += key(column + inset, row + inset)
            }
        }
    }

    val out = canvas(width + 2 * inset, height + 2 * inset)
    rings
        .sortedByDescending { it.radius }
        .forEach { ring ->
            mask.forEach { at ->
                val x = (at shr 32).toInt()
                val y = at.toInt()
                for (dy in -ring.radius..ring.radius) {
                    for (dx in -ring.radius..ring.radius) {
                        val nx = x + dx
                        val ny = y + dy
                        if (
                            key(nx, ny) !in mask &&
                                nx in 0 until out.width &&
                                ny in 0 until out.height
                        ) {
                            out.setRGB(nx, ny, (ring.alpha shl 24) or 0xFFFFFF)
                        }
                    }
                }
            }
        }
    return out
}

private fun key(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)

/**
 * One 16x16 patch per slot, cut out of [panel] where that slot's item is drawn.
 *
 * A marker that blanks vanilla's hover box has to put back whatever the panel had there, and on any
 * screen worth theming that is not one colour: a well here, a heading's text there, bare face in
 * between. Cutting each slot's own pixels is the only version of this that cannot be wrong.
 *
 * Cut for every slot rather than for the ones a layout leaves empty, so this holds no opinion about
 * the layout — that lives with the screen, and a second copy of it here is how the two drift.
 *
 * @param lift alpha out of 255 to lighten the patch by, for a hover that answers the cursor. Zero
 *   makes a hovered tile look exactly like an unhovered one, which is what an empty tile wants.
 */
fun slotPatches(panel: BufferedImage, rows: Int, lift: Int = 0): Map<Int, BufferedImage> {
    require(rows in 1..6) { "a chest has 1..6 rows, got $rows" }
    return (0 until rows * 9).associateWith { slot ->
        val patch = panel.crop(slotItemX(slot % 9), slotItemY(slot / 9), ITEM_AREA, ITEM_AREA)
        if (lift == 0) patch else patch.lightened(lift)
    }
}

/**
 * A character set cut out of a font sheet: one sprite per drawable codepoint, plus the advances.
 */
data class GlyphCut(
    val sprites: Map<Int, BufferedImage>,
    /** Codepoint to pen advance, blanks included — a space advances and draws nothing. */
    val advances: Map<Int, Int>,
)

/**
 * Cuts a character set out of a font sheet laid out sixteen glyphs to a row.
 *
 * The advances are the point. A sheet is proportional — `i` is not as wide as `W` — and only the
 * side holding the pixels can measure that, so anything composing text from these sprites needs the
 * numbers to travel with them rather than be restated somewhere.
 *
 * Sprites are trimmed to their drawn width and then padded back to [minWidth], because a hover
 * frame needs four pixels for the marker's own data row and `i`, `.` and `!` are narrower than
 * that. Padding on the right leaves the ink in column zero, so a glyph draws where it always did.
 *
 * @param order the sheet's characters in sheet order; the client's ascii page starts with 32 blanks
 * @param codepoints which of them to cut, usually printable ASCII
 */
fun cutGlyphs(
    sheet: BufferedImage,
    order: String,
    codepoints: Iterable<Int> = 32..126,
    minWidth: Int = 4,
): GlyphCut {
    val cell = sheet.width / 16
    require(cell > 0) { "a ${sheet.width}x${sheet.height} sheet is not sixteen glyphs wide" }

    val sprites = LinkedHashMap<Int, BufferedImage>()
    val advances = LinkedHashMap<Int, Int>()
    codepoints.forEach { codepoint ->
        val index = order.indexOf(codepoint.toChar())
        if (index < 0) return@forEach
        val originX = (index % 16) * cell
        val originY = (index / 16) * cell

        var right = -1
        var bottom = -1
        for (row in 0 until cell) {
            for (column in 0 until cell) {
                if (sheet.getRGB(originX + column, originY + row) ushr 24 != 0) {
                    right = maxOf(right, column)
                    bottom = maxOf(bottom, row)
                }
            }
        }
        if (right < 0) {
            // Blank: it still has to advance, or every space in every string closes up.
            advances[codepoint] = 4
            return@forEach
        }

        advances[codepoint] = right + 2
        val glyph =
            BufferedImage(maxOf(right + 1, minWidth), bottom + 1, BufferedImage.TYPE_INT_ARGB)
        for (row in 0..bottom) {
            for (column in 0..right) {
                // White, so the palette decides the colour rather than the sheet.
                if (sheet.getRGB(originX + column, originY + row) ushr 24 != 0) {
                    glyph.setRGB(column, row, -1)
                }
            }
        }
        sprites[codepoint] = glyph
    }
    return GlyphCut(sprites, advances)
}
