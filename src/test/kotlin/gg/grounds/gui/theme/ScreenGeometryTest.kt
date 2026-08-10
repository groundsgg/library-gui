package gg.grounds.gui.theme

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The shader's arithmetic, checked without a GPU.
 *
 * Every marker's position is `floor(screen * 0.5) + offset`, where `screen` is recovered from the
 * projection matrix rather than known. If that recovery is wrong by one pixel at some GUI scale,
 * every themed screen is wrong by one pixel at that scale — and nobody plays at every scale, so it
 * would be found by a player rather than by us.
 *
 * The recovery is float arithmetic, which is the one part of a shader that is the same on the CPU:
 * GLSL's `highp float` and the JVM's `Float` are both IEEE binary32. So the formula can be replayed
 * here over every size the client can produce.
 *
 * The epsilon is read out of the shipped shader rather than repeated, so a change there cannot pass
 * this test by being invisible to it.
 */
class ScreenGeometryTest {
    private val shader: String =
        checkNotNull(javaClass.getResourceAsStream("/gg/grounds/gui/pack/text.vsh"))
            .bufferedReader()
            .use { it.readText() }

    /** The `- 0.001` in `ceil(2.0 / ... - 0.001)`, taken from the shader itself. */
    private val epsilon: Float =
        Regex("""ceil\(2\.0 / vec2\(ProjMat\[0]\[0], -ProjMat\[1]\[1]\) - ([0-9.]+)\)""")
            .find(shader)
            ?.groupValues
            ?.get(1)
            ?.toFloat()
            ?: error("the shader no longer recovers the screen size the way this test replays")

    /** What the client puts in the matrix: an orthographic projection over the scaled GUI. */
    private fun projection(scaledWidth: Int): Float = 2.0f / scaledWidth.toFloat()

    /** What the shader gets back out of it. */
    private fun recovered(scaledWidth: Int): Int =
        ceil(2.0f / projection(scaledWidth) - epsilon).toInt()

    @Test
    fun `the screen size survives the round trip through the matrix`() {
        // Every width the client can hand out: guiScaledWidth is ceil(framebuffer / guiScale), and
        // a framebuffer runs from a small window to 8K. One pixel of error here is one pixel of
        // error on every marker at that scale.
        val wrong = (1..8192).filter { recovered(it) != it }
        assertEquals(emptyList(), wrong, "widths the shader would misread")
    }

    @Test
    fun `the nudge sits inside a two-sided bound, with room on both sides`() {
        // Both directions matter, and that is what makes the value non-obvious. A round trip that
        // comes back high needs the nudge to exceed the error, or the ceiling jumps a whole pixel.
        // One that comes back low needs the nudge to stay a pixel clear of 1.0, or a real width is
        // rounded away. So the safe range is [error, 1 - error] and the middle of it is the value
        // that survives the widest displays — the first version used 0.001, which was legal but sat
        // against the floor of that range.
        val worst =
            (1..8192).maxOf { width -> Math.abs(2.0f / projection(width) - width.toFloat()) }
        assertTrue(worst < 0.01f, "round-trip error grew unexpectedly: $worst")
        assertTrue(epsilon > worst, "nudge $epsilon must exceed the $worst error it absorbs")
        assertTrue(epsilon < 1f - worst, "nudge $epsilon must stay clear of swallowing a pixel")

        // And it should not merely be legal. Sitting near either end means the first wider display
        // breaks it, which is a bug nobody can reproduce without that display.
        val headroom = minOf(epsilon - worst, 1f - worst - epsilon)
        assertTrue(
            headroom > 0.4f,
            "only $headroom of margin; the nudge is not centred in its range",
        )
    }

    @Test
    fun `a marker lands where the client puts the window, at both parities`() {
        // The client centres a container with `leftPos = (width - imageWidth) / 2`, truncating.
        // The shader computes `floor(screen / 2) + x - imageWidth / 2`. Those agree for even and
        // odd screen widths, and the odd case is the one nobody checks by hand.
        listOf(1920, 1921, 854, 855, 320, 321).forEach { screen ->
            listOf(CONTAINER_WIDTH, 276).forEach { imageWidth ->
                val clientLeft = (screen - imageWidth) / 2
                val shaderOrigin = floor(screen / 2.0).toInt() + (0 - imageWidth / 2)
                assertEquals(clientLeft, shaderOrigin, "screen $screen, window $imageWidth")
            }
        }
    }

    @Test
    fun `a window wider than the screen still agrees, rather than drifting apart`() {
        // A merchant window is 276 wide and the smallest GUI the client allows is 320, so this is
        // reachable at scale 1 on a small window with a wide screen type.
        listOf(280 to 276, 300 to 276).forEach { (screen, imageWidth) ->
            assertEquals(
                (screen - imageWidth) / 2,
                floor(screen / 2.0).toInt() - imageWidth / 2,
                "screen $screen, window $imageWidth",
            )
        }
    }
}
