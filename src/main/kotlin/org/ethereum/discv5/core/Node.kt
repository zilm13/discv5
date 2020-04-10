package org.ethereum.discv5.core

import io.libp2p.core.crypto.PrivKey
import org.ethereum.discv5.util.MessageSizeEstimator
import java.util.Random
import kotlin.math.roundToInt
import kotlin.streams.toList

val K_BUCKET = 16
val BUCKETS_COUNT = 256
val DISTANCE_DIVISOR = 1 // Reduces number of possible distances with numbers greater than 1
val CHANCE_TO_FORGET = 0.2
val NEIGHBORS_DISTANCES_LIMIT = 3

data class Node(val enr: Enr, val privKey: PrivKey, val rnd: Random, val router: Router) {
    val tasks: MutableList<Task> = ArrayList()
    val table = KademliaTable(
        enr,
        K_BUCKET,
        BUCKETS_COUNT,
        DISTANCE_DIVISOR,
        { ping(it) },
        listOf()
    )
    val outgoingMessages: MutableList<Int> = ArrayList()
    val incomingMessages: MutableList<Int> = ArrayList()

    fun resetAll() {
        tasks.clear()
        outgoingMessages.clear()
        incomingMessages.clear()
    }

    /**
     * @return peers surrounding [other] using [KademliaTable.findStrict]
     * to match Discovery V5 spec.
     */
    internal fun findNodesStrict(other: Enr): List<Enr> {
        val startBucket = other.simTo(enr, DISTANCE_DIVISOR)
        var currentRadius = 0
        val buckets = LinkedHashSet<Int>()
        while (buckets.size < NEIGHBORS_DISTANCES_LIMIT) {
            val topBucket = startBucket + currentRadius
            val bottomBucket = startBucket - currentRadius
            if (topBucket <= BUCKETS_COUNT) {
                buckets.add(topBucket)
                if (buckets.size == NEIGHBORS_DISTANCES_LIMIT) {
                    break
                }
            }
            if (bottomBucket in 1 until topBucket) {
                buckets.add(bottomBucket)
            }
            currentRadius++
        }

        // TODO: update message size estimator
        outgoingMessages.add(MessageSizeEstimator.getFindNodesSize())
        // TODO: make router forward messages between instances and put message measurement in it
        return router.route(other)?.handleFindNodesStrict(buckets.toList(), enr)?.also {
            incomingMessages.addAll(MessageSizeEstimator.getNodesSize(it.size))
        } ?: emptyList()
    }

    private fun handleFindNodesStrict(buckets: List<Int>, initiator: Enr): List<Enr> {
        return table.findStrict(buckets).stream().limit(K_BUCKET.toLong()).toList().also {
            incomingMessages.add(MessageSizeEstimator.getFindNodesSize())
            outgoingMessages.addAll(MessageSizeEstimator.getNodesSize(it.size))
            table.put(initiator, this::ping)
        }
    }

    fun initTasks(): Unit {
        if (tasks.isNotEmpty()) error("Already initialized")
        tasks.let {
            it.add(RecursiveTableVisit(this) { enr ->
                findNodesStrict(enr).forEach { enrFound -> this.table.put(enrFound, this::ping) }
            })
            it.add(PingTableVisit(this))
        }
    }

    fun step(): Unit {
        tasks.forEach { it -> it.step() }
    }

    /**
     * It would be more realistic if the chance of dropping peer will decrease with
     * each check (peer which is online for 2 hours will be more likely online in an hour
     * than the peer which is online for 1 hour) but such simulation could be very
     * complex and set aside for further improvements of simulation.
     *
     * TODO:
     * 1. traffic
     * 2. enr update payload
     * 3. real down nodes
     * 4. network loss
     * */
    fun ping(other: Enr): Boolean {
        return rnd.nextInt((1 / CHANCE_TO_FORGET).roundToInt()) != 0
    }
}