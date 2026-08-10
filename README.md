# library-gui

Kotlin DSL for Minestom inventory GUIs: reactive slots (signals), pagination
and anvil text input.

```kotlin
dependencies {
    compileOnly("gg.grounds:library-gui:<version>") // host server supplies Minestom
}
```

## Compatibility

`library-gui` 0.2.x targets Minecraft 26.2 with Minestom `2026.07.22-26.2` and JVM 25.
The host supplies Minestom; consumers must not shade a second copy.

## Basics

One `Gui` instance serves one player — Minestom shares a single item array and
window id across all viewers of an inventory, so per-player state (and
per-player translation) requires one inventory per player.

Every click is cancelled by default; button handlers decide what happens
instead. Minestom fully resyncs slots and cursor after a cancelled click, so
no item can ever enter or leave the GUI — including via shift-clicks, drags
and hotbar/offhand swaps.

```kotlin
gui(player, Component.text("Menu"), rows = 3) {
    button(13, item(Material.DIAMOND_SWORD) {
        name(Component.text("Duel"))
        lore(Component.text("Jump the queue"))
        glowing = true
    }) {
        onClick { player.sendMessage(Component.text("Queued!")) }
        onRightClick { /* specific handlers win over onClick */ }
    }
    onClose { /* runs on every close path */ }
}.open()
```

## Layout

Shape first, contents second — no slot math:

```kotlin
gui(player, Component.text("Menu"), rows = 3) {
    layout(
        "#########",
        "#       #",
        "####X####",
    ) {
        place('#', item(Material.GRAY_STAINED_GLASS_PANE))
        place('X', item(Material.BARRIER) { name(Component.text("Close")) }) {
            onClick { close() }
        }
    }
}.open()
```

Rows are 9 chars, spaces leave slots untouched. Unbound chars are markers:
`slots('.')` returns their positions — e.g. as `contentSlots` for `pagedGui`.

## Translations

Per-player GUIs render per-player language (library-i18n):

```kotlin
gui(player, messages.render(ShopMessage.TITLE, player)) {
    translations = messages
    button(4, item(Material.EMERALD) { name(text(ShopMessage.BUY)) }) { /* ... */ }
}.open()
```

## Signals

`effect { }` runs immediately and re-runs whenever a signal it read changes.
Buttons set inside re-render on every run. Writing an equal value is a no-op,
and an effect writing a signal it also reads does not re-trigger itself.

```kotlin
gui(player, Component.text("Shop")) {
    var owned by signal(0)
    effect {
        button(4, item(Material.EMERALD) { name(Component.text("Owned: $owned")) }) {
            onClick { owned += 1 }
        }
    }
}.open()
```

## Pagination

Same-inventory page flips (no window flicker); the bottom row stays free for
navigation. Boundary clicks (next on the last page) are free no-ops, and
`setItems(newList)` re-renders an open GUI when the underlying data changes.

```kotlin
pagedGui(
    player,
    title = { page, pages -> Component.text("Friends ${page + 1}/$pages") },
    items = friends,
    render = { friend -> button(head(friend.skin) { name(Component.text(friend.name)) }) {
        onClick { /* ... */ }
    } },
) {
    navigation() // prev/next arrows, auto-hiding at the ends
}.open()
```

## More building blocks

```kotlin
// Player heads (friends, party, leaderboards) — anywhere
head(player)                     // current skin
head(skin) { name(displayName) } // PlayerSkin from your data

// Confirm dialog — onCancel also covers closing without choosing
confirmGui(player, Component.text("Delete party?"), onConfirm = { party.delete() }).open()

// Inside a gui(player, title) { ... } body:
gui(player, Component.text("Settings")) {
    // Toggle / cycle buttons backed by signals
    val notifications = toggleButton(3, off = bellOff, on = bellOn)
    val region = cycleButton(5, values = regions, render = { it.icon })

    // Async data: placeholder now, real content when the future lands (tick thread)
    val stats = signal(statsService.fetch(player.uuid), initial = null)
    effect { button(13, stats.get()?.let(::statsItem) ?: loadingItem) }

    // Click throttle — the clock is per slot, so effect re-renders don't reset it
    button(8, buyItem) {
        cooldown = Duration.ofMillis(500)
        onClick { /* ... */ }
    }

    // Frame animation — frames must differ, equal items skip the packet
    animate(0, TaskSchedule.tick(10), listOf(frame1, frame2, frame3))
}.open()
```

## Anvil input

```kotlin
anvilInput(player, Component.text("Party name")) { text ->
    party.rename(text)
}
```

## Themes: custom graphics

A theme is the one declaration behind both halves of a custom-looking GUI — the
resource pack the client downloads, and the components the server sends. Keeping
those two in sync by hand is what usually breaks; here neither can be generated
without the other.

```kotlin
val guiTheme = theme("grounds", PackFormat(88, minInclusive = 84, maxInclusive = 88)) {
    description = "Grounds GUI"
    // Background artwork. width/height are the PNG's real size — the generator
    // fails the build if the file on disk ever disagrees.
    panel("shop", "panels/shop.png", 176, 166, offsetY = -6)
    // A graphic that replaces an item's whole appearance.
    icon("coin", "icons/coin.png")
    // A hover effect: this button's tooltip is drawn with these sprites.
    tooltip("gold", "tooltips/gold_bg.png", "tooltips/gold_frame.png")
}
```

`PackFormat` is per Minecraft version — read `pack_version.resource_major` out of
that version's `version.json` rather than guessing (26.2 is 88). The range says
which clients the pack claims to serve.

Building the pack is a build-time step, not a server one:

```kotlin
writePack(guiTheme, assets = Path.of("art"), out = Path.of("build/pack"))
val sha1 = zipPack(Path.of("build/pack"), Path.of("build/grounds-gui.zip"))
```

Host that zip and hand the client both halves:

```kotlin
player.sendResourcePacks(
    ResourcePackRequest.resourcePackRequest()
        .packs(ResourcePackInfo.resourcePackInfo(id, URI.create(url), sha1))
        .required(true)
        .build(),
)
```

Using it is the normal DSL — the theme only supplies the title and two item ids:

```kotlin
gui(player, guiTheme.title("shop", Component.text("Shop")), rows = 3) {
    button(13, item(Material.PAPER) {
        name(Component.text("Buy"))
        itemModel = guiTheme.itemModel("coin")
        tooltipStyle = guiTheme.tooltipStyle("gold")
    }) {
        onClick { /* ... */ }
    }
}.open()
```

### How the background gets there

The client gives a server no way to draw inside a container window. The one piece
it does control is the *title*, so the artwork rides in as a font glyph: jump to
the panel's origin, draw the glyph, jump back, then the label. The jumps are
space glyphs from a fixed power-of-two ladder, so changing `offsetX` or `advance`
rewrites only the string the server sends — the pack's bytes, and its hash, stay
put and no client re-downloads for a horizontal nudge.

- **`offsetY` is the exception.** It is pixels below the title's top row
  (negative moves up), and it becomes the glyph's *ascent*, which lives in the
  font file. Changing it rebuilds the pack and every client refetches, so settle
  it once rather than treating it as a runtime knob. A pack whose ascent exceeds
  its height is rejected by the client, so that combination fails at declaration
  instead.
- **`advance` is measured, not guessed.** The client trims fully transparent
  columns off the right of a glyph before deciding how far it advances, so
  artwork with a soft right edge consumes less width than it occupies. The
  generator reproduces that calculation against the real PNG and fails the build
  with the number to declare, rather than letting the label drift sideways at
  runtime. Leave `advance` unset unless a build tells you otherwise.
- **`TITLE_INSET` (-8) is verified.** A container draws its title at
  `titleLabelX = 8`, `titleLabelY = 6`, and its window is `176 x (114 + rows*18)`
  — read out of the 26.2 client's `AbstractContainerScreen`/`ContainerScreen`.
  So a 3-row panel is 176x168 and the default inset puts it flush left.
- **`TITLE_ASCENT` (7) is still a convention.** It matches the vanilla font's own
  ascent for 8px glyphs, which is what makes `offsetY` read as "pixels below the
  title", but the client code that turns an ascent into a screen position was not
  available to check. Dial `offsetY` once against a real client.
  `examples/theme-demo` is a server built for exactly that: it boots, serves its
  own pack, and lets you nudge the offsets from in-game until the artwork lines
  up, then prints the tuned `panel(...)` line to paste back.

### What hover can and cannot do

The server never learns what the cursor is over — the client sends nothing on
hover. `tooltipStyle` is the whole sanctioned surface: the client redraws *this*
item's tooltip with *these* sprites, which is why the effect is chosen per
button rather than globally. Sprites are nine-sliced, because a tooltip is sized
by its text and never by us.

The highlight the client paints over the hovered slot is reachable too, but from
the other direction:

```kotlin
slotHighlight("highlight/back.png", "highlight/front.png")
```

That replaces vanilla's own `container/slot_highlight_back` and
`slot_highlight_front` sprites — drawn 24x24 at four pixels outside the slot,
`back` under the item and `front` over it. Those four pixels of bleed are what
make a *glow* possible rather than only a box: put the bloom on `back`, where it
spills around the item instead of over it, and leave `front` transparent.

Being a vanilla override it is **global**: every slot of every container changes,
the player's own inventory included, and it cannot be varied per GUI or per
button. That is structural, not a gap — `Slot.isHighlightable()` is `true` for
every ordinary slot, the one exception is a client-side class the server never
selects, and the client never tells the server what the cursor is over. The
choice is the effect everywhere or nowhere.

Clients that never load the pack see a plain vanilla GUI: the label alone, and
items in their material's own model. Nothing breaks, it just looks ordinary.

### Markers: what the pack format cannot express

Everything above is what the pack format offers on its own. Past it — an outline
over one specific slot, a description that stays where the layout put it, a price
no pack could know — needs a core shader override, and the library ships one.

A marker is a glyph carried in an item's tooltip. The client renders a tooltip
only for the slot under the cursor and only for that slot's own item, so a glyph
riding there is already hover-scoped and item-scoped without the server ever
learning where the cursor is. The shader recognises it by pixels inside its own
sprite and redraws it somewhere else entirely:

```kotlin
frame("outline", "frame/outline.png")
```
```kotlin
name(theme.frameMarker("outline", x = 7, y = 17, imageHeight = containerHeight(6)))
```

Markers draw in the order they are appended, which is what makes layering work:
a patch that hides vanilla's hover box, then artwork, then a label over it.

Position rides in the glyph's colour as two signed bytes measured from the
window's centre, so a marker draws within ±128px of it and no further. A panel
floating beside the window is not reachable, and asking for one fails rather than
landing somewhere unintended.

#### Text, and colour

A glyph set is one frame per character, which turns text into something the
server composes at render time rather than something the pack decides:

```kotlin
colour("dim", 0x969AA4)
glyphs("ascii", "glyph_", advances)
```
```kotlin
theme.text("ascii", x = 54, y = 98, "Sharpness III", imageHeight = height, tint = "dim")
```

`advances` maps codepoint to pen advance and has to come from wherever the glyphs
were cut — the sheet is proportional, and a second copy of those widths is the
kind that drifts a pixel at a time.

`tint` names a declared colour. It exists because the payload's low byte was
free: red and green carry the offset, a marker's identity is in its sprite, and
nothing read blue. So one white glyph set covers every weight, instead of one
family per colour. The shader multiplies, so tinting is exact on white or
greyscale artwork and darkens anything already coloured.

The shader is per Minecraft version — 26.2 renamed `rendertype_text` to `text`,
and a pack aimed at the wrong one overrides nothing at all, silently.

## Behavior notes

- **Close paths.** Client close, server close, GUI-to-GUI switch and
  disconnect all run `onClose` handlers and cancel `every`-tasks. The switch
  path matters: Minestom fires no `InventoryCloseEvent` when another
  inventory opens over this one. `onClose` handlers run one tick after the
  close — that makes `onClose { parentMenu.open() }` safe (a GUI opened
  _inside_ Minestom's close dispatch would be silently undone).
- **`preventClose = true`** reopens the GUI when the client tries to close
  it; `close()` always works.
- **`every(TaskSchedule.tick(20)) { }`** for animations. Slot updates skip
  the packet when the new item equals the current one — vary a component
  (e.g. amount or a lore line) or the animation won't render.
- **Anvil caveats** (vanilla client behavior, unverified against a live
  client): input only arrives once slot 0 holds an item (pre-filled), and
  arrives on roughly every keystroke.
- **Threading.** Call GUI methods from the tick thread (event handlers and
  scheduler tasks already are).

## License

[GNU Affero General Public License v3.0](LICENSE), the same terms every other
public Grounds repository carries. The repository was public for a while with
no license file at all, which means "all rights reserved" — this states the
terms that were always intended.
