package org.ethereum.discv5.core.task.regular

import org.ethereum.discv5.core.Enr
import org.ethereum.discv5.core.Message
import org.ethereum.discv5.core.Node
import org.ethereum.discv5.core.task.Task

/**
 * Two steps message task:
 * 1) message is delivered from node to recipient
 * 2) message is handled on recipient side and result is returned back
 */
class MessageRoundTripTask(
    private val node: Node,
    private val recipient: Enr,
    private val message: Message,
    private val cb: (List<Message>) -> Unit
) : Task {
    private var deliveryDone = false
    private var isOver = false

    override fun step() {
        if (!deliveryDone) {
            deliveryDone = true
            return
        }
        val result = node.router.route(node, recipient, message)
        cb(result)
        isOver = true
    }

    override fun isOver(): Boolean {
        return isOver
    }
}