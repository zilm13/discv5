package org.ethereum.discv5.util


/**
 * Copied from https://github.com/libp2p/jvm-libp2p/blob/feature/simulator/src/test/kotlin/io/libp2p/tools/Ext.kt
 * Aligns [String] to match width
 */
fun String.align(width: Int, alignLeft: Boolean = true, fillChar: Char = ' '): String {
    val n = kotlin.math.max(1, width - length)
    return if (alignLeft) this + fillChar.toString().repeat(n) else fillChar.toString().repeat(n) + this
}

/**
 * Copied from https://github.com/libp2p/jvm-libp2p/blob/feature/simulator/src/test/kotlin/io/libp2p/tools/Ext.kt
 * Formats string as table
 */
fun String.formatTable(firstLineHeaders: Boolean = true, separator: String = "\t", alignLeft: Boolean = true): String {
    val list = this.split("\n").map { it.split(separator) }
    require(list.map { it.size }.min() == list.map { it.size }.max()) { "Different number of columns" }
    val colSizes = list[0].indices.map { col -> list.map { it[col].length + 1 }.max() }
    val strings = list.map { raw ->
        raw.indices.map { raw[it].align(colSizes[it]!!, alignLeft) }
            .joinToString("")
    }.toMutableList()

    if (firstLineHeaders) {
        strings.add(1, colSizes.map { "-".repeat(it!! - 1) + " " }.joinToString(""))
    }
    return strings.joinToString("\n")
}