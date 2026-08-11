package gg.grounds.gui.demo

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.readBytes

/**
 * Serves the generated pack over plain HTTP so a local client can fetch it.
 *
 * Each artifact has an immutable content-addressed URL. Published snapshots deliberately remain
 * in memory for the demo process lifetime, so a client that downloads an older request later still
 * receives the exact bytes whose hash it was given.
 */
class PackHost(private val port: Int) {
    private val published = ConcurrentHashMap<String, ByteArray>()

    private val server: HttpServer = HttpServer.create(InetSocketAddress(port), 0)

    init {
        server.createContext("/packs") { exchange ->
            val hash = exchange.requestURI.path.removePrefix("/packs/").removeSuffix(".zip")
            val body = published[hash]
            val status = if (body == null) 404 else 200
            // Logged because the alternative is guessing. A failed pack download tells the server
            // only FAILED_DOWNLOAD; whether the request ever arrived is the difference between a
            // broken pack and a client that cannot reach this host at all.
            println("[pack-host] ${exchange.requestMethod} ${exchange.requestURI} from " +
                "${exchange.remoteAddress} -> $status (${body?.size ?: 0} bytes)")
            if (body != null) exchange.responseHeaders.add("Content-Type", "application/zip")
            exchange.sendResponseHeaders(status, body?.size?.toLong() ?: -1)
            exchange.responseBody.use { if (body != null) it.write(body) }
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

    /** Snapshots [zip] under [sha1], rejecting a hash/byte mismatch or a hash collision. */
    fun publish(zip: Path, sha1: String) {
        val bytes = zip.readBytes()
        require(sha1(bytes) == sha1) {
            "Supplied SHA-1 $sha1 does not match bytes from $zip"
        }
        published.compute(sha1) { _, previous ->
            when {
                previous == null -> bytes
                previous.contentEquals(bytes) -> previous
                else -> error("SHA-1 $sha1 is already bound to different pack bytes")
            }
        }
    }

    /** The immutable artifact URL to hand the client. [host] must be reachable by that client. */
    fun url(host: String, sha1: String): String = "http://$host:${server.address.port}/packs/$sha1.zip"

    private fun sha1(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
