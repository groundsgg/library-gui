package gg.grounds.gui.pack

import gg.grounds.gui.theme.Theme
import gg.grounds.resourcepack.api.ByteArrayEntrySource
import gg.grounds.resourcepack.api.FileEntrySource
import gg.grounds.resourcepack.api.PackEntrySource
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile

internal fun checkedImageFile(
    theme: Theme,
    assets: Path,
    texture: String,
    validate: (BufferedImage) -> Unit = {},
): PackEntrySource {
    checkedImage(theme, assets, texture, validate)
    return FileEntrySource(assets.resolve(texture))
}

internal fun checkedImage(
    theme: Theme,
    assets: Path,
    texture: String,
    validate: (BufferedImage) -> Unit = {},
): BufferedImage {
    val source = assets.resolve(texture)
    require(source.isRegularFile()) {
        "Theme '${theme.namespace}' texture '$texture' source path $source was not found"
    }
    val image =
        try {
            source.inputStream().buffered().use { ImageIO.read(it) }
        } catch (failure: Exception) {
            throw IllegalArgumentException(
                "Theme '${theme.namespace}' texture '$texture' source path $source is not a readable image",
                failure,
            )
        }
            ?: throw IllegalArgumentException(
                "Theme '${theme.namespace}' texture '$texture' source path $source is not a readable image"
            )
    validate(image)
    return image
}

internal fun generatedText(value: String): PackEntrySource =
    ByteArrayEntrySource(value.toByteArray(UTF_8))

internal fun generatedPng(image: BufferedImage): PackEntrySource {
    val bytes = ByteArrayOutputStream()
    check(ImageIO.write(image, "png", bytes)) { "No PNG writer is available" }
    return ByteArrayEntrySource(bytes.toByteArray())
}

internal fun generatedBlankPng(size: Int): PackEntrySource =
    generatedPng(BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB))

internal fun themeClientAdvance(image: BufferedImage, scale: Int): Int {
    var column = image.width - 1
    if (image.colorModel.hasAlpha()) {
        while (column >= 0) {
            if ((0 until image.height).any { y -> (image.getRGB(column, y) ushr 24) != 0 }) break
            column--
        }
    }
    return (column + 1) * scale + 1
}
