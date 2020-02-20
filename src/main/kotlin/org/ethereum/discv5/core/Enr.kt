package org.ethereum.discv5.core

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.UnknownFieldSet
import io.libp2p.core.PeerId
import io.libp2p.core.multiformats.Multiaddr
import java.io.ByteArrayOutputStream
import kotlin.experimental.xor

/**
 * Ethereum Node Record
 */
data class Enr(val addr: Multiaddr, val id: PeerId) {
    private constructor(pair: Pair<Multiaddr, PeerId>) : this(pair.first, pair.second)
    constructor(bytes: ByteArray) : this(fromBytes(bytes))

    fun getBytes(): ByteArray {
        val baos = ByteArrayOutputStream()
        val out: CodedOutputStream = CodedOutputStream.newInstance(baos)
        out.writeByteArray(1, addr.getBytes())
        out.writeByteArray(2, id.bytes)
        out.flush()
        return baos.toByteArray()
    }

    companion object {
        private fun fromBytes(bytes: ByteArray): Pair<Multiaddr, PeerId> {
            val inputStream: CodedInputStream = CodedInputStream.newInstance(bytes)
            val fields: UnknownFieldSet = UnknownFieldSet.parseFrom(inputStream)
            val addrBytes = fields.getField(1).lengthDelimitedList[0].toByteArray()
            val idBytes = fields.getField(2).lengthDelimitedList[0].toByteArray()
            return Pair(Multiaddr(addrBytes), PeerId(idBytes))
        }
    }
}

/**
 * The 'distance' between this [PeerId] and other is the bitwise XOR of the IDs, taken as the number.
 *
 * <p>distance(n₁, n₂) = n₁ XOR n₂
 *
 * <p>LogDistance is reverse of length of common prefix in bits (length - number of leftmost zeros
 * in XOR)
 */

fun PeerId.to(other: PeerId): Int {
    assert(this.bytes.size == other.bytes.size)
    val size = this.bytes.size
    val xorResult = ByteArray(size)
    for (i in 0 until size) {
        xorResult[i] = (this.bytes[i] xor other.bytes[i])
    }
    var logDistance: Int = Byte.SIZE_BITS * xorResult.size // 256
    for (i in xorResult.indices) {
        logDistance -= Byte.SIZE_BITS
        when (xorResult[i].toInt() and 0xff) {
            1 -> logDistance += 1
            in 2..3 -> logDistance += 2
            in 4..7 -> logDistance += 3
            in 8..15 -> logDistance += 4
            in 16..31 -> logDistance += 5
            in 32..63 -> logDistance += 6
            in 64..127 -> logDistance += 7
            in 128..255 -> logDistance += 8
        }
        if (xorResult[i] != 0.toByte()) {
            break
        }
    }
    return logDistance
}

/**
 * Same as [PeerId.to] but reduced to decrease number of possible distances for simulation
 */
fun PeerId.simTo(other: PeerId, distanceDivisor: Int) =
    kotlin.math.ceil(this.to(other) / distanceDivisor.toDouble()).toInt()

/**
 * The 'distance' between this [Node] and other is the bitwise XOR of the [PeerId]s, taken as the number.
 *
 * <p>distance(n₁, n₂) = n₁ XOR n₂
 *
 * <p>LogDistance is reverse of length of common prefix in bits (length - number of leftmost zeros
 * in XOR)
 */
fun Enr.to(other: Enr): Int = this.id.to(other.id)

/**
 * Same as [Enr.to] but reduced to decrease number of possible distances for simulation
 */
fun Enr.simTo(other: Enr, distanceDivisor: Int) = this.id.simTo(other.id, distanceDivisor)
