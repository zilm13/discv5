package org.ethereum.discv5.core

import java.math.BigInteger

interface Message {
    val type: MessageType
    fun getSize(): Int
}

enum class MessageType {
    FINDNODE, NODES, PING, PONG
}

class FindNodeMessage(val buckets: List<Int>) : Message {
    override val type = MessageType.FINDNODE

    /**
     * Estimates FindNode size. Value is taken from implementation and is average
     */
    override fun getSize(): Int {
        return 72 + buckets.size
    }
}

class NodesMessage(val peers: List<Enr>) : Message {
    override val type = MessageType.NODES

    /**
     * Estimates NODES message size. Value is taken from implementation and is average.
     */
    override fun getSize(): Int {
        return 74 + peers.size * 168
    }
}

class PingMessage(val seq: BigInteger) : Message {
    override val type = MessageType.PING

    /**
     * Estimates Ping size. Value is taken from implementation and is average
     */
    override fun getSize(): Int {
        return 76
    }
}

class PongMessage(val seq: BigInteger) : Message {
    override val type = MessageType.PONG

    /**
     * Estimates Pong size. Value is taken from implementation and is average
     */
    override fun getSize(): Int {
        return 80
    }
}
