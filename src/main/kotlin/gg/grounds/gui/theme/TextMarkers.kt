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
            out = out.append(frameMarker(frame, pen, y, imageWidth, imageHeight))
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
