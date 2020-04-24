package org.ethereum.discv5.core

import org.ethereum.discv5.util.ByteArrayWrapper
import java.math.BigInteger

interface Message {
    val type: MessageType
    fun getSize(): Int
}

enum class MessageType {
    FINDNODE, NODES, PING, PONG, REGTOPIC, TICKET, REGCONFIRMATION, TOPICQUERY
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

class RegTopicMessage(val topic: ByteArrayWrapper, val enr: Enr, val ticket: ByteArray) : Message {
    override val type = MessageType.REGTOPIC

    /**
     * Estimates RegTopic size. Value is taken from implementation and is average
     */
    override fun getSize(): Int {
        return 72 + 168 + topic.size + ticket.size
    }
}

class TicketMessage(val ticket: ByteArray, val waitSteps: Int) : Message {
    override val type = MessageType.TICKET

    /**
     * Estimates Ticket size. Value is taken from implementation and is average
     */
    override fun getSize(): Int {
        return 76 + ticket.size
    }

    companion object {
        fun getAverageTicketSize() = 100
    }
}

class RegConfirmation(val topic: ByteArrayWrapper) : Message {
    override val type = MessageType.REGCONFIRMATION

    /**
     * Estimates RegConfirmation size. Value is taken from implementation and is average
     */
    override fun getSize(): Int {
        return 72 + topic.size
    }
}

class TopicQuery(val topic: ByteArrayWrapper) : Message {
    override val type = MessageType.TOPICQUERY

    /**
     * Estimates TopicQuery size. Value is taken from implementation and is average
     */
    override fun getSize(): Int {
        return 72 + topic.size
    }
}