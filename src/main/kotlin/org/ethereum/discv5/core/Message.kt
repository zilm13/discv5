package org.ethereum.discv5.core

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
     * Estimates FindNode size. Value is taken from implementation
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