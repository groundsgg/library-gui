package gg.grounds.gui.demo.art

import java.nio.file.Path

/**
 * Regenerates the demo's artwork.
 *
 * Run with `./gradlew :examples:theme-demo:paintArt`. It reads the raw dumps under `art/vanilla`,
 * which the repository deliberately does not carry — `art/README.md` says how to recreate them.
 */
fun main(args: Array<String>) {
    val out = Path.of(args.getOrElse(0) { "art" })
    val dumps = Path.of(args.getOrElse(1) { "art/vanilla" })
    paintAll(dumps, out)
    println("painted the demo's artwork into $out")
}
