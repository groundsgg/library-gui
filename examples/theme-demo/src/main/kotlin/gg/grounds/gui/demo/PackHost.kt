package gg.grounds.gui.demo

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Path
import kotlin.io.path.readBytes

/**
 * Serves the generated pack over plain HTTP so a local client can fetch it.
 *
 * The bytes are swappable: tuning a panel's vertical offset rewrites the font's ascent, which is
 * part of the pack, so that adjustment has to go out as a new download. Horizontal offset and
 * advance do not — they only change the title string the server sends. Keeping that distinction
 * visible is half the point of this demo.
 */
class PackHost(private val port: Int) {
    @Volatile private var payload: ByteArray = ByteArray(0)

    private val server: HttpServer = HttpServer.create(InetSocketAddress(port), 0)

    init {
        server.createContext("/pack.zip") { exchange ->
            val body = payload
            val status = if (body.isEmpty()) 404 else 200
            // Logged because the alternative is guessing. A failed pack download tells the server
            // only FAILED_DOWNLOAD; whether the request ever arrived is the difference between a
            // broken pack and a client that cannot reach this host at all.
            println("[pack-host] ${exchange.requestMethod} ${exchange.requestURI} from " +
                "${exchange.remoteAddress} -> $status (${body.size} bytes)")
            exchange.responseHeaders.add("Content-Type", "application/zip")
            exchange.sendResponseHeaders(status, body.size.toLong())
            exchange.responseBody.use { if (body.isNotEmpty()) it.write(body) }
        }
        server.executor = null
    }

    /** Starts listening on every interface, so a client on another machine can reach it too. */
    fun start() {
        server.start()
    }

    /**
     * Stops listening and releases the port.
     *
     * Worth calling on any failure path: the JDK's HTTP dispatcher is a non-daemon thread, so a
     * process that gives up after starting this would otherwise never exit — it would sit there
     * holding the port with nothing serving players. A shutdown hook is no help, because the JVM
     * never gets as far as shutting down.
     */
    fun stop() {
        server.stop(0)
    }

    /** Publishes [zip]'s current bytes; later calls replace what the next download returns. */
    fun publish(zip: Path) {
        payload = zip.readBytes()
    }

    /** The URL to hand the client. [host] must be an address that client can actually reach. */
    fun url(host: String): String = "http://$host:$port/pack.zip"
}
