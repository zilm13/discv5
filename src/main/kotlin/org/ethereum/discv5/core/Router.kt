package org.ethereum.discv5.core

class Router {
    private val enrToNode: MutableMap<Enr, Node> = HashMap()

    fun register(node: Node) {
        enrToNode.putIfAbsent(node.enr, node)
    }

    fun route(enr: Enr): Node? {
        return enrToNode.get(enr)
    }
}