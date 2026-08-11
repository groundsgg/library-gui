package gg.grounds.gui.pack

import gg.grounds.gui.theme.LAST_PRE_MINOR_FORMAT
import gg.grounds.gui.theme.Theme
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.outputStream
import kotlin.io.path.writeText

/**
 * Writes [theme] as a complete resource-pack tree under [out], reading artwork from [assets].
 *
 * [out] must be absent or empty so a texture from a previous theme cannot survive into the pack.
 */
@Deprecated(
    "Use Theme.toPackContribution(assets); final pack writing belongs to resource-pack-builder."
)
fun writePack(theme: Theme, assets: Path, out: Path) {
    require(assets.isDirectory()) { "asset root $assets does not exist" }
    require(!out.exists() || (out.isDirectory() && out.listDirectoryEntries().isEmpty())) {
        "output directory $out must be absent or empty so nothing stale leaks into the pack"
    }

    ThemePackPlan.from(theme).materialize(assets).forEach { entry ->
        val target = out.resolve(entry.path.value)
        target.parent.createDirectories()
        entry.source.openStream().use { input -> Files.copy(input, target) }
    }
    writeLegacyMcmeta(theme, out)
}

/**
 * Zips [packDir] into [archive] and returns the archive's SHA-1, which is what
 * `Player.sendResourcePacks` needs alongside the download URL.
 */
@Deprecated("Use ResourcePackComposer and ZipPackWriter for final artifacts.")
fun zipPack(packDir: Path, archive: Path): String {
    require(packDir.isDirectory()) { "pack directory $packDir does not exist" }
    archive.parent?.createDirectories()

    val files =
        Files.walk(packDir)
            .use { paths -> paths.filter { it.isRegularFile() }.toList() }
            .sortedBy { packDir.relativize(it).joinToString("/") }

    ZipOutputStream(archive.outputStream().buffered()).use { zip ->
        files.forEach { file ->
            val entry = ZipEntry(packDir.relativize(file).joinToString("/"))
            entry.setTimeLocal(ZIP_EPOCH)
            zip.putNextEntry(entry)
            file.inputStream().buffered().use { input -> input.copyTo(zip) }
            zip.closeEntry()
        }
    }
    return sha1(archive)
}

/** The DOS timestamp Gradle also stamps into reproducible archives. */
private val ZIP_EPOCH = LocalDateTime.of(1980, 2, 1, 0, 0, 0)

private fun writeLegacyMcmeta(theme: Theme, out: Path) {
    val format = theme.packFormat
    val fields = buildList {
        add("pack_format" to Json.number(format.format))
        add("description" to Json.string(theme.description))
        if (format.minInclusive > LAST_PRE_MINOR_FORMAT) {
            add("min_format" to Json.number(format.minInclusive))
            add("max_format" to Json.number(format.maxInclusive))
        } else {
            add(
                "supported_formats" to
                    Json.obj(
                        "min_inclusive" to Json.number(format.minInclusive),
                        "max_inclusive" to Json.number(format.maxInclusive),
                    )
            )
        }
    }
    val target = out.resolve("pack.mcmeta")
    target.parent?.createDirectories()
    target.writeText(Json.obj("pack" to Json.obj(*fields.toTypedArray())))
}

private fun sha1(file: Path): String {
    val digest = MessageDigest.getInstance("SHA-1")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(1 shl 16)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/** The vanilla paths this theme's pack will overwrite, relative to `assets/minecraft`. */
fun Theme.vanillaOverrides(): List<String> =
    ThemePackPlan.from(this).vanillaPaths.map { it.value.removePrefix("assets/minecraft/") }
