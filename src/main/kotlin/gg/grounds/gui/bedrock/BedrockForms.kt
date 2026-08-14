package gg.grounds.gui.bedrock

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerPluginMessageEvent

/**
 * Bedrock forms, over Floodgate's own plugin-message channel.
 *
 * A Bedrock player reaches the network through Geyser, so by the time this server sees them they
 * are an ordinary Java-protocol player — which is exactly why a themed [gg.grounds.gui.Gui] fails
 * for them. The theme draws its panels with private-use glyphs positioned by negative advance and
 * composited by a Java core shader; the Bedrock client has neither. What arrives is a bare
 * container with missing-glyph text where the interface should be.
 *
 * Forms are Bedrock's native answer, and they need no resource pack.
 *
 * **This server sends them itself; Floodgate is not on the classpath and does not need to be.**
 * Floodgate's own backend-to-client path is this channel, and its proxy handler forwards a message
 * from a *server* straight to the client (`Result.forward()`), then routes the response back to the
 * connected server whenever the form id has the `0x8000` bit clear. That bit means "sent by a
 * proxy", so a backend keeps it clear and owns its own id space.
 *
 * Wire format, from Floodgate's `FormChannel.createFormData`:
 * ```text
 * send      byte 0     form type: 0 = form (simple), 1 = modal, 2 = custom_form
 *           byte 1..2  form id, big-endian short, high bit clear
 *           byte 3..N  the form's JSON, UTF-8
 *
 * response  byte 0..1  form id, big-endian short
 *           byte 2..N  response data, UTF-8
 * ```
 *
 * Call [install] once at startup. Without it, forms are still sent but no response ever arrives, so
 * every callback would be stranded — [install] is what makes a form a question rather than a
 * broadcast.
 */
object BedrockForms {

    /** Floodgate's channel. Both directions travel on it. */
    const val CHANNEL: String = "floodgate:form"

    private const val TYPE_MODAL: Byte = 1
    private const val TYPE_CUSTOM: Byte = 2

    /**
     * Whether this player is on Bedrock.
     *
     * Floodgate derives a Bedrock player's UUID from their XUID and zeroes the high eight bytes, so
     * this is true for Bedrock players and for no real Java UUID. It costs nothing and needs no
     * Floodgate dependency, which matters here: a Minestom backend receives the UUID through modern
     * forwarding and little else.
     *
     * A player who linked a Java account is a caveat worth knowing rather than one this can solve:
     * neighbouring services may hold that account's id instead, so confirm which id a given service
     * actually sees before branching on it there.
     */
    fun isBedrock(player: Player): Boolean = player.uuid.mostSignificantBits == 0L

    private class Pending(val onResponse: (String?) -> Unit)

    private class State {
        var nextId: Int = 0
        val open = ConcurrentHashMap<Short, Pending>()
    }

    private val states = ConcurrentHashMap<UUID, State>()

    @Volatile private var installed = false

    /**
     * Registers the response listener. Idempotent, so calling it from several modules is harmless.
     */
    @Synchronized
    fun install() {
        if (installed) return
        installed = true
        val node = MinecraftServer.getGlobalEventHandler()
        node.addListener(PlayerPluginMessageEvent::class.java) { event ->
            if (event.identifier == CHANNEL) handleResponse(event.player, event.message)
        }
        // A disconnect answers every form the player still had open. Without this a caller that
        // waits for an answer waits forever, and the map leaks one entry per abandoned form.
        node.addListener(PlayerDisconnectEvent::class.java) { event ->
            states.remove(event.player.uuid)?.open?.values?.forEach { it.onResponse(null) }
        }
    }

    /**
     * Shows a yes/no dialog. [onResponse] receives true, false, or null when the player dismissed
     * it — so it fires exactly once for every form shown, which is the contract
     * [gg.grounds.gui.confirmGui] already promises on Java.
     */
    fun modal(
        player: Player,
        title: Component,
        content: Component,
        button1: Component,
        button2: Component,
        onResponse: (Boolean?) -> Unit,
    ) {
        val json = modalJson(plain(title), plain(content), plain(button1), plain(button2))
        send(player, TYPE_MODAL, json) { response ->
            onResponse(
                when (response?.trim()) {
                    "true" -> true
                    "false" -> false
                    else -> null
                }
            )
        }
    }

    /**
     * Shows a single text input. [onResponse] receives the text, or null when the player dismissed
     * the form.
     *
     * This is the one place where Bedrock is better served than Java: the Java path types into an
     * anvil rename field, which sends on roughly every keystroke and only works once the first slot
     * holds an item.
     */
    fun textInput(
        player: Player,
        title: Component,
        label: Component,
        placeholder: String = "",
        initial: String = "",
        onResponse: (String?) -> Unit,
    ) {
        val json = customInputJson(plain(title), plain(label), placeholder, initial)
        send(player, TYPE_CUSTOM, json) { response -> onResponse(firstStringOfJsonArray(response)) }
    }

    /**
     * Cumulus's `modal` shape. Field names and the `type` value come from Cumulus's own codec and
     * its `@SerializedName` annotations, not from guesswork — a wrong key here is a form that
     * silently never appears.
     */
    internal fun modalJson(
        title: String,
        content: String,
        button1: String,
        button2: String,
    ): String = buildString {
        append("{\"type\":\"modal\",\"title\":")
        appendJsonString(title)
        append(",\"content\":")
        appendJsonString(content)
        append(",\"button1\":")
        appendJsonString(button1)
        append(",\"button2\":")
        appendJsonString(button2)
        append('}')
    }

    /** Cumulus's `custom_form` with a single `input` component. */
    internal fun customInputJson(
        title: String,
        label: String,
        placeholder: String,
        initial: String,
    ): String = buildString {
        append("{\"type\":\"custom_form\",\"title\":")
        appendJsonString(title)
        append(",\"content\":[{\"type\":\"input\",\"text\":")
        appendJsonString(label)
        append(",\"placeholder\":")
        appendJsonString(placeholder)
        append(",\"default\":")
        appendJsonString(initial)
        append("}]}")
    }

    private fun send(player: Player, type: Byte, json: String, onResponse: (String?) -> Unit) {
        val state = states.computeIfAbsent(player.uuid) { State() }
        val id: Short
        synchronized(state) {
            // Short.MAX_VALUE keeps the 0x8000 bit clear, which is what tells the proxy this form
            // came from a server and its response belongs back here.
            id = state.nextId.toShort()
            state.nextId = if (state.nextId == Short.MAX_VALUE.toInt()) 0 else state.nextId + 1
        }
        state.open[id] = Pending(onResponse)

        val body = json.toByteArray(Charsets.UTF_8)
        val data = ByteArray(body.size + 3)
        data[0] = type
        data[1] = (id.toInt() shr 8 and 0xFF).toByte()
        data[2] = (id.toInt() and 0xFF).toByte()
        body.copyInto(data, 3)
        player.sendPluginMessage(CHANNEL, data)
    }

    private fun handleResponse(player: Player, data: ByteArray) {
        if (data.size < 2) return
        val id = ((data[0].toInt() and 0xFF) shl 8 or (data[1].toInt() and 0xFF)).toShort()
        val pending = states[player.uuid]?.open?.remove(id) ?: return
        val body = String(data, 2, data.size - 2, Charsets.UTF_8)
        // Geyser answers a dismissed form with an empty or null body; Floodgate's own codecs treat
        // both as "no answer", and so does every caller here.
        pending.onResponse(if (body.isBlank() || body.trim() == "null") null else body)
    }

    private fun plain(component: Component): String =
        PlainTextComponentSerializer.plainText().serialize(component)

    /**
     * Pulls the first element out of a custom form's response array.
     *
     * A custom form answers with a JSON array, one entry per component. There is exactly one
     * component here, so a full parser would be more machinery than the shape deserves — but the
     * value is player-typed, so quotes and escapes inside it are ordinary rather than exceptional.
     */
    internal fun firstStringOfJsonArray(response: String?): String? {
        val trimmed = response?.trim() ?: return null
        if (!trimmed.startsWith("[")) return null
        val firstQuote = trimmed.indexOf('"')
        if (firstQuote < 0) return null
        val out = StringBuilder()
        var i = firstQuote + 1
        while (i < trimmed.length) {
            when (val c = trimmed[i]) {
                '"' -> return out.toString()
                '\\' -> {
                    if (i + 1 >= trimmed.length) return out.toString()
                    when (val escaped = trimmed[i + 1]) {
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'b' -> out.append('\b')
                        'f' -> out.append('')
                        'u' -> {
                            if (i + 5 < trimmed.length) {
                                out.append(trimmed.substring(i + 2, i + 6).toInt(16).toChar())
                                i += 4
                            }
                        }
                        else -> out.append(escaped)
                    }
                    i++
                }
                else -> out.append(c)
            }
            i++
        }
        return out.toString()
    }

    private fun StringBuilder.appendJsonString(value: String) {
        append('"')
        for (c in value) {
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c < ' ' -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
        }
        append('"')
    }
}
