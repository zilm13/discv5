package org.ethereum.discv5.core

import io.libp2p.core.PeerId
import org.apache.logging.log4j.LogManager

class Router {
    private val idToNode: MutableMap<PeerId, Node> = HashMap()
    private val logger = LogManager.getLogger("Router")

    fun register(node: Node) {
        idToNode.putIfAbsent(node.enr.id, node)
    }

    fun resolve(peerId: PeerId): Node? {
        return idToNode[peerId]
    }

    fun resolve(enr: Enr): Node? {
        return resolve(enr.id)
    }

    /**
     * TODO: real down nodes and network losses
     */
    fun route(from: Node, to: Enr, message: Message): List<Message> {
        from.outgoingMessages.add(message.getSize())
        from.roundtripLatency.add(listOf(Unit))
        val toNode = resolve(to)
        if (toNode == null) {
            logger.debug("Failed to route $message from Node${from.enr.toId()} to Node${to.toId()}, recipient not found")
            return emptyList()
        }
        toNode.incomingMessages.add(message.getSize())
        return toNode.handle(message, from.enr).also { messages ->
            from.roundtripLatency.add(messages.map { Unit }.toList())
            messages.forEach {
                toNode.outgoingMessages.add(it.getSize())
                from.incomingMessages.add(it.getSize())
            }
        }
    }
}
