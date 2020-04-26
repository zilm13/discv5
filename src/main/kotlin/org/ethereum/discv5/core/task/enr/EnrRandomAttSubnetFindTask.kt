package org.ethereum.discv5.core.task.enr

import io.libp2p.core.PeerId
import org.ethereum.discv5.core.Enr
import org.ethereum.discv5.core.MetaKey
import org.ethereum.discv5.core.Node
import org.ethereum.discv5.core.task.ProducerTask
import org.ethereum.discv5.core.task.Task
import org.ethereum.discv5.util.ByteArrayWrapper
import org.ethereum.discv5.util.StatefulSequence
import java.util.Queue

/**
 * Uses experimental FindNodeByAttributeMessage
 */
class EnrRandomAttSubnetFindTask(
    private val node: Node,
    peers: List<Enr>,
    private val subnet: ByteArrayWrapper,
    adCount: Int,
    parallelism: Int,
    private val cb: (Set<PeerId>) -> Unit
) : Task {
    private val enrSearchTask: ParallelEnrSearchTask
    private var finished = false

    init {
        val tasks = StatefulSequence<FindAttEnrsOnPeerTask>(
            peers.asSequence().map { FindAttEnrsOnPeerTask(node, it, MetaKey.SUBNET.of, subnet) })
        enrSearchTask = ParallelEnrSearchTask(tasks as Queue<ProducerTask<Set<PeerId>>>, adCount, parallelism)
    }

    override fun step() {
        if (enrSearchTask.isOver()) {
            cb(enrSearchTask.getResult())
            finished = true
            return
        }
        enrSearchTask.step()
    }

    override fun isOver(): Boolean {
        return finished
    }
}