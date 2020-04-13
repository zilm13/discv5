package org.ethereum.discv5.core

class Router {
    private val enrToNode: MutableMap<Enr, Node> = HashMap()

    fun register(node: Node) {
        enrToNode.putIfAbsent(node.enr, node)
    }

    /**
     * TODO: real down nodes and network losses
     */
    fun route(from: Node, to: Enr, message: Message): List<Message> {
        from.outgoingMessages.add(message.getSize())
        from.roundtripLatency.add(listOf(Unit))
        val toNode = enrToNode.get(to)
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
