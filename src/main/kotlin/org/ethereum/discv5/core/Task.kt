package org.ethereum.discv5.core

import java.util.LinkedList
import java.util.Queue

interface Task {
    fun step(): Unit
    fun isOver(): Boolean {
        return false
    }
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

/**
 * Node update task
 */
class NodeUpdateTask(private val enr: Enr, private val node: Node) : Task {
    private var done: Boolean = false

    override fun step() {
        if (done) {
            return
        }

        node.updateNode(enr)
        this.done = true
    }

    override fun isOver(): Boolean {
        return done
    }

    override fun toString(): String {
        return "NodeUpdateTask[${enr.toId()}]"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NodeUpdateTask

        if (enr.id != other.enr.id) return false
        if (node != other.node) return false
        if (done != other.done) return false

        return true
    }

    override fun hashCode(): Int {
        var result = enr.id.hashCode()
        result = 31 * result + node.hashCode()
        result = 31 * result + done.hashCode()
        return result
    }
}
