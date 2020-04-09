package org.ethereum.discv5.core

import java.util.LinkedList
import java.util.Queue

interface Task {
    fun step(): Unit
}

/**
 * Naive implementation without task updates between rounds
 */
class RecursiveTableVisit(private val node: Node, private val peerFn: (Enr) -> Unit) : Task {
    private val peers: Queue<Enr> = LinkedList()

    private fun isRoundOver(): Boolean = peers.isEmpty()

    override fun step() {
        if (isRoundOver()) {
            peers.addAll(node.table.findAll())
        }
        if (peers.isEmpty()) { // XXX: still could be empty
            return
        }
        peerFn(peers.poll())
    }
}

/**
 * Naive implementation without task updates between rounds
 */
class PingTableVisit(private val node: Node) : Task {
    private val peers: Queue<Enr> = LinkedList()

    private fun isRoundOver(): Boolean = peers.isEmpty()

    override fun step() {
        if (isRoundOver()) {
            peers.addAll(node.table.findAll())
        }
        if (peers.isEmpty()) { // XXX: still could be empty
            return
        }
        val current = peers.poll()
        if (!node.ping(current)) {
            node.table.remove(current)
        }
    }
}
