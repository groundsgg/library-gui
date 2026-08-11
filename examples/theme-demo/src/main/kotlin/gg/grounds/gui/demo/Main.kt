package gg.grounds.gui.demo

import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URI
import java.nio.file.Path
import java.util.UUID
import net.kyori.adventure.resource.ResourcePackCallback
import net.kyori.adventure.resource.ResourcePackInfo
import net.kyori.adventure.resource.ResourcePackRequest
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.CommandExecutor
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.kyori.adventure.nbt.CompoundBinaryTag
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.player.PlayerCustomClickEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.instance.LightingChunk
import net.minestom.server.instance.block.Block

private val OUT: Path = Path.of("build/demo-pack")

/**
 * A runnable server for dialling in a theme against a real client.
 *
 * The library's title offsets are conventions, not measurements — nothing in it has been checked
 * against a running Minecraft client. This demo exists to settle them: join, open the GUI, nudge
 * the numbers until the artwork's slot grid sits under the real slots, then paste what `/tune show`
 * prints back into the theme declaration.
 */
fun main() {
    val packHostName = System.getenv("PACK_HOST") ?: reachableAddress()
    // Required by default, because a pack that silently fails to load wastes an afternoon. Set
    // PACK_REQUIRED=false while iterating on shaders: a shader that fails to compile makes the
    // client drop the pack, and a required pack that gets dropped kicks you out of your own session.
    val packRequired = System.getenv("PACK_REQUIRED")?.toBooleanStrict() ?: true
    val packPort = (System.getenv("PACK_PORT") ?: "8080").toInt()
    val serverPort = (System.getenv("SERVER_PORT") ?: "25565").toInt()

    val host = PackHost(packPort)
    val packs = Packs(host, packHostName, packRequired)
    // Generate before binding the port. The JDK's HTTP dispatcher is a non-daemon thread, so a
    // failure between starting it and starting Minestom would leave a JVM that never exits, holding
    // the pack port with nothing listening for players.
    val sha1 = packs.rebuild()
    host.start()

    // init() is init(Auth.Offline()) — there is no MojangAuth in 26.2, and offline is what lets a
    // dev client join without a session. Never copy this into anything reachable from outside.
    val server = MinecraftServer.init()

    val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
    instance.setChunkSupplier(::LightingChunk)
    // fillHeight's upper bound is exclusive, so the floor's top block is y=40 and players stand on 41.
    instance.setGenerator { unit -> unit.modifier().fillHeight(0, 41, Block.STONE) }
    val spawn = Pos(0.5, 41.0, 0.5)

    MinecraftServer.getGlobalEventHandler().addListener(AsyncPlayerConfigurationEvent::class.java) { event ->
        event.spawningInstance = instance
        val player = event.player
        player.respawnPoint = spawn
        player.gameMode = GameMode.CREATIVE
        // The configuration phase is the right place: Minestom joins the resource pack future
        // before it sends FinishConfiguration, so the pack is loaded before the player spawns and
        // therefore before any GUI can open. Sending after spawn would race the first /gui.
        player.sendResourcePacks(packs.request)
    }

    // The whole point of a dialog is that something comes back. Without a listener the submit
    // button looks broken, so the demo echoes exactly what arrived.
    MinecraftServer.getGlobalEventHandler().addListener(PlayerCustomClickEvent::class.java) { event ->
        val payload = event.payload as? CompoundBinaryTag
        // The market's search is the one submission that is not just reported back: it reopens the
        // container it came from. Everything else is a dialog demonstrating dialogs.
        if (event.key.value() == MARKET_SEARCH) {
            openMarket(event.player, payload?.getString("query").orEmpty())
        } else {
            event.player.sendMessage(describeSubmission(event.key, payload))
        }
    }

    MinecraftServer.getGlobalEventHandler().addListener(PlayerSpawnEvent::class.java) { event ->
        event.player.sendMessage(welcome())
    }

    registerCommands(packs)
    printBanner(packs.url, sha1, serverPort)
    try {
        server.start("0.0.0.0", serverPort)
    } catch (failure: Throwable) {
        // Without this the pack host outlives the failure and keeps the JVM alive — a bound port
        // and no server, which reads as a hang rather than the "address already in use" it is.
        host.stop()
        throw failure
    }
}

/**
 * Holds the pack currently being served.
 *
 * The pack keeps a stable identity while every rebuild receives an immutable, hash-addressed URL.
 * With `replace(false)`, old requests remain valid and never replace other server packs.
 */
private class Packs(
    private val host: PackHost,
    private val hostName: String,
    private val required: Boolean,
) {
    @Volatile lateinit var request: ResourcePackRequest
        private set

    @Volatile lateinit var url: String
        private set

    /** Regenerates the pack from the current offsets, republishes it, and returns its SHA-1. */
    /**
     * This pack's identity, stable across rebuilds and restarts.
     *
     * The id is how a client recognises a pack it already holds: a new one every time means a new
     * pack every time, so replacing a pack becomes adding a second copy of it, and the stack grows
     * with every reload. Derived from the namespace so two themes cannot collide and one theme
     * cannot drift.
     */
    private val PACK_ID: UUID =
        UUID.nameUUIDFromBytes("grounds:pack:${DemoTheme.NAMESPACE}".toByteArray())

    @Synchronized
    fun rebuild(): String {
        val artifact = DemoTheme.rebuild(ART, OUT)
        host.publish(artifact.path, artifact.sha1)
        val versionedUrl = host.url(hostName, artifact.sha1)
        // The snapshot is published before exposing the matching request, so both this URL and
        // every older request URL retain their own exact bytes for the demo process lifetime.
        request =
            ResourcePackRequest.resourcePackRequest()
                .packs(ResourcePackInfo.resourcePackInfo(PACK_ID, URI.create(versionedUrl), artifact.sha1))
                .required(required)
                // Never replace. A client can hold several server packs at once, and replacing
                // drops every one it already has — including packs this server never sent. A
                // platform shipping a GUI pack beside a scene pack would have each of them
                // removing the other, in whichever order they happened to arrive.
                .replace(false)
                .prompt(Component.text("library-gui theme demo", NamedTextColor.GOLD))
                .callback(
                    ResourcePackCallback { id, status, _ ->
                        if (!status.intermediate()) println("[pack] $id -> $status")
                    },
                )
                .build()
        url = versionedUrl
        return artifact.sha1
    }
}

private fun registerCommands(packs: Packs) {
    val open = Command("gui")
    open.setDefaultExecutor { sender, _ -> (sender as? Player)?.let(::openDemoGui) }
    MinecraftServer.getCommandManager().register(open)

    val story = Command("story")
    story.setDefaultExecutor { sender, _ -> (sender as? Player)?.let(::openStorybook) }
    MinecraftServer.getCommandManager().register(story)

    val overview = Command("overview")
    overview.setDefaultExecutor { sender, _ -> (sender as? Player)?.let(::openOverview) }
    MinecraftServer.getCommandManager().register(overview)

    val highlight = Command("highlight")
    highlight.setDefaultExecutor { sender, _ ->
        val player = sender as? Player ?: return@setDefaultExecutor
        if (player.packPending()) {
            player.sendMessage(stillLoading())
        } else {
            DemoTheme.blankHighlight = !DemoTheme.blankHighlight
            val sha1 = packs.rebuild()
            player.sendResourcePacks(packs.request)
            player.sendMessage(
                Component.text(
                    if (DemoTheme.blankHighlight) {
                        "Vanilla hover box blanked everywhere; themed screens hand it back per slot."
                    } else {
                        "Vanilla hover box restored everywhere — multi-slot buttons flash on click."
                    },
                    NamedTextColor.GREEN,
                )
            )
            player.sendMessage(rebuilt(sha1))
        }
    }
    MinecraftServer.getCommandManager().register(highlight)

    // No pack rebuild here, deliberately: both patch families ship in the pack, so this only
    // changes which glyph the server names. Reopening the screen is enough to see it.
    val tint = Command("tint")
    tint.setDefaultExecutor { sender, _ ->
        val player = sender as? Player ?: return@setDefaultExecutor
        DemoTheme.tintEmpty = !DemoTheme.tintEmpty
        player.sendMessage(
            Component.text(
                if (DemoTheme.tintEmpty) {
                    "Empty tiles now hover to a soft tint. Reopen the screen."
                } else {
                    "Empty tiles no longer answer the cursor at all. Reopen the screen."
                },
                NamedTextColor.GREEN,
            )
        )
    }
    MinecraftServer.getCommandManager().register(tint)

    val market = Command("market")
    market.setDefaultExecutor { sender, _ -> (sender as? Player)?.let { openMarket(it) } }
    MinecraftServer.getCommandManager().register(market)

    val mapdemo = Command("mapdemo")
    mapdemo.setDefaultExecutor { sender, _ -> (sender as? Player)?.let(::openMapDemo) }
    MinecraftServer.getCommandManager().register(mapdemo)

    val menu = Command("menu")
    menu.setDefaultExecutor { sender, _ -> (sender as? Player)?.let(::openMenu) }
    MinecraftServer.getCommandManager().register(menu)

    val dialogs = Command("dialog")
    dialogs.setDefaultExecutor { sender, _ -> (sender as? Player)?.let(::openDialogIndex) }
    MinecraftServer.getCommandManager().register(dialogs)

    val screens = Command("ui")
    screens.setDefaultExecutor { sender, _ -> (sender as? Player)?.let(::openScreenGallery) }
    MinecraftServer.getCommandManager().register(screens)

    val hover = Command("hover")
    hover.setDefaultExecutor { sender, _ -> (sender as? Player)?.let(::openHoverGui) }
    MinecraftServer.getCommandManager().register(hover)

    // The glow lives in the pack, so switching it is a rebuild and a fresh download — the same
    // path /tune y takes, and it needs the same guard against replacing a push still in flight.
    val glow = Command("glow")
    glow.setDefaultExecutor { sender, _ ->
        val player = sender as? Player ?: return@setDefaultExecutor
        if (player.packPending()) {
            player.sendMessage(stillLoading())
        } else {
            DemoTheme.slotGlow = !DemoTheme.slotGlow
            val sha1 = packs.rebuild()
            player.sendResourcePacks(packs.request)
            player.sendMessage(
                Component.text(
                    if (DemoTheme.slotGlow) {
                        "Slot glow on — in every container, including your own inventory."
                    } else {
                        "Slot glow off — vanilla's highlight is back."
                    },
                    NamedTextColor.GREEN,
                )
            )
            player.sendMessage(rebuilt(sha1))
        }
    }
    MinecraftServer.getCommandManager().register(glow)

    // ArgumentType.Word rather than a literal per branch: one syntax, dispatched below, keeps the
    // command surface small enough to read.
    val field = ArgumentType.Word("field")
    val value = ArgumentType.Integer("value")

    val tune = Command("tune")
    tune.setDefaultExecutor { sender, _ -> sender.sendMessage(usage()) }

    tune.addSyntax(
        CommandExecutor { sender, context ->
            val player = sender as? Player ?: return@CommandExecutor
            when (context.get(field)) {
                "show" -> player.sendMessage(showTuning())
                "reset" ->
                    if (player.packPending()) {
                        player.sendMessage(stillLoading())
                    } else {
                        DemoTheme.reset()
                        val sha1 = packs.rebuild()
                        player.sendResourcePacks(packs.request)
                        player.sendMessage(rebuilt(sha1))
                    }
                else -> player.sendMessage(usage())
            }
        },
        field,
    )

    tune.addSyntax(
        CommandExecutor { sender, context ->
            val player = sender as? Player ?: return@CommandExecutor
            val amount = context.get(value)
            when (context.get(field)) {
                // Horizontal offset lives only in the title string, so it takes effect on the next
                // open with no download involved.
                "x" -> {
                    val previous = DemoTheme.offsetX
                    DemoTheme.offsetX = amount
                    if (player.accepts { DemoTheme.offsetX = previous }) {
                        openDemoGui(player)
                        player.sendMessage(applied("offsetX", amount))
                    }
                }
                // Vertical offset becomes the glyph's ascent, which lives in the font file — so it
                // is a new pack and a new download. The GUI is deliberately not reopened here: the
                // client is still fetching, and reopening now would show the old artwork.
                "y" ->
                    if (player.packPending()) {
                        player.sendMessage(stillLoading())
                    } else {
                        val previous = DemoTheme.offsetY
                        DemoTheme.offsetY = amount
                        if (player.accepts { DemoTheme.offsetY = previous }) {
                            val sha1 = packs.rebuild()
                            player.sendResourcePacks(packs.request)
                            player.sendMessage(applied("offsetY", amount))
                            player.sendMessage(rebuilt(sha1))
                        }
                    }
                else -> player.sendMessage(usage())
            }
        },
        field,
        value,
    )
    MinecraftServer.getCommandManager().register(tune)
}

/**
 * This machine's own routable address, for the client to fetch the pack from.
 *
 * `127.0.0.1` is the *client's* loopback, not the server's — the client is what resolves this URL.
 * When the two are not the same host, and under WSL or a VM they are not, that points the client at
 * whatever it happens to be running on that port, and the download fails without a single request
 * ever reaching this process. A routable address works in both cases.
 */
private fun reachableAddress(): String =
    NetworkInterface.networkInterfaces()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses() }
        .filter { it is Inet4Address && !it.isLoopbackAddress }
        .map { it.hostAddress }
        .findFirst()
        .orElse("127.0.0.1")

/**
 * True while a pack push this player has not answered yet is still outstanding.
 *
 * Minestom's future is non-null only while pushes are pending. Sending another one now would pop
 * the outstanding pack, the client would report it discarded, and because the push was required
 * that terminal status kicks the player out of their own tuning session.
 */
private fun Player.packPending(): Boolean = resourcePackFuture != null

/**
 * Runs the theme's own validation against the value just written, rolling back through [restore]
 * if it is rejected.
 *
 * The offsets live in a process-global object, so a value the theme refuses has to be undone —
 * otherwise every later command works from a theme that can no longer be built at all.
 */
private inline fun Player.accepts(restore: () -> Unit): Boolean =
    try {
        DemoTheme.current()
        true
    } catch (rejected: IllegalArgumentException) {
        restore()
        sendMessage(Component.text(rejected.message ?: "value rejected", NamedTextColor.RED))
        false
    }

private fun stillLoading(): Component =
    Component.text(
        "The last pack is still on its way — wait for it to land before tuning again.",
        NamedTextColor.YELLOW,
    )

private fun welcome(): Component =
    Component.text()
        .append(Component.text("library-gui theme demo\n", NamedTextColor.GOLD))
        .append(Component.text("/gui", NamedTextColor.AQUA))
        .append(Component.text(" opens the themed GUI. The panel draws a slot grid and 8px ", NamedTextColor.GRAY))
        .append(Component.text("rulers — line them up with the real slots.\n", NamedTextColor.GRAY))
        .append(Component.text("/story", NamedTextColor.AQUA))
        .append(
            Component.text(
                " opens the storybook: every element, every container type.\n",
                NamedTextColor.GRAY,
            )
        )
        .append(Component.text("/mapdemo", NamedTextColor.AQUA))
        .append(
            Component.text(
                " draws a picture per player and sends it as map data — the one image in this demo " +
                    "that is not in the pack.\n",
                NamedTextColor.GRAY,
            )
        )
        .append(Component.text("/market", NamedTextColor.AQUA))
        .append(
            Component.text(
                " opens a shop: the description lands in a fixed card instead of a tooltip, every " +
                    "line of it composed at runtime, and the search runs through a dialog because a " +
                    "container cannot read a keystroke.\n",
                NamedTextColor.GRAY,
            )
        )
        .append(Component.text("/hover", NamedTextColor.AQUA))
        .append(
            Component.text(
                " opens a five-slot screen with the hover effect and nothing else.\n",
                NamedTextColor.GRAY,
            )
        )
        .append(usage())
        .build()

private fun usage(): Component =
    Component.text()
        .append(Component.text("/tune x <px>", NamedTextColor.AQUA))
        .append(Component.text(" instant   ", NamedTextColor.DARK_GRAY))
        .append(Component.text("/tune y <px>", NamedTextColor.AQUA))
        .append(Component.text(" rebuilds the pack   ", NamedTextColor.DARK_GRAY))
        .append(Component.text("/tune show|reset", NamedTextColor.AQUA))
        .build()

private fun applied(name: String, amount: Int): Component =
    Component.text("$name = $amount", NamedTextColor.GREEN)

private fun rebuilt(sha1: String): Component =
    Component.text()
        .append(Component.text("pack rebuilt (sha1 ${sha1.take(12)}…), downloading — ", NamedTextColor.YELLOW))
        .append(Component.text("/gui", NamedTextColor.AQUA))
        .append(Component.text(" again once it lands.", NamedTextColor.YELLOW))
        .build()

private fun showTuning(): Component =
    Component.text()
        .append(Component.text("Paste into your theme:\n", NamedTextColor.GRAY))
        .append(Component.text(DemoTheme.snippet(), NamedTextColor.WHITE))
        .build()

private fun printBanner(url: String, sha1: String, serverPort: Int) {
    println(
        """
        |
        |  library-gui theme demo
        |  ----------------------
        |  server   localhost:$serverPort   (offline mode — dev only)
        |  pack     $url
        |  sha1     $sha1
        |
        |  Join, then: /gui   and   /tune x|y <px>   /tune show|reset
        |
        |  PACK_HOST is $url's host. If the client runs on another machine,
        |  set PACK_HOST to an address that machine can reach — the client, not
        |  the server, downloads the pack.
        |
        """
            .trimMargin(),
    )
}
