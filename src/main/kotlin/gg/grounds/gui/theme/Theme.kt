package gg.grounds.gui.theme

/**
 * Vanilla draws an ordinary container's title 8px right of the window's left edge.
 *
 * Kept for callers that want the number itself; a panel no longer carries it, because it is not the
 * same on every screen — see [TitleAnchor].
 */
const val TITLE_INSET: Int = -8

/**
 * Ascent that puts a glyph's top row level with the title text's own top row — the vanilla font
 * draws 8px-tall glyphs with an ascent of 7, and matching that is what makes [Panel.offsetY]
 * readable as "pixels below the title".
 */
const val TITLE_ASCENT: Int = 7

/** Row the title text's own top edge sits on, from the client's `titleLabelY`. */
const val TITLE_TOP_Y: Int = 6

/**
 * Where the title's baseline falls inside the window — the number a panel's ascent is measured
 * against, and the one worth naming, because a panel aligned with the window's top edge is declared
 * at exactly this ascent.
 */
const val TITLE_BASELINE_Y: Int = TITLE_TOP_Y + TITLE_ASCENT

/**
 * Default vertical offset: lifts a panel's top edge to the window's own top edge.
 *
 * Zero would put the artwork six pixels lower, level with the title text instead of the window —
 * which is the one placement nobody actually wants, so it is not the default.
 */
const val DEFAULT_PANEL_OFFSET_Y: Int = -TITLE_TOP_Y

private val RESOURCE_ID = Regex("[a-z0-9_.-]+")

private fun requireId(value: String, what: String) {
    require(RESOURCE_ID.matches(value)) { "$what '$value' must match ${RESOURCE_ID.pattern}" }
}

private fun requireTexture(path: String) {
    require(path.isNotBlank()) { "texture path must not be blank" }
    require(!path.startsWith("/")) { "texture path '$path' must be relative to the asset root" }
    require(".." !in path) { "texture path '$path' must not escape the asset root" }
    require(path.endsWith(".png")) { "texture path '$path' must be a .png" }
}

/**
 * `pack.mcmeta` format numbers.
 *
 * These change per Minecraft version — read `pack_version.resource_major` out of the target
 * version's `version.json` instead of guessing (26.2 is 88, 26.1.2 was 84). [minInclusive] and
 * [maxInclusive] declare the range the pack claims to serve; a client outside it warns the player.
 */
data class PackFormat(
    val format: Int,
    val minInclusive: Int = format,
    val maxInclusive: Int = format,
) {
    init {
        require(format > 0) { "pack format must be positive, got $format" }
        require(minInclusive <= format && format <= maxInclusive) {
            "pack format $format must lie inside $minInclusive..$maxInclusive"
        }
        // A range crossing the boundary would have to declare both metadata shapes at once, with
        // matching values and pack_format pinned to the floor. Refusing is better than emitting
        // something the client quietly fails to parse.
        require(minInclusive > LAST_PRE_MINOR_FORMAT || maxInclusive <= LAST_PRE_MINOR_FORMAT) {
            "pack format range $minInclusive..$maxInclusive straddles $LAST_PRE_MINOR_FORMAT, where " +
                "the client switches metadata shape; ship one pack per side of that line"
        }
    }
}

/**
 * The format at which the client switches how a pack declares which versions it serves, taken from
 * its own `PackFormat.lastPreMinorVersion(CLIENT_RESOURCES)`.
 *
 * At or below it a pack declares `pack_format` and `supported_formats`. Above it `min_format` and
 * `max_format` are mandatory and `supported_formats` is refused outright. The failure is quiet:
 * metadata the client cannot parse falls back to a placeholder, so the pack loads with an unknown
 * compatibility and a stack trace in every player's log rather than an obvious error.
 */
const val LAST_PRE_MINOR_FORMAT: Int = 64

/**
 * First codepoint of the hover frame's own block, kept well clear of the panel glyphs so adding a
 * panel can never renumber a corner.
 */
const val FRAME_GLYPH_BASE: Int = 0xF000

/**
 * First codepoint of the spaces that cancel each frame's advance.
 *
 * The gap to [FRAME_GLYPH_BASE] is the number of frames a theme can hold, and it has to be a gap
 * rather than a convention: a frame and its cancelling space are numbered from the same index, so
 * the moment the two blocks touch, frame *n* is written with the codepoint of space *0*. Both
 * providers then claim it, the later one in the font wins, and the marker silently renders as a
 * space — the frame simply never appears, with nothing logged anywhere.
 */
const val FRAME_SPACE_BASE: Int = 0xF400

/**
 * How many frames a theme can hold, from the distance between the two blocks.
 *
 * Both blocks stay inside the private use area (0xE000–0xF8FF), which is what keeps them off every
 * codepoint a player could type.
 */
const val FRAME_CAPACITY: Int = FRAME_SPACE_BASE - FRAME_GLYPH_BASE

/**
 * Red channel that marks a glyph for relocation by the shader.
 *
 * Every vanilla text colour has a red channel of 0x00, 0x55, 0xAA or 0xFF, and an implicit drop
 * shadow is the colour scaled by a quarter, so no ordinary text reaches this value. Reserve
 * `#FE****` across the whole server: any other text using it would be teleported too.
 */
const val FRAME_MARKER_RED: Int = 0xFE

/**
 * How many colours a theme can declare.
 *
 * The index rides in the payload's low byte and zero is reserved for "leave the sprite alone", so
 * 255 remain. Nothing about the mechanism wants that many; the cap exists to fail on the ridiculous
 * rather than to silently wrap.
 */
const val PALETTE_CAPACITY: Int = 255

/**
 * A colour a marker can be tinted with.
 *
 * Markers looked untintable for a long time, and the second glyph family in the demo exists because
 * of that reading. It was wrong: the payload's red and green carry the offset, a marker's identity
 * lives in its sprite's data pixels, and the blue byte was written as zero and read by nobody. So
 * it carries a palette index, the generator writes the palette into the shader, and one sprite
 * serves every colour instead of one family per colour.
 *
 * @param rgb 0xRRGGBB; the alpha the sprite was drawn with is kept
 */
data class ThemeColour(val name: String, val rgb: Int) {
    init {
        requireId(name, "colour name")
        require(rgb in 0..0xFFFFFF) { "colour '$name' must be 0xRRGGBB, got ${rgb.toString(16)}" }
    }
}

/**
 * A proportional character set whose glyphs are hover frames, one per codepoint.
 *
 * A label painted into a panel is fixed when the pack is built, which is all a button that always
 * says the same thing needs. A price is not that. Cutting every character into its own frame turns
 * text into something the server composes per render: one marker per glyph, each placed at the
 * running pen position.
 *
 * @param framePrefix frame id for a codepoint is [framePrefix] followed by the codepoint
 * @param advances codepoint to pen advance; a codepoint that has an advance but no frame — a space
 *   — moves the pen without drawing, which is exactly what a blank glyph is
 */
data class GlyphSet(val id: String, val framePrefix: String, val advances: Map<Int, Int>) {
    init {
        requireId(id, "glyph set id")
        require(advances.isNotEmpty()) { "glyph set '$id' has no advances" }
    }

    /** The frame that draws [codepoint]. */
    fun frame(codepoint: Int): String = "$framePrefix$codepoint"
}

/**
 * A control that can be drawn at any width from three sprites.
 *
 * A button spanning seven slots used to be its own PNG, and so did one spanning three: a sprite per
 * width, decided when the pack was built, and a width nobody anticipated meant a new asset. Three
 * frames replace all of them — two caps and a middle that is a horizontal meter, drawn clipped to
 * exactly the span wanted.
 *
 * Only the horizontal case, deliberately. A vertical one is the same idea rotated and has had no
 * caller; adding it on the chance that one appears is how an API grows things nobody uses.
 *
 * @param capWidth how much of each end is copied one to one, the way a nine-slice border is
 * @param middleWidth the middle sprite's own width, and therefore the widest span it can cover
 */
data class Slice(
    val id: String,
    val left: String,
    val middle: String,
    val right: String,
    val capWidth: Int,
    val middleWidth: Int,
) {
    init {
        requireId(id, "slice id")
        require(capWidth > 0) { "slice '$id' needs a cap, got $capWidth" }
        // The shader reads a sprite's width from a byte, so nothing wider than this exists anyway —
        // and it is also what guarantees every span is reachable exactly. See sliceMarkers.
        require(middleWidth in 1..255) { "slice '$id' middle is ${middleWidth}px, must be 1..255" }
    }

    /** The widest control these three sprites can draw. */
    val maxWidth: Int
        get() = 2 * capWidth + middleWidth
}

/**
 * A background image drawn behind a GUI.
 *
 * The client gives a server no way to draw into a container window, so the image rides in as a font
 * glyph inside the window *title* — the one piece of that window the server controls. The title is
 * rendered before the slots, which is exactly what makes it usable as a backdrop.
 *
 * [width] and [height] are the source PNG's pixel size; the generator fails the build if the file
 * on disk disagrees, so this declaration can never silently drift from the artwork.
 *
 * @param offsetX extra horizontal nudge from wherever the screen puts its title. Zero is right for
 *   almost everything — the screen's own inset is supplied by the [TitleAnchor] passed to
 *   [Theme.title], not hardcoded here.
 * @param offsetY pixels *down* from the title text's top row; negative moves up.
 * @param advance width the glyph consumes before the label is drawn. Defaults to the drawn width
 *   plus the vanilla 1px gap. Override it if the client trims fully transparent columns off the
 *   right of the artwork — that shows up as the label sitting too far left, and the editor is the
 *   place to measure the true number.
 */
data class Panel(
    val id: String,
    val texture: String,
    val width: Int,
    val height: Int,
    val scale: Int = 1,
    val offsetX: Int = 0,
    val offsetY: Int = DEFAULT_PANEL_OFFSET_Y,
    val advance: Int? = null,
) {
    init {
        requireId(id, "panel id")
        requireTexture(texture)
        require(width > 0 && height > 0) {
            "panel '$id' must have a positive size, got ${width}x$height"
        }
        require(scale >= 1) { "panel '$id' scale must be at least 1, got $scale" }
        require(ascent <= drawnHeight) {
            "panel '$id' has ascent $ascent above its drawn height $drawnHeight — the client rejects " +
                "a pack whose glyph ascent exceeds its height; raise offsetY or the artwork"
        }
        require(advance == null || advance > 0) { "panel '$id' advance override must be positive" }
        // Both jumps are written with space glyphs, so a panel wider than the ladder could never be
        // drawn without shifting the label. Catch that at declaration, not the first time a player
        // opens the GUI.
        require(offsetX >= -Spaces.MAX_OFFSET && offsetX <= Spaces.MAX_OFFSET) {
            "panel '$id' offsetX $offsetX is outside +-${Spaces.MAX_OFFSET}"
        }
        // The jump back cancels the outward jump and the advance together, so it is that sum, not
        // the advance alone, that has to stay inside the ladder.
        require(kotlin.math.abs(offsetX + effectiveAdvance) <= Spaces.MAX_OFFSET) {
            "panel '$id' needs a ${offsetX + effectiveAdvance} px jump back, more than the " +
                "${Spaces.MAX_OFFSET} px the space ladder can express; lower the scale or split the artwork"
        }
    }

    /** Width the image covers on screen. */
    val drawnWidth: Int
        get() = width * scale

    /** Height the image covers on screen. */
    val drawnHeight: Int
        get() = height * scale

    /**
     * Font ascent that realises [offsetY], measured from the title's baseline down to where the
     * artwork's top edge should land.
     */
    val ascent: Int
        get() = TITLE_BASELINE_Y - (TITLE_TOP_Y + offsetY)

    /** [advance] if set, else the drawn width plus the vanilla 1px inter-glyph gap. */
    val effectiveAdvance: Int
        get() = advance ?: (drawnWidth + 1)
}

/**
 * A custom graphic for an item slot, shipped as an item model definition and selected at runtime
 * through the `item_model` component. This replaces the item's whole appearance, so the underlying
 * material only decides stack behaviour, never what the player sees.
 */
data class Icon(val id: String, val texture: String?) {
    init {
        requireId(id, "icon id")
        texture?.let(::requireTexture)
    }

    /** An icon with no artwork at all — the client is told to draw nothing. */
    val empty: Boolean
        get() = texture == null
}

/**
 * A per-item tooltip skin, selected at runtime through the `tooltip_style` component.
 *
 * This is the one hover effect the client offers a server: hover state never reaches the server,
 * but the client redraws *this* item's tooltip with *these* sprites, so styling is chosen per
 * button rather than globally.
 *
 * @param border nine-slice inset in pixels, so the frame stretches to any tooltip size without
 *   smearing its corners.
 */
data class Tooltip(val id: String, val background: String, val frame: String, val border: Int = 4) {
    init {
        requireId(id, "tooltip id")
        requireTexture(background)
        requireTexture(frame)
        require(border >= 0) { "tooltip '$id' border must not be negative, got $border" }
    }
}

/**
 * Replaces the highlight the client draws over whichever slot the cursor is on.
 *
 * Unlike everything else in a theme this is a *vanilla sprite override*, so it applies everywhere:
 * every slot of every container the player opens, themed or not. The client alone decides when to
 * draw it — the server is never told what the cursor is over — so this cannot be varied per button.
 * A highlight that reacts to one specific slot needs a core shader in the pack, which this library
 * does not generate.
 *
 * Both sprites are blitted 24x24 at four pixels outside the slot, [back] beneath the item and
 * [front] over it, so the item's own 16x16 area sits at offset (4, 4) within them. Artwork must be
 * square; larger squares are simply higher-resolution versions of the same thing.
 */
data class SlotHighlight(val back: String, val front: String) {
    init {
        requireTexture(back)
        requireTexture(front)
    }
}

/**
 * A frame that appears around a region while the cursor is on it, drawn by an overridden core
 * shader.
 *
 * This is the only hover effect that can be scoped to one region of one GUI. It works by carrying
 * marker glyphs in an item's tooltip: the client renders a tooltip only for the slot under the
 * cursor and only for that slot's own item, so a glyph placed there is inherently hover-scoped and
 * item-scoped. The shipped `core/text.vsh` recognises the marker's colour and rebuilds that glyph's
 * quad at a position the server encoded into it, instead of leaving it inside the tooltip.
 *
 * The artwork is the outline itself, drawn at its true size and in the shape's own form — the
 * sprite states its dimensions in data pixels the shader reads back, so a triangle is outlined as a
 * triangle rather than boxed. One marker draws the whole thing.
 *
 * Shipping this replaces a vanilla shader. If it fails to compile the client drops **every**
 * resource pack and saves that to its options, so test it against a client that has nothing to lose
 * before letting players near it.
 */
/**
 * Which way a meter fills, if it is one.
 *
 * A property of the artwork rather than of the marker: the payload has no room left, and a bar's
 * axis does not change from one draw to the next anyway. It rides in the spare byte of the size
 * pixel the sprite already carries, so a meter costs no extra data pixel and no extra width.
 */
enum class MeterAxis(internal val code: Int) {
    HORIZONTAL(1),
    VERTICAL(2),
}

data class Frame(val id: String, val texture: String, val meter: MeterAxis? = null) {
    init {
        requireId(id, "frame id")
        requireTexture(texture)
    }
}

/**
 * Everything a GUI theme contributes to the client: the panels, icons and tooltip skins, plus the
 * namespace they live under.
 *
 * One declaration feeds both sides — `gg.grounds.gui.pack.writePack` turns it into a resource pack,
 * and [title]/[itemModel]/[tooltipStyle] turn it into what the server sends. Nothing has to be kept
 * in sync by hand, which is the failure mode this type exists to remove.
 */
data class Theme(
    val namespace: String,
    val packFormat: PackFormat,
    val description: String = "GUI theme",
    val font: String = "gui",
    val panels: List<Panel> = emptyList(),
    val icons: List<Icon> = emptyList(),
    val tooltips: List<Tooltip> = emptyList(),
    val slotHighlight: SlotHighlight? = null,
    val bundleFiller: Boolean = false,
    val frames: List<Frame> = emptyList(),
    val glyphSets: List<GlyphSet> = emptyList(),
    val slices: List<Slice> = emptyList(),
    val colours: List<ThemeColour> = emptyList(),
) {
    private val glyphs: Map<String, String>
    private val panelsById: Map<String, Panel>

    /**
     * The distinct sprites the frames draw, sorted so both halves number them identically.
     *
     * A frame is a name for a sprite at a position, and nothing stops two names meaning the same
     * sprite — a screen whose empty tiles all sit on flat panel face declares one patch per slot
     * and they are the same patch. Numbering by sprite rather than by frame is what makes that cost
     * one glyph instead of fifty-four.
     *
     * The meter axis is part of the key because it is written into the sprite's own data pixels:
     * the same PNG declared as a bar and as a plain frame are two different textures once shipped.
     */
    internal val frameSprites: List<Pair<String, MeterAxis?>> =
        frames
            .map { it.texture to it.meter }
            .distinct()
            .sortedWith(compareBy({ it.first }, { it.second?.code ?: 0 }))

    init {
        requireId(namespace, "namespace")
        requireId(font, "font name")
        requireDistinct(panels.map { it.id }, "panel")
        requireDistinct(icons.map { it.id }, "icon")
        requireDistinct(tooltips.map { it.id }, "tooltip")
        requireDistinct(frames.map { it.id }, "frame")
        requireDistinct(glyphSets.map { it.id }, "glyph set")
        requireDistinct(slices.map { it.id }, "slice")
        requireDistinct(colours.map { it.name }, "colour")
        require(colours.size <= PALETTE_CAPACITY) {
            "${colours.size} colours exceed the $PALETTE_CAPACITY a payload byte can index"
        }
        // Past this the frame block runs into the space block that cancels its advance, and every
        // frame from there on renders as a space instead of as artwork. Nothing about that failure
        // is visible — no log line, no missing file, just a hover that draws nothing — so it has to
        // be caught here rather than discovered in a screenshot.
        //
        // Counted in sprites rather than in frames: two frames drawn from the same file are the
        // same glyph, so a screen naming a hundred slot patches that are all the same patch costs
        // one codepoint, not a hundred.
        require(frameSprites.size <= FRAME_CAPACITY) {
            "${frameSprites.size} frame sprites exceed the $FRAME_CAPACITY a theme can carry"
        }
        panelsById = panels.associateBy { it.id }

        // Sorted, so a glyph keeps its codepoint no matter what order the panels were declared in.
        // Reordering the declaration would otherwise rewrite the pack and force every client to
        // download it again.
        val sorted = panels.map { it.id }.sorted()
        require(Spaces.firstFreeCodepoint + sorted.size - 1 <= Spaces.PUA_END) {
            "${sorted.size} panels do not fit into the private use area"
        }
        glyphs =
            sorted.withIndex().associate { (index, id) ->
                id to String(Character.toChars(Spaces.firstFreeCodepoint + index))
            }
    }

    /** Which glyph a frame draws with — its sprite's place in [frameSprites]. */
    internal fun frameSpriteIndex(id: String): Int {
        val frame = frames.firstOrNull { it.id == id }
        require(frame != null) { "no frame '$id' in theme '$namespace'" }
        return frameSprites.indexOf(frame.texture to frame.meter)
    }

    /** The panel declared under [id]. */
    fun panel(id: String): Panel =
        panelsById[id] ?: throw IllegalArgumentException("no panel '$id' in theme '$namespace'")

    /** The single-character glyph string that draws [id]'s artwork. */
    fun glyph(id: String): String =
        glyphs[id] ?: throw IllegalArgumentException("no panel '$id' in theme '$namespace'")

    /** The `item_model` id for [id], for [gg.grounds.gui.ItemBuilder.itemModel]. */
    fun itemModel(id: String): String {
        require(icons.any { it.id == id }) { "no icon '$id' in theme '$namespace'" }
        return "$namespace:$id"
    }

    /** The `tooltip_style` id for [id], for [gg.grounds.gui.ItemBuilder.tooltipStyle]. */
    fun tooltipStyle(id: String): String {
        require(tooltips.any { it.id == id }) { "no tooltip '$id' in theme '$namespace'" }
        return "$namespace:$id"
    }

    private fun requireDistinct(ids: List<String>, what: String) {
        val duplicate = ids.groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }
        require(duplicate == null) { "duplicate $what id '${duplicate?.key}'" }
    }
}

/** Builds a [Theme]. */
fun theme(namespace: String, packFormat: PackFormat, block: ThemeBuilder.() -> Unit = {}): Theme =
    ThemeBuilder(namespace, packFormat).apply(block).build()

/** Collects a theme's parts; see [theme]. */
class ThemeBuilder(private val namespace: String, private val packFormat: PackFormat) {
    /** Shown in the client's resource pack list. */
    var description: String = "GUI theme"

    /** Font name, i.e. `assets/<namespace>/font/<font>.json`. */
    var font: String = "gui"

    private val panels = mutableListOf<Panel>()
    private val icons = mutableListOf<Icon>()
    private val tooltips = mutableListOf<Tooltip>()

    /** Declares a background image; see [Panel] for what the offsets mean. */
    fun panel(
        id: String,
        texture: String,
        width: Int,
        height: Int,
        scale: Int = 1,
        offsetX: Int = 0,
        offsetY: Int = DEFAULT_PANEL_OFFSET_Y,
        advance: Int? = null,
    ) {
        panels += Panel(id, texture, width, height, scale, offsetX, offsetY, advance)
    }

    /** Declares a custom item graphic; see [Icon]. */
    fun icon(id: String, texture: String) {
        icons += Icon(id, texture)
    }

    /**
     * Declares an item model that renders nothing.
     *
     * For the invisible items a themed screen is full of — the ones that exist only so a slot has
     * something to hang a tooltip on. A fully transparent texture would also disappear, but it
     * still costs an atlas entry and a blended quad per slot; `minecraft:empty` resolves to a model
     * whose whole job is to produce no geometry.
     */
    fun emptyIcon(id: String) {
        icons += Icon(id, null)
    }

    /** Declares a tooltip skin — the per-item hover effect; see [Tooltip]. */
    fun tooltip(id: String, background: String, frame: String, border: Int = 4) {
        tooltips += Tooltip(id, background, frame, border)
    }

    /**
     * Replaces the client's slot hover highlight. Global, and not variable per button — see
     * [SlotHighlight] before reaching for this.
     */
    fun slotHighlight(back: String, front: String) {
        highlight = SlotHighlight(back, front)
    }

    private var highlight: SlotHighlight? = null

    /**
     * Makes an empty bundle usable as an invisible marker carrier.
     *
     * Worth the oddity for one reason: a bundle's tooltip is the only one the client keeps drawing
     * while the cursor carries a stack. `BundleItem.getTooltipImage` checks only that the contents
     * component is *present*, and every bundle has an empty one by default, so a bare bundle
     * already qualifies — and `ClientBundleTooltip` is the sole class in the client whose
     * `showTooltipWithItemInHand()` returns true. Markers riding in it therefore survive a click
     * elsewhere on the screen, where any other item's markers blink out.
     *
     * An empty bundle's tooltip draws a progress bar and two strings of its own, so this blanks
     * both: the two sprites and the two language keys. Those overrides are global, but they reach
     * only real bundles — the sprite paths live under `container/bundle/` and are referenced from
     * exactly one class.
     *
     * It does not survive being picked up itself: a left click with an empty cursor takes the
     * bundle out of the slot, and a slot with no item has no tooltip at all. A right click is
     * absorbed and does not.
     */
    fun bundleFiller() {
        bundles = true
    }

    private var bundles = false

    /**
     * Ships the core-shader hover frame — the one hover effect that can be limited to a region of a
     * single GUI. Read [Frame] first; declaring any frame replaces a vanilla shader.
     *
     * [texture] is the outline itself, drawn at its own pixel size and in whatever shape it has.
     */
    fun frame(id: String, texture: String, meter: MeterAxis? = null) {
        frames += Frame(id, texture, meter)
    }

    /**
     * Declares a character set whose glyphs are hover frames, so text can be composed at runtime.
     *
     * [advances] maps codepoint to pen advance and has to come from wherever the glyphs were cut,
     * because the measurement and the artwork are the same fact: vanilla's sheet is proportional,
     * and a second copy of those numbers is a second thing to get wrong.
     */
    fun glyphs(id: String, framePrefix: String, advances: Map<Int, Int>) {
        glyphSets += GlyphSet(id, framePrefix, advances)
    }

    /** Declares a control drawable at any width; see [Slice]. */
    fun slice(
        id: String,
        left: String,
        middle: String,
        right: String,
        capWidth: Int,
        middleWidth: Int,
    ) {
        slices += Slice(id, left, middle, right, capWidth, middleWidth)
    }

    /** Declares a colour markers can be tinted with; see [ThemeColour]. */
    fun colour(name: String, rgb: Int) {
        colours += ThemeColour(name, rgb)
    }

    private val frames = mutableListOf<Frame>()
    private val glyphSets = mutableListOf<GlyphSet>()
    private val slices = mutableListOf<Slice>()
    private val colours = mutableListOf<ThemeColour>()

    /** Builds the theme, validating ids, sizes and glyph budget. */
    fun build(): Theme =
        Theme(
            namespace,
            packFormat,
            description,
            font,
            panels.toList(),
            icons.toList(),
            tooltips.toList(),
            highlight,
            bundles,
            frames.toList(),
            glyphSets.toList(),
            slices.toList(),
            colours.toList(),
        )
}
