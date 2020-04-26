package org.ethereum.discv5.core.task.topic

import org.ethereum.discv5.core.Enr
import org.ethereum.discv5.core.Node
import org.ethereum.discv5.core.task.ParallelQueueProducerTask
import org.ethereum.discv5.core.task.ProducerTask
import org.ethereum.discv5.core.task.Task
import org.ethereum.discv5.util.ByteArrayWrapper
import java.util.LinkedList


class TopicAdvertiseTask(
    private val node: Node,
    private val mediaSearchTask: ProducerTask<List<Enr>>,
    private val topicHash: ByteArrayWrapper,
    private val adRetrySteps: Int,
    private val parallelism: Int,
    private val cb: (List<Pair<Enr, Boolean>>) -> Unit
) : Task {
    private lateinit var advertiseTask: ProducerTask<List<Pair<Enr, Boolean>>>
    private var finished = false

    override fun step() {
        if (mediaSearchTask.isOver() && !this::advertiseTask.isInitialized) {
            val tasks =
                mediaSearchTask.getResult().map { AdvertiseOnMediaTask(node, it, topicHash, adRetrySteps) }.toList()
            this.advertiseTask = ParallelQueueProducerTask<Pair<Enr, Boolean>>(LinkedList(tasks), parallelism)
        } else if (!this::advertiseTask.isInitialized) {
            mediaSearchTask.step()
            return
        }
        if (advertiseTask.isOver()) {
            cb(advertiseTask.getResult())
            finished = true
            return
        }
        advertiseTask.step()
    }

    override fun isOver(): Boolean {
        return finished
    }
}
