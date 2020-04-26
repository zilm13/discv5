package org.ethereum.discv5.util

/**
 * Wraps ByteArray to make equals work
 */
class ByteArrayWrapper(val bytes: ByteArray) {
    /** Returns the number of elements in the array. */
    val size: Int = bytes.size


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ByteArrayWrapper

        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }

    override fun hashCode(): Int {
        return bytes.contentHashCode()
    }
}