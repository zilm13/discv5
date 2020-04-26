package org.ethereum.discv5.core

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.UnknownFieldSet
import io.libp2p.core.PeerId
import io.libp2p.core.multiformats.Multiaddr
import org.ethereum.discv5.util.ByteArrayWrapper
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicInteger
import kotlin.experimental.xor

val DEFAULT_SEQ: BigInteger = BigInteger.ONE
val DEFAULT_META: Map<ByteArrayWrapper, ByteArrayWrapper> = HashMap()

enum class MetaKey(val of: ByteArrayWrapper) {
    SUBNET(ByteArrayWrapper(ByteArray(1) { 1.toByte() }))
}

/**
 * Ethereum Node Record
 * Doesn't comply with https://eips.ethereum.org/EIPS/eip-778 but contains all fields required for simulation
 */
data class Enr(
    val addr: Multiaddr,
    val id: PeerId,
    val seq: BigInteger,
    val meta: Map<ByteArrayWrapper, ByteArrayWrapper>
) {
    private constructor(quartet: Quartet<Multiaddr, PeerId, BigInteger, Map<ByteArrayWrapper, ByteArrayWrapper>>) :
            this(quartet.first, quartet.second, quartet.third, quartet.forth)

    constructor(addr: Multiaddr, id: PeerId) : this(addr, id, DEFAULT_SEQ, DEFAULT_META)

    constructor(bytes: ByteArray) : this(fromBytes(bytes))

    data class Quartet<T1, T2, T3, T4>(val first: T1, val second: T2, val third: T3, val forth: T4)

    fun getBytes(): ByteArray {
        val baos = ByteArrayOutputStream()
        val out: CodedOutputStream = CodedOutputStream.newInstance(baos)
        out.writeByteArray(1, addr.getBytes())
        out.writeByteArray(2, id.bytes)
        out.writeByteArray(3, seq.toByteArray())
        val mapBaos = ByteArrayOutputStream()
        val mapOut: CodedOutputStream = CodedOutputStream.newInstance(mapBaos)
        val counter = AtomicInteger(1)
        meta.forEach { (key, value) ->
            run {
                mapOut.writeByteArray(counter.getAndIncrement(), key.bytes)
                mapOut.writeByteArray(counter.getAndIncrement(), value.bytes)
            }
        }
        mapOut.flush()
        out.writeByteArray(4, mapBaos.toByteArray())
        out.flush()
        return baos.toByteArray()
    }

    fun toId(): String {
        return "#${id.toHex().substring(0, 6)}"
    }

    companion object {
        private fun fromBytes(bytes: ByteArray): Quartet<Multiaddr, PeerId, BigInteger, Map<ByteArrayWrapper, ByteArrayWrapper>> {
            val inputStream: CodedInputStream = CodedInputStream.newInstance(bytes)
            val fields: UnknownFieldSet = UnknownFieldSet.parseFrom(inputStream)
            val addrBytes = fields.getField(1).lengthDelimitedList[0].toByteArray()
            val idBytes = fields.getField(2).lengthDelimitedList[0].toByteArray()
            val seqBytes = fields.getField(3).lengthDelimitedList[0].toByteArray()
            val inputMetaStream: CodedInputStream =
                CodedInputStream.newInstance(fields.getField(4).lengthDelimitedList[0].toByteArray())
            val metaFields: UnknownFieldSet = UnknownFieldSet.parseFrom(inputMetaStream)
            val counter = AtomicInteger(1)
            val metaMap = HashMap<ByteArrayWrapper, ByteArrayWrapper>()
            while (metaFields.hasField(counter.get())) {
                val keyBytes = fields.getField(counter.getAndIncrement()).lengthDelimitedList[0].toByteArray()
                val valueBytes = fields.getField(counter.getAndIncrement()).lengthDelimitedList[0].toByteArray()
                metaMap[ByteArrayWrapper(keyBytes)] = ByteArrayWrapper(valueBytes)
            }

            return Quartet(Multiaddr(addrBytes), PeerId(idBytes), BigInteger(1, seqBytes), metaMap)
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
 * The 'distance' between this [Node] and other is the bitwise XOR of the [PeerId]s, taken as the number.
 *
 * <p>distance(n₁, n₂) = n₁ XOR n₂
 *
 * <p>LogDistance is reverse of length of common prefix in bits (length - number of leftmost zeros
 * in XOR)
 */
fun Enr.to(other: Enr): Int = this.id.to(other.id)
