package org.ethereum.discv5.core.task.regular

import org.ethereum.discv5.core.Enr
import org.ethereum.discv5.core.Node
import org.ethereum.discv5.core.task.Task
import java.util.LinkedList
import java.util.Queue

/**
 * Naive implementation without task updates between rounds
 */
class PingTableVisit(private val node: Node, private val parallelism: Int = 1) : Task {
    private val peers: Queue<Enr> = LinkedList()

    private fun isRoundOver(): Boolean = peers.isEmpty()

    override fun step() {
        for (i in 0 until parallelism) {
            if (isRoundOver()) {
                peers.addAll(node.table.findAll())
            }
            if (peers.isEmpty()) { // XXX: still could be empty
                return
            }
            val current = peers.poll()
            node.ping(current) {
                if (!it) {
                    node.table.remove(current)
                }
            }
        }
    }
}