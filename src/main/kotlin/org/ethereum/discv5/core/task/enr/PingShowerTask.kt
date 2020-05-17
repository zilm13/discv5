package org.ethereum.discv5.core.task.enr

import org.ethereum.discv5.core.Node
import org.ethereum.discv5.core.task.Task

/**
 * Sends ping to all peers in node table without queueing
 */
class PingShowerTask(private val node: Node) : Task {
    private var finished = false

    override fun isOver(): Boolean {
        return finished
    }

    override fun step() {
        node.table.findAll().forEach {
            node.ping(it) { alive ->
                if (!alive) {
                    node.table.remove(it)
                }
            }
        }
        finished = true
    }
}