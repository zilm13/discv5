package org.ethereum.discv5.core.task.topic

import io.libp2p.core.PeerId
import org.ethereum.discv5.core.Enr
import org.ethereum.discv5.core.KademliaTable
import org.ethereum.discv5.core.Node
import org.ethereum.discv5.core.task.ParallelProducerTask
import org.ethereum.discv5.core.task.ProducerTask
import java.util.Random

class ParallelIdSearchTask(
    private val node: Node,
    private val id: PeerId,
    private val startFn: () -> Enr,
    private val parallelism: Int,
    private val radius: Int,
    private val rnd: Random
) : ProducerTask<List<Enr>> {
    private val delegate: ParallelProducerTask<List<Enr>>

    init {
        val tasks = (0 until parallelism).map {
            startFn()
        }.map {
            IdSearchTask(node, id, it, parallelism, rnd, startFn)
        }.toList()
        this.delegate = ParallelProducerTask(tasks)
    }

    override fun step() {
        delegate.step()
    }

    override fun isOver(): Boolean {
        return delegate.isOver()
    }

    override fun getResult(): List<Enr> {
        return KademliaTable.filterNeighborhood(id, delegate.getResult().flatten(), radius)
    }
}