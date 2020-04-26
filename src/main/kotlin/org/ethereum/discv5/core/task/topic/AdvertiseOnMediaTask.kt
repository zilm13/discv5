package org.ethereum.discv5.core.task.topic

import org.ethereum.discv5.core.Enr
import org.ethereum.discv5.core.Node
import org.ethereum.discv5.core.RegTopicMessage
import org.ethereum.discv5.core.TicketMessage
import org.ethereum.discv5.core.task.ProducerTask
import org.ethereum.discv5.core.task.regular.MessageRoundTripTask
import org.ethereum.discv5.util.ByteArrayWrapper

class AdvertiseOnMediaTask(
    private val node: Node,
    private val media: Enr,
    private val topicHash: ByteArrayWrapper,
    private val adRetrySteps: Int
) : ProducerTask<Pair<Enr, Boolean>> {
    private var finished = false
    private var needRetryIn: Int = 0
    private lateinit var result: Pair<Enr, Boolean>
    private var retrying = false
    private var waitingForTask = false

    override fun step() {
        if (finished || waitingForTask) {
            return
        }
        if (needRetryIn > 0) {
            needRetryIn--
            return
        }
        placeTask()
    }

    private fun placeTask() {
        waitingForTask = true
        val ticket: ByteArray = if (retrying) {
            ByteArray(TicketMessage.getAverageTicketSize())
        } else {
            ByteArray(1)
        }
        node.tasks.add(MessageRoundTripTask(node, media, RegTopicMessage(topicHash, node.enr, ticket)) {
            node.handle(it, media)
            it.filterIsInstance<TicketMessage>().toList().run { handleAnswer(this, media) }
        })
    }

    private fun handleAnswer(messages: List<TicketMessage>, sender: Enr) {
        if (messages.isEmpty()) {
            placeTask()
            return
        }
        val message = messages[0]
        when {
            message.waitSteps == 0 -> {
                finished = true
                result = Pair(sender, true)
            }
            // TODO: we could do other tasks in this period
            message.waitSteps <= adRetrySteps -> {
                needRetryIn = message.waitSteps
                retrying = true
            }
            else -> {
                finished = true
                result = Pair(sender, false)
            }
        }
        waitingForTask = false
    }

    override fun isOver(): Boolean {
        return finished
    }

    override fun getResult(): Pair<Enr, Boolean> {
        if (!isOver()) {
            error("Task is not over. Query result when the task is over!")
        }

        return result
    }
}