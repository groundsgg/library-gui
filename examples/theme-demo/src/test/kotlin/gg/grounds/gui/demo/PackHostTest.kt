package gg.grounds.gui.demo

import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PackHostTest {
    @Test
    fun `published pack URLs retain their original bytes after later publications`() {
        val first = pack("first.zip", "first artifact".encodeToByteArray())
        val second = pack("second.zip", "second artifact".encodeToByteArray())
        val host = PackHost(0)
        try {
            host.publish(first.path, first.sha1)
            val firstUrl = host.url("127.0.0.1", first.sha1)
            host.start()

            host.publish(second.path, second.sha1)
            val secondUrl = host.url("127.0.0.1", second.sha1)

            assertTrue(firstUrl.contains(first.sha1), firstUrl)
            assertTrue(secondUrl.contains(second.sha1), secondUrl)
            assertArtifact(firstUrl, first)
            assertArtifact(secondUrl, second)
            assertEquals(404, response(host.url("127.0.0.1", "0".repeat(40))).status)
        } finally {
            host.stop()
        }
    }

    @Test
    fun `publish rejects a hash that does not describe the supplied bytes`() {
        val pack = pack("artifact.zip", "artifact".encodeToByteArray())
        val host = PackHost(0)
        try {
            val failure = assertFailsWith<IllegalArgumentException> {
                host.publish(pack.path, "0".repeat(40))
            }

            assertTrue("SHA-1" in failure.message.orEmpty(), failure.message)
        } finally {
            host.stop()
        }
    }

    private fun assertArtifact(url: String, expected: Artifact) {
        val response = response(url)

        assertEquals(200, response.status)
        assertEquals("application/zip", response.contentType)
        assertContentEquals(expected.bytes, response.body)
        assertEquals(expected.sha1, sha1(response.body))
    }

    private fun response(url: String): Response {
        val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
        return try {
            Response(
                status = connection.responseCode,
                contentType = connection.contentType,
                body = (if (connection.responseCode == 200) connection.inputStream else connection.errorStream)
                    ?.readBytes()
                    ?: ByteArray(0),
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun pack(name: String, bytes: ByteArray): Artifact {
        val path = createTempDirectory("pack-host").resolve(name)
        Files.write(path, bytes)
        return Artifact(path, bytes, sha1(bytes))
    }

    private fun sha1(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private data class Artifact(val path: java.nio.file.Path, val bytes: ByteArray, val sha1: String)

    private data class Response(val status: Int, val contentType: String?, val body: ByteArray)
}
