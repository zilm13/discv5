package org.ethereum.discv5.core.task.topic

import io.libp2p.core.PeerId
import org.ethereum.discv5.core.task.ProducerTask
import java.util.Queue

class ParallelAdSearchTask(
    private val subTasks: Queue<ProducerTask<Set<PeerId>>>,
    private val requireAds: Int,
    private val parallelism: Int
) :
    ProducerTask<Set<PeerId>> {
    private val result = HashSet<PeerId>()
    private val current = HashSet<ProducerTask<Set<PeerId>>>()

    override fun step() {
        if (isOver()) {
            return
        }
        while (current.size < parallelism && subTasks.isNotEmpty()) {
            current.add(subTasks.poll())
        }
        current.forEach { it.step() }
        current.removeAll {
            val toRemove = it.isOver()
            if (toRemove) {
                result.addAll(it.getResult())
            }
            toRemove
        }
    }

    override fun isOver(): Boolean {
        return result.size >= requireAds || (subTasks.isEmpty() && current.isEmpty())
    }


    override fun getResult(): Set<PeerId> {
        if (!isOver()) {
            error("Task is not over. Query result when the task is over!")
        }

        return result
    }
}