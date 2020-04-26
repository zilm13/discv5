package org.ethereum.discv5.core.task.enr

import io.libp2p.core.PeerId
import org.ethereum.discv5.core.BUCKETS_COUNT
import org.ethereum.discv5.core.Enr
import org.ethereum.discv5.core.FindNodeMessage
import org.ethereum.discv5.core.K_BUCKET
import org.ethereum.discv5.core.Node
import org.ethereum.discv5.core.NodesMessage
import org.ethereum.discv5.core.task.ProducerTask
import org.ethereum.discv5.core.task.regular.MessageRoundTripTask
import org.ethereum.discv5.util.ByteArrayWrapper

class FindEnrsOnPeerTask(
    private val node: Node,
    private val media: Enr,
    private val enrKey: ByteArrayWrapper,
    private val enrValue: ByteArrayWrapper
) : ProducerTask<Set<PeerId>> {
    private var finished = false
    private var result = HashSet<PeerId>()
    private var waitingForTask = false
    private var currentDistance = BUCKETS_COUNT

    override fun step() {
        if (finished || waitingForTask) {
            return
        }
        placeTask()
    }

    private fun placeTask() {
        waitingForTask = true
        node.tasks.add(
            MessageRoundTripTask(
                node,
                media,
                FindNodeMessage(listOf(currentDistance, currentDistance - 1, currentDistance - 2))
            ) {
                node.handle(it, media)
                it.filterIsInstance<NodesMessage>().toList().run { handleAnswer(this) }
            })
    }

    private fun handleAnswer(messages: List<NodesMessage>) {
        val peers = messages.map { it.peers }.flatten()
        peers.filter { enr -> enr.meta[enrKey] == enrValue }.forEach { result.add(it.id) }
        if (peers.size < K_BUCKET) {
            finished = true
        }
        currentDistance -= 1
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