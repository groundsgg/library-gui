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
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.instance.LightingChunk
import net.minestom.server.instance.block.Block

private val ART: Path = Path.of("art")
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
    val url = host.url(packHostName)
    val packs = Packs(host, url, packRequired)
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

    MinecraftServer.getGlobalEventHandler().addListener(PlayerSpawnEvent::class.java) { event ->
        event.player.sendMessage(welcome())
    }

    registerCommands(packs)
    printBanner(url, sha1, serverPort)
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
 * Each rebuild gets a fresh pack id so a client can never match new bytes against a cached entry,
 * and `replace(true)` drops whatever it was holding before.
 */
private class Packs(
    private val host: PackHost,
    private val url: String,
    private val required: Boolean,
) {
    @Volatile lateinit var request: ResourcePackRequest
        private set

    /** Regenerates the pack from the current offsets, republishes it, and returns its SHA-1. */
    fun rebuild(): String {
        val (zip, sha1) = DemoTheme.rebuild(ART, OUT)
        host.publish(zip)
        // The hash is of exactly the bytes the host now serves, so the two cannot drift — which
        // matters, because a required pack whose hash is stale kicks every player who joins.
        request =
            ResourcePackRequest.resourcePackRequest()
                .packs(ResourcePackInfo.resourcePackInfo(UUID.randomUUID(), URI.create(url), sha1))
                .required(required)
                .replace(true)
                .prompt(Component.text("library-gui theme demo", NamedTextColor.GOLD))
                .callback(
                    ResourcePackCallback { id, status, _ ->
                        if (!status.intermediate()) println("[pack] $id -> $status")
                    },
                )
                .build()
        return sha1
    }
}

private fun registerCommands(packs: Packs) {
    val open = Command("gui")
    open.setDefaultExecutor { sender, _ -> (sender as? Player)?.let(::openDemoGui) }
    MinecraftServer.getCommandManager().register(open)

    val story = Command("story")
    story.setDefaultExecutor { sender, _ -> (sender as? Player)?.let(::openStorybook) }
    MinecraftServer.getCommandManager().register(story)

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
        |  Join, then: /gui   and   /tune x|y|advance <px>   /tune show
        |
        |  PACK_HOST is $url's host. If the client runs on another machine,
        |  set PACK_HOST to an address that machine can reach — the client, not
        |  the server, downloads the pack.
        |
        """
            .trimMargin(),
    )
}
