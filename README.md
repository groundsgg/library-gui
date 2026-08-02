# library-gui

Kotlin DSL for Minestom inventory GUIs: reactive slots (signals), pagination
and anvil text input.

```kotlin
dependencies {
    compileOnly("gg.grounds:library-gui:<version>") // host server supplies Minestom
}
```

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

## Behavior notes

- **Close paths.** Client close, server close, GUI-to-GUI switch and
  disconnect all run `onClose` handlers and cancel `every`-tasks. The switch
  path matters: Minestom fires no `InventoryCloseEvent` when another
  inventory opens over this one. `onClose` handlers run one tick after the
  close — that makes `onClose { parentMenu.open() }` safe (a GUI opened
  *inside* Minestom's close dispatch would be silently undone).
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
