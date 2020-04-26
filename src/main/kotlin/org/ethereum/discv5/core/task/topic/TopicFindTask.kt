package org.ethereum.discv5.core.task.topic

import io.libp2p.core.PeerId
import org.ethereum.discv5.core.Enr
import org.ethereum.discv5.core.Node
import org.ethereum.discv5.core.task.ProducerTask
import org.ethereum.discv5.core.task.Task
import org.ethereum.discv5.util.ByteArrayWrapper
import java.util.LinkedList

class TopicFindTask(
    private val node: Node,
    private val mediaSearchTask: ProducerTask<List<Enr>>,
    private val topicHash: ByteArrayWrapper,
    private val adCount: Int,
    private val parallelism: Int,
    private val cb: (Set<PeerId>) -> Unit
) : Task {
    private lateinit var adSearchTask: ProducerTask<Set<PeerId>>
    private var finished = false

    override fun step() {
        if (mediaSearchTask.isOver() && !this::adSearchTask.isInitialized) {
            val tasks =
                mediaSearchTask.getResult().map { FindAdOnMediaTask(node, it, topicHash) }.toList()
            this.adSearchTask = ParallelAdSearchTask(LinkedList(tasks), adCount, parallelism)
        } else if (!this::adSearchTask.isInitialized) {
            mediaSearchTask.step()
            return
        }
        if (adSearchTask.isOver()) {
            cb(adSearchTask.getResult())
            finished = true
            return
        }
        adSearchTask.step()
    }

    override fun isOver(): Boolean {
        return finished
    }
}