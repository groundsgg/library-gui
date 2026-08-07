# theme-demo

A runnable server for settling a theme's title offsets against a real client.

The library positions a panel with numbers taken from how vanilla lays a
container title out. Nothing in it has been measured against a running client,
and it cannot be — so this demo exists to close that gap: join, open the GUI,
nudge until the artwork's slot grid sits under the real slots, and paste what
`/tune show` prints back into your theme.

```bash
./gradlew :examples:theme-demo:run
```

Then connect a **Minecraft 26.2** client to `localhost:25565` and type `/gui`.

## Tuning

The panel is a calibration target, not decoration: 8px rulers along the top and
left edge, and a slot grid at the positions a 3-row container uses. A misalign
is readable in pixels instead of guessable.

| Command | Effect |
| --- | --- |
| `/gui` | Opens the themed GUI |
| `/hover` | Opens the hover-only screen (see below) |
| `/glow` | Toggles the slot glow — **rebuilds the pack**, client refetches |
| `/tune x <px>` | Horizontal offset — instant, no download |
| `/tune y <px>` | Vertical offset — **rebuilds the pack**, client refetches |
| `/tune show` | Prints the current values as a pasteable `panel(...)` line |
| `/tune reset` | Back to the library's defaults |

`x` only changes the string the server puts in the window title, so it applies on
the next open. `y` becomes the glyph's *ascent*, which lives in the font file —
so it is a new pack with a new hash, and the client downloads it again. `/tune y`
therefore does not reopen the GUI for you: reopen with `/gui` once the download
lands, or you will be looking at the old artwork. `DemoThemeTest` pins exactly
this split.

A panel's *advance* is deliberately not tunable. The generator reproduces the
client's own measurement — it trims fully transparent columns off the right edge
before measuring — and fails the build with the correct number, so a hand-set
value could only ever be the wrong one.

While a pack is still on its way, `/tune y` and `/tune reset` refuse to send
another. Replacing a push that has not settled makes the client report the old
pack as discarded, and because the push is required, that terminal status would
kick you out of your own session.

## The hover-only screen

`/hover` opens a five-slot hopper with everything except the hover effect switched
off: no panel behind the window, no `item_model` on the items, a plain vanilla
title. The middle pickaxe carries a `tooltip_style`; the axe and shovel beside it
carry nothing. Whatever differs when you hover the middle one *is* the effect,
with its neighbours as the control.

Five compartments rather than a chest's twenty-seven, so the comparison is one
glance instead of a hunt.

The theme also replaces the **slot highlight** with a glow around the hovered
item — the bloom sits on the `back` layer, so it spills into the 4px around the
item rather than washing over it, and the `front` layer is left empty.

That one is a vanilla sprite override, so unlike the tooltip skin it is global: it
changes the hover glow in every container, the player's own inventory included,
and cannot be limited to a single GUI. `/glow` toggles it and re-sends the pack,
which is the only honest way to decide between everywhere and nowhere — those are
the two options.

Between them those two are the full extent of what a server can do to hover. The
client never reports what the cursor is over — it draws *this* item's tooltip
with *these* sprites, and its own highlight wherever it likes. Effects tied to
one specific slot need a core shader in the pack, which this demo does not ship.

## Configuration

| Variable | Default | Meaning |
| --- | --- | --- |
| `SERVER_PORT` | `25565` | Minecraft listen port |
| `PACK_PORT` | `8080` | HTTP port the pack is served on |
| `PACK_HOST` | `127.0.0.1` | Host **the client** resolves to fetch the pack |

`PACK_HOST` is the one that catches people out: the client downloads the pack,
not the server. If the client runs on another machine, `127.0.0.1` points it at
itself — set an address that machine can actually reach.

## Things worth knowing before you run it

- **The server is in offline mode.** `MinecraftServer.init()` is
  `init(Auth.Offline())` — there is no `MojangAuth` in 26.2. It accepts any
  username with no session check. Local development only; never expose it.
- **The pack is sent as required.** Minestom kicks on any terminal pack status
  that is not `SUCCESSFULLY_LOADED`, so a declined or failed download drops the
  player with "Required resource pack was not loaded." That is deliberate: a
  silent fallback to vanilla would waste your time wondering why nothing looks
  themed. The console logs every terminal status.
- **The hash cannot drift.** The SHA-1 handed to the client is computed from the
  exact bytes the HTTP host serves, in the same call. A stale hash would kick
  every player who joins, which is the failure this arrangement removes.
- **Artwork is generated, not drawn.** `art/generate.py` rebuilds all four PNGs;
  run it if you want to change the calibration target.
