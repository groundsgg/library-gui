package gg.grounds.gui.theme

/**
 * Vanilla draws a container's title 8px right of the window's left edge, so a panel that should
 * start at that edge shifts back by 8.
 */
const val TITLE_INSET: Int = -8

/**
 * Ascent that puts a glyph's top row level with the title text's own top row — the vanilla font
 * draws 8px-tall glyphs with an ascent of 7, and matching that is what makes [Panel.offsetY]
 * readable as "pixels below the title".
 */
const val TITLE_ASCENT: Int = 7

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

/** First codepoint of the spaces that cancel each corner's advance. */
const val FRAME_SPACE_BASE: Int = 0xF010

/**
 * Red channel that marks a glyph for relocation by the shader.
 *
 * Every vanilla text colour has a red channel of 0x00, 0x55, 0xAA or 0xFF, and an implicit drop
 * shadow is the colour scaled by a quarter, so no ordinary text reaches this value. Reserve
 * `#FE****` across the whole server: any other text using it would be teleported too.
 */
const val FRAME_MARKER_RED: Int = 0xFE

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
 * @param offsetX horizontal shift from the title's own origin. The default puts the image at the
 *   window's left edge.
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
    val offsetX: Int = TITLE_INSET,
    val offsetY: Int = 0,
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

    /** Font ascent that realises [offsetY]. */
    val ascent: Int
        get() = TITLE_ASCENT - offsetY

    /** [advance] if set, else the drawn width plus the vanilla 1px inter-glyph gap. */
    val effectiveAdvance: Int
        get() = advance ?: (drawnWidth + 1)
}

/**
 * A custom graphic for an item slot, shipped as an item model definition and selected at runtime
 * through the `item_model` component. This replaces the item's whole appearance, so the underlying
 * material only decides stack behaviour, never what the player sees.
 */
data class Icon(val id: String, val texture: String) {
    init {
        requireId(id, "icon id")
        requireTexture(texture)
    }
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
data class Frame(val id: String, val texture: String) {
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
    val frames: List<Frame> = emptyList(),
) {
    private val glyphs: Map<String, String>
    private val panelsById: Map<String, Panel>

    init {
        requireId(namespace, "namespace")
        requireId(font, "font name")
        requireDistinct(panels.map { it.id }, "panel")
        requireDistinct(icons.map { it.id }, "icon")
        requireDistinct(tooltips.map { it.id }, "tooltip")
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
        offsetX: Int = TITLE_INSET,
        offsetY: Int = 0,
        advance: Int? = null,
    ) {
        panels += Panel(id, texture, width, height, scale, offsetX, offsetY, advance)
    }

    /** Declares a custom item graphic; see [Icon]. */
    fun icon(id: String, texture: String) {
        icons += Icon(id, texture)
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
     * Ships the core-shader hover frame — the one hover effect that can be limited to a region of a
     * single GUI. Read [Frame] first; declaring any frame replaces a vanilla shader.
     *
     * [texture] is the outline itself, drawn at its own pixel size and in whatever shape it has.
     */
    fun frame(id: String, texture: String) {
        frames += Frame(id, texture)
    }

    private val frames = mutableListOf<Frame>()

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
            frames.toList(),
        )
}
