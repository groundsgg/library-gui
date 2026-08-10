package gg.grounds.gui.pack

/**
 * Just enough JSON to emit a resource pack.
 *
 * The pack files this library writes are small and fixed in shape, and their bytes decide the
 * pack's hash — so they are built from strings that are trivially diffable rather than through a
 * serialization library the host server would then have to carry.
 */
internal object Json {
    /** An object from already-encoded values, in the order given. */
    fun obj(vararg fields: Pair<String, String>): String =
        fields.joinToString(",", "{", "}") { (key, value) -> "${string(key)}:$value" }

    /** An array of already-encoded values. */
    fun array(values: List<String>): String = values.joinToString(",", "[", "]")

    /**
     * A JSON string. Everything outside printable ASCII is escaped, which keeps the private use
     * area glyphs readable in a diff and independent of how a client decodes the file.
     */
    fun string(value: String): String {
        val out = StringBuilder(value.length + 2)
        out.append('"')
        for (char in value) {
            when {
                char == '"' -> out.append("\\\"")
                char == '\\' -> out.append("\\\\")
                char == '\n' -> out.append("\\n")
                char == '\r' -> out.append("\\r")
                char == '\t' -> out.append("\\t")
                char.code in 0x20..0x7E -> out.append(char)
                else -> out.append("\\u").append("%04x".format(char.code))
            }
        }
        out.append('"')
        return out.toString()
    }

    /** A JSON number. */
    fun number(value: Int): String = value.toString()
}
