package org.ethereum.discv5.core.task.topic

import io.libp2p.core.PeerId
import org.ethereum.discv5.core.Enr
import org.ethereum.discv5.core.Node
import org.ethereum.discv5.core.NodesMessage
import org.ethereum.discv5.core.TopicQuery
import org.ethereum.discv5.core.task.ProducerTask
import org.ethereum.discv5.core.task.regular.MessageRoundTripTask
import org.ethereum.discv5.util.ByteArrayWrapper

class FindAdOnMediaTask(
    private val node: Node,
    private val media: Enr,
    private val topicHash: ByteArrayWrapper
) : ProducerTask<Set<PeerId>> {
    private var finished = false
    private lateinit var result: Set<PeerId>
    private var waitingForTask = false

    override fun step() {
        if (finished || waitingForTask) {
            return
        }
        placeTask()
    }

    private fun placeTask() {
        waitingForTask = true
        node.tasks.add(MessageRoundTripTask(node, media, TopicQuery(topicHash)) {
            node.handle(it, media)
            it.filterIsInstance<NodesMessage>().toList().run { handleAnswer(this, media) }
        })
    }

    private fun handleAnswer(messages: List<NodesMessage>, sender: Enr) {
        result = messages.map { it.peers }.flatten().map { it.id }.toSet()
        finished = true
        waitingForTask = false
    }

    override fun isOver(): Boolean {
        return finished
    }

    override fun getResult(): Set<PeerId> {
        if (!isOver()) {
            error("Task is not over. Query result when the task is over!")
        }

        return result
    }
}