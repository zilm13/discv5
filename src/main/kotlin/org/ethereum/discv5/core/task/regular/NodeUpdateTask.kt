package org.ethereum.discv5.core.task.regular

import org.ethereum.discv5.core.Enr
import org.ethereum.discv5.core.Node
import org.ethereum.discv5.core.task.Task

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