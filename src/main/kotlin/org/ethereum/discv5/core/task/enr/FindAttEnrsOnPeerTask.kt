package org.ethereum.discv5.core.task.enr

import io.libp2p.core.PeerId
import org.ethereum.discv5.core.Enr
import org.ethereum.discv5.core.FindNodeByAttributeMessage
import org.ethereum.discv5.core.Node
import org.ethereum.discv5.core.NodesMessage
import org.ethereum.discv5.core.task.ProducerTask
import org.ethereum.discv5.core.task.regular.MessageRoundTripTask
import org.ethereum.discv5.util.ByteArrayWrapper

/**
 * Uses experimental FindNodeByAttributeMessage
 */
class FindAttEnrsOnPeerTask(
    private val node: Node,
    private val media: Enr,
    private val enrKey: ByteArrayWrapper,
    private val enrValue: ByteArrayWrapper
) : ProducerTask<Set<PeerId>> {
    private var finished = false
    private var result = HashSet<PeerId>()
    private var waitingForTask = false

    override fun step() {
        if (finished || waitingForTask) {
            return
        }
        placeTask()
    }

    private fun placeTask() {
        waitingForTask = true
        node.tasks.add(MessageRoundTripTask(node, media, FindNodeByAttributeMessage(Pair(enrKey, enrValue))) {
            node.handle(it, media)
            it.filterIsInstance<NodesMessage>().toList().run { handleAnswer(this) }
        })
    }

    private fun handleAnswer(messages: List<NodesMessage>) {
        val peers = messages.map { it.peers }.flatten()
        peers.filter { enr -> enr.meta[enrKey] == enrValue }.forEach { result.add(it.id) }
        finished = true
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