package org.ethereum.discv5.core

import io.libp2p.core.PeerId

class Router {
    private val idToNode: MutableMap<PeerId, Node> = HashMap()

    fun register(node: Node) {
        idToNode.putIfAbsent(node.enr.id, node)
    }

    /**
     * TODO: real down nodes and network losses
     */
    fun route(from: Node, to: Enr, message: Message): List<Message> {
        from.outgoingMessages.add(message.getSize())
        from.roundtripLatency.add(listOf(Unit))
        val toNode = idToNode[to.id]
        if (toNode == null) {
            // TODO: log
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
