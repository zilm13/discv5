package org.ethereum.discv5.util

/**
 * Wraps ByteArray to make equals work
 */
class ByteArrayWrapper(private val delegate: ByteArray) {
    /** Returns the number of elements in the array. */
    val size: Int = delegate.size


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ByteArrayWrapper

        if (!delegate.contentEquals(other.delegate)) return false

        return true
    }

    override fun hashCode(): Int {
        return delegate.contentHashCode()
    }
}