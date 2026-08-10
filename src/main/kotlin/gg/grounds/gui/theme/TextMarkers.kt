package gg.grounds.gui.theme

import net.kyori.adventure.text.Component

/**
 * Draws [text] into the GUI window with its left edge at ([x], [y]), one marker per character.
 *
 * This is the difference between a themed screen that shows a picture and one that shows a value. A
 * label painted into the panel is decided when the pack is built; a price, a balance or a player's
 * name is not known until the moment the item is hovered. Every character is its own frame, so the
 * server places them itself and the string can be anything.
 *
 * Like every marker, these live in an item's tooltip and therefore appear exactly while that slot
 * is hovered — which is what makes a detail card possible without the client ever telling the
 * server where the cursor is.
 *
 * Cost is one glyph plus its cancelling space per character, and both are invisible where they sit.
 * A twenty-character line is forty codepoints in a string nobody reads.
 *
 * @param glyphSetId a set declared with `glyphs(...)` on the theme
 * @param tint a colour declared with `colour(...)`, or null to keep the glyphs' own. One set drawn
 *   in several colours is why this exists — the alternative was a whole family per colour.
 * @throws IllegalArgumentException if the set does not exist, or if [text] contains a character the
 *   set has no advance for. Loud on purpose: the alternative is a line that silently renders short.
 */
fun Theme.text(
    glyphSetId: String,
    x: Int,
    y: Int,
    text: String,
    imageWidth: Int = CONTAINER_WIDTH,
    imageHeight: Int,
    tint: String? = null,
): Component {
    val set = glyphSet(glyphSetId)
    val frames = frames.mapTo(HashSet()) { it.id }
    var pen = x
    var out = Component.empty()
    text.codePoints().forEach { codepoint ->
        val advance = set.advanceOf(glyphSetId, codepoint)
        val frame = set.frame(codepoint)
        // A codepoint with an advance but no frame is a space: it moves the pen and draws nothing.
        // Checking the frames rather than assuming keeps a missing sprite from being mistaken for
        // one, which would leave a hole in the middle of a word and no clue why.
        if (frame in frames) {
            out = out.append(frameMarker(frame, pen, y, imageWidth, imageHeight, tint))
        }
        pen += advance
    }
    return out
}

/**
 * Width [text] will occupy in the set [glyphSetId], for centring it without rendering it first.
 *
 * The trailing character's advance is included, exactly as the client's own measurement does it, so
 * a centred string sits one pixel left of true centre — same as vanilla text.
 */
fun Theme.textWidth(glyphSetId: String, text: String): Int {
    val set = glyphSet(glyphSetId)
    return text.codePoints().map { set.advanceOf(glyphSetId, it) }.sum()
}

/** The set declared under [id]. */
fun Theme.glyphSet(id: String): GlyphSet =
    glyphSets.firstOrNull { it.id == id }
        ?: throw IllegalArgumentException("no glyph set '$id' in theme '$namespace'")

private fun GlyphSet.advanceOf(setId: String, codepoint: Int): Int =
    advances[codepoint]
        ?: throw IllegalArgumentException(
            "glyph set '$setId' has no advance for U+${codepoint.toString(16).uppercase()}" +
                " ('${String(Character.toChars(codepoint))}')"
        )

/**
 * A marker placed from the screen's centre rather than from a container window.
 *
 * Container markers are given window coordinates and converted, because the server knows a window's
 * size but not where the client put it. A dialog has no window: it is laid out around the middle of
 * the screen, which is the one point the shader already computes. So these coordinates are that
 * offset directly — (0, 0) is the centre, negative is up and left.
 *
 * That this works at all is a property of where the glyph rides: markers live in text, the client
 * draws a dialog's body with the same font pipeline as a container's title, and the shader does not
 * care which screen asked. Confirmed against a 26.2 client — a dialog is decorated by exactly the
 * mechanism that outlines a container slot, which the dialog format itself offers no way to do.
 *
 * The ±128px reach is unchanged and matters more here, since a dialog is wider than the span of a
 * signed byte.
 */
fun Theme.screenMarker(id: String, x: Int, y: Int, tint: String? = null): Component =
    frameMarker(id, x, y, imageWidth = 0, imageHeight = 0, tint = tint)

/** [text], placed from the screen's centre; see [screenMarker]. */
fun Theme.screenText(
    glyphSetId: String,
    x: Int,
    y: Int,
    body: String,
    tint: String? = null,
): Component = text(glyphSetId, x, y, body, imageWidth = 0, imageHeight = 0, tint = tint)
