package gg.grounds.gui.theme

/**
 * The theme font's space glyphs: one codepoint per signed power of two, so any pixel offset in
 * `-`[MAX_OFFSET]`..`[MAX_OFFSET] is expressed as a short string of existing glyphs.
 *
 * A fixed ladder keeps the generated pack stable. Nudging a panel by a pixel changes only the
 * *string* the server sends in the window title — the pack's bytes, and therefore its hash, stay
 * put, so clients never redownload for a layout tweak. Allocating a glyph per distinct offset would
 * invert that: every tweak would be a new pack.
 */
object Spaces {
    /** First codepoint of the Unicode Private Use Area, where all theme glyphs live. */
    const val PUA_START: Int = 0xE000

    /** Last usable Private Use Area codepoint. */
    const val PUA_END: Int = 0xF8FF

    private val STEPS = intArrayOf(1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024)

    /**
     * Largest offset [of] can express, in pixels. The steps are powers of two, so this is their
     * sum.
     */
    const val MAX_OFFSET: Int = 2047

    /**
     * First codepoint the ladder does not occupy — where [Theme] starts allocating panel glyphs.
     */
    val firstFreeCodepoint: Int = PUA_START + STEPS.size * 2

    private fun codepoint(index: Int, negative: Boolean): Int =
        PUA_START + index * 2 + if (negative) 1 else 0

    /** The font's `space` provider contents: glyph string to advance in pixels. */
    fun advances(): Map<String, Int> = buildMap {
        STEPS.forEachIndexed { index, step ->
            put(String(Character.toChars(codepoint(index, false))), step)
            put(String(Character.toChars(codepoint(index, true))), -step)
        }
    }

    /**
     * The glyph string that moves the text cursor [px] pixels; negative moves left. Empty for `0`.
     *
     * The steps are powers of two and [px] is bounded, so this is a binary decomposition — each
     * step appears at most once and the result is never longer than [STEPS]`.size` characters.
     */
    fun of(px: Int): String {
        require(px >= -MAX_OFFSET && px <= MAX_OFFSET) {
            "offset $px is outside +-$MAX_OFFSET, which the space ladder cannot express"
        }
        if (px == 0) return ""
        val negative = px < 0
        var remaining = if (negative) -px else px
        val out = StringBuilder()
        for (index in STEPS.indices.reversed()) {
            if (remaining >= STEPS[index]) {
                out.appendCodePoint(codepoint(index, negative))
                remaining -= STEPS[index]
            }
        }
        return out.toString()
    }
}
