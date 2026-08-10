package gg.grounds.gui.theme

import net.kyori.adventure.text.Component

/**
 * Draws the slice [sliceId] at ([x], [y]), [width] pixels across.
 *
 * Three markers, whatever the width: the two caps as themselves, and the middle as a meter clipped
 * to exactly the span between them. Tiling a middle band would have worked too and would have cost
 * a marker per tile plus a seam wherever the span was not a multiple of the tile — clipping is one
 * marker and no seam, and it falls out of the meter that already exists.
 *
 * Not tintable, and that is a property of how it is built rather than an omission: the middle's
 * fill occupies the byte a tint would use, so a tinted slice would colour its caps and leave its
 * middle alone. Colour it in the sprites, which is where a control's colour belongs anyway.
 *
 * @throws IllegalArgumentException if [width] cannot be built from these sprites — narrower than
 *   two caps, or wider than the middle can span. Both are the same mistake: a control asked to be a
 *   size its artwork cannot make, and a silent clamp would leave a button that looks nothing like
 *   the region it is supposed to cover.
 */
fun Theme.sliceMarkers(
    sliceId: String,
    x: Int,
    y: Int,
    width: Int,
    imageWidth: Int = CONTAINER_WIDTH,
    imageHeight: Int,
): Component {
    val slice =
        slices.firstOrNull { it.id == sliceId }
            ?: throw IllegalArgumentException("no slice '$sliceId' in theme '$namespace'")
    require(width >= 2 * slice.capWidth) {
        "slice '$sliceId' cannot be ${width}px wide; its caps alone are ${2 * slice.capWidth}px"
    }
    require(width <= slice.maxWidth) {
        "slice '$sliceId' cannot be ${width}px wide; its middle spans at most ${slice.middleWidth}px"
    }

    val span = width - 2 * slice.capWidth
    var out = Component.empty().append(frameMarker(slice.left, x, y, imageWidth, imageHeight))
    if (span > 0) {
        out =
            out.append(
                frameMarker(
                    slice.middle,
                    x + slice.capWidth,
                    y,
                    imageWidth,
                    imageHeight,
                    tintIndex = meterStep(span, slice.middleWidth),
                )
            )
    }
    return out.append(
        frameMarker(slice.right, x + width - slice.capWidth, y, imageWidth, imageHeight)
    )
}

/**
 * The payload step whose drawn width is exactly [target] out of [full].
 *
 * A fill expressed as a fraction of 255 does not land on a chosen pixel count by itself: the shader
 * rounds `full * step / 255`, and a span computed as `target / full` can come back a pixel short or
 * long. A button whose middle is a pixel narrower than the gap between its caps has a seam, and a
 * pixel wider has its right cap overdrawn — both read as a rendering fault rather than as a
 * rounding one.
 *
 * Every target is reachable because a middle is at most 255 wide, so the drawn width steps by zero
 * or one across the range and therefore hits every integer in it. The search is a direct estimate
 * plus a couple of steps either way, and it fails loudly rather than returning an approximation.
 */
internal fun meterStep(target: Int, full: Int): Int {
    require(target in 0..full) { "cannot draw ${target}px of a ${full}px meter" }
    val estimate = Math.round(target * 255.0 / full).toInt().coerceIn(0, 255)
    (0..3).forEach { delta ->
        listOf(estimate - delta, estimate + delta).forEach { step ->
            if (step in 0..255 && drawnWidth(step, full) == target) return step
        }
    }
    throw IllegalStateException("no meter step draws exactly ${target}px of ${full}px")
}

/** What the shader draws for a step, kept here so the two cannot disagree quietly. */
internal fun drawnWidth(step: Int, full: Int): Int = Math.floor(full * (step / 255.0) + 0.5).toInt()
