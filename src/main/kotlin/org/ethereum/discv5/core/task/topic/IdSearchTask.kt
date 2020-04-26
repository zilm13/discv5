package org.ethereum.discv5.core.task.topic

import io.libp2p.core.PeerId
import org.ethereum.discv5.core.Enr
import org.ethereum.discv5.core.K_BUCKET
import org.ethereum.discv5.core.KademliaTable
import org.ethereum.discv5.core.Node
import org.ethereum.discv5.core.task.ProducerTask
import org.ethereum.discv5.core.to
import java.util.LinkedList
import java.util.Random

/**
 * Id search task
 * FIXME: what if we need options for a wider radius
 */
class IdSearchTask(
    private val node: Node,
    private val id: PeerId,
    private val start: Enr?,
    private val alpha: Int,
    private val rnd: Random,
    private val candidateReplaceOnFail: () -> Enr
) : ProducerTask<List<Enr>> {
    private var finished = false
    private var replacement: IdSearchTask? = null
    private var firstStepOver = false
    private var firstCbPlaced = false
    private var secondCbPlaced = false
    var candidates = LinkedList<Enr>()
    private val candidateReplacement = ArrayList<List<Enr>>()

    override fun step() {
        if (finished) {
            return
        }
        if (replacement != null) {
            replacement?.step()
            return
        }
        if (start == null) {
            finished = true
            return
        }

        // First step could fail
        if (!firstStepOver) {
            if (!firstCbPlaced) {
                node.findNodes(start, id, this::firstStepCb)
                firstCbPlaced = true
            }
            return
        }

        if (candidateReplacement.size == alpha || (candidates.isEmpty() && !secondCbPlaced)) {
            this.candidates = LinkedList(KademliaTable.filterNeighborhood(id, candidateReplacement.flatten(), K_BUCKET))
            candidateReplacement.clear()
        }
        if (candidateReplacement.size < alpha && candidates.isNotEmpty()) {
            if (!secondCbPlaced) {
                val next = candidates.poll()
                candidates.add(next) // Move it to the end
                node.findNodes(next, id, this::secondStepCb)
                secondCbPlaced = true
            }
        }
    }

    private fun firstStepCb(nodes: List<Enr>) {
        if (nodes.isEmpty()) {
            replacement = IdSearchTask(node, id, candidateReplaceOnFail(), alpha, rnd, candidateReplaceOnFail)
        } else {
            this.candidates.addAll(nodes.shuffled(rnd))
        }
        firstStepOver = true
    }

    private fun secondStepCb(nodes: List<Enr>) {
        if (nodes.isNotEmpty()) {
            candidateReplacement.add(nodes)
        }
        checkIsOver()
        secondCbPlaced = false
    }

    private fun checkIsOver() {
        if (candidateReplacement.size < alpha) {
            return
        }
        val replacement = KademliaTable.filterNeighborhood(id, candidateReplacement.flatten(), 1)
        val current = KademliaTable.filterNeighborhood(id, candidates, 1)
        if (replacement.isEmpty()) {
            finished = true
        } else if (current.isEmpty() || replacement[0].id.to(id) >= current[0].id.to(id)) {
            candidates = LinkedList(candidateReplacement.flatten())
            finished = true
        }
    }

    override fun isOver(): Boolean {
        if (replacement != null) {
            return replacement!!.finished
        }
        return finished
    }

    override fun getResult(): List<Enr> {
        if (!isOver()) {
            error("Task is not over. Query result when the task is over!")
        }

        if (replacement != null) {
            return replacement!!.candidates
        }
        return candidates
    }

    override fun toString(): String {
        return "IdSearchTask[Node=${node.enr.toId()}, id=$id, start=${start?.toId() ?: "null"}]"
    }
}