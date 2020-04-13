package org.ethereum.discv5.core

import java.util.LinkedList
import java.util.Queue

interface Task {
    fun step(): Unit
    fun isOver(): Boolean
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

    override fun isOver(): Boolean {
        return false
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

    override fun isOver(): Boolean {
        return false
    }
}

/**
 * Any one step task
 */
class OneStepTask(private val fn: () -> Unit) : Task {
    private var done: Boolean = false

    override fun step() {
        if (done) {
            return
        }

        fn()
        this.done = true
    }

    override fun isOver(): Boolean {
        return done
    }
}
