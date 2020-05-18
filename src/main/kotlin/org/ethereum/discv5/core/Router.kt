package org.ethereum.discv5.core

import io.libp2p.core.PeerId
import org.apache.logging.log4j.LogManager
import java.util.Random

class Router(private val rnd: Random) {
    private val idToNode: MutableMap<PeerId, Node> = HashMap()
    private val logger = LogManager.getLogger("Router")
    var churnPcts = 0
        set(value) {
            if (value > 100 || value < 0) error("Value should be from 0 to 100 percents")
            field = value
        }

    fun register(node: Node) {
        idToNode.putIfAbsent(node.enr.id, node)
    }

    fun resolve(peerId: PeerId): Node? {
        return idToNode[peerId]
    }

    fun resolve(enr: Enr): Node? {
        return resolve(enr.id)
    }

    fun route(from: Node, to: Enr, message: Message): List<Message> {
        from.outgoingMessages.add(message.getSize().toLong())
        from.roundtripLatency.add(listOf(Unit))
        val toNode = resolve(to)
        if (toNode == null) {
            logger.debug("Failed to route $message from Node${from.enr.toId()} to Node${to.toId()}, recipient not found")
            return emptyList()
        }
        // Apply churn rate
        if (churnPcts > 0) {
            if (rnd.nextInt(100) < churnPcts) {
                return emptyList()
            }
        }
        toNode.incomingMessages.add(message.getSize().toLong())
        return toNode.handle(message, from.enr).also { messages ->
            from.roundtripLatency.add(messages.map { Unit }.toList())
            messages.forEach {
                toNode.outgoingMessages.add(it.getSize().toLong())
                from.incomingMessages.add(it.getSize().toLong())
            }
        }
    }
}
