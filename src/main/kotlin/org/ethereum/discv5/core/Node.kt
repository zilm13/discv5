package org.ethereum.discv5.core

import io.libp2p.core.PeerId
import io.libp2p.core.crypto.PrivKey
import io.libp2p.core.multiformats.Multiaddr
import org.ethereum.discv5.util.MessageSizeEstimator
import java.util.Random
import kotlin.math.roundToInt

val K_BUCKET = 16
val BUCKETS_COUNT = 256
val DISTANCE_DIVISOR = 1 // Reduces number of possible distances with numbers greater than 1
val CHANCE_TO_FORGET = 0.2


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
        val startNode = Enr(Multiaddr(ByteArray(0)), enr.id)
        val startBucket = other.simTo(enr, DISTANCE_DIVISOR)
        var currentRadius = 0
        val res: MutableList<Enr> = ArrayList()
        while (res.size < K_BUCKET) {
            val candidates: MutableSet<Enr> = HashSet()
            val topBucket = startBucket + currentRadius
            val bottomBucket = startBucket - currentRadius
            if (topBucket <= BUCKETS_COUNT) {
                outgoingMessages.add(MessageSizeEstimator.getFindNodesSize())
                val topBucketNodes = router.route(other)?.handleFindNodesStrict(topBucket)?.also {
                    incomingMessages.addAll(MessageSizeEstimator.getNodesSize(it.size))
                } ?: emptyList()
                incomingMessages.addAll(MessageSizeEstimator.getNodesSize(topBucketNodes.size))
                topBucketNodes.forEach { candidates.add(it) }
            }
            if (bottomBucket in 1 until topBucket) {
                outgoingMessages.add(MessageSizeEstimator.getFindNodesSize())
                val bottomBucketNodes = router.route(other)?.handleFindNodesStrict(bottomBucket)?.also {
                    incomingMessages.addAll(MessageSizeEstimator.getNodesSize(it.size))
                } ?: emptyList()
                incomingMessages.addAll(MessageSizeEstimator.getNodesSize(bottomBucketNodes.size))
                bottomBucketNodes.forEach { candidates.add(it) }
            }
            // Have not executed any of 2 above ifs
            if (topBucket > BUCKETS_COUNT && bottomBucket < 1) {
                break
            }
            KademliaTable.filterNeighborhood(startNode, res, candidates, K_BUCKET, DISTANCE_DIVISOR)
            currentRadius++
        }

        return res
    }

    /**
     * TODO:
     * 1. update to proposed version with several buckets input
     * 2. remove all old handlers
     * 3. update initiator status in all handlers
     */
    private fun handleFindNodesStrict(bucket: Int): List<Enr> {
        return table.findStrict(bucket).also {
            incomingMessages.add(MessageSizeEstimator.getFindNodesSize())
            outgoingMessages.addAll(MessageSizeEstimator.getNodesSize(it.size))
        }
    }

    /**
     * @return peers surrounding [other] using [KademliaTable.findDown]. Experimental.
     */
    internal fun findNodesDown(other: Enr): List<Enr> {
        val startNode = Enr(Multiaddr(ByteArray(0)), enr.id)
        val startBucket = other.simTo(enr, DISTANCE_DIVISOR)
        var currentRadius = 0
        val res: MutableList<Enr> = ArrayList()
        val fullBucketsCache: MutableMap<Int, List<Enr>> = HashMap()
        while (res.size < K_BUCKET) {
            val candidates: MutableSet<Enr> = HashSet()
            val topBucket = startBucket + currentRadius
            val bottomBucket = startBucket - currentRadius
            if (bottomBucket > 0) {
                if (!fullBucketsCache.containsKey(bottomBucket)) {
                    outgoingMessages.add(MessageSizeEstimator.getFindNodesDownSize())
                    val enrs = router.route(other)?.handleFindNodesDown(bottomBucket)?.also {
                        incomingMessages.addAll(MessageSizeEstimator.getNodesSize(it.size))
                    } ?: emptyList()
                    incomingMessages.addAll(MessageSizeEstimator.getNodesSize(enrs.size))
                    if (enrs.isNotEmpty()) {
                        val lastDistance = enrs.last().simTo(other, DISTANCE_DIVISOR)
                        val lastBucketNotFull = enrs.size == K_BUCKET && lastDistance != bottomBucket
                        var enrsWDistance = enrs.map { Pair(it.simTo(other, DISTANCE_DIVISOR), it) }
                        if (lastBucketNotFull) {
                            enrsWDistance = enrsWDistance.filter { it.first != lastDistance }
                        } else if (enrs.size < K_BUCKET) { // so we are over with nodes in table, fill everything else with 0's
                            (1 until lastDistance).forEach { fullBucketsCache[it] = listOf() }
                        }
                        enrsWDistance.groupBy { it.first }.forEach { bucket, pairsList ->
                            fullBucketsCache[bucket] =
                                pairsList.map { it.second }
                        }
                    } else {
                        (1..bottomBucket).forEach { fullBucketsCache[it] = listOf() }
                    }
                }
                fullBucketsCache[bottomBucket]?.forEach { candidates.add(it) }
                fullBucketsCache.remove(bottomBucket)
            }
            if (topBucket in BUCKETS_COUNT downTo (bottomBucket + 1)) { // skip when topBucket == bottomBucket
                outgoingMessages.add(MessageSizeEstimator.getFindNodesDownSize())
                val topBucketNodes = router.route(other)?.handleFindNodesDown(topBucket)?.also {
                    incomingMessages.addAll(MessageSizeEstimator.getNodesSize(it.size))
                } ?: emptyList()
                incomingMessages.addAll(MessageSizeEstimator.getNodesSize(topBucketNodes.size))
                topBucketNodes.filter { other.simTo(it, DISTANCE_DIVISOR) == topBucket }
                    .forEach { candidates.add(it) }
            }
            // Have not executed any of 2 above ifs
            if (topBucket > BUCKETS_COUNT && bottomBucket < 1) {
                break
            }
            KademliaTable.filterNeighborhood(startNode, res, candidates, K_BUCKET, DISTANCE_DIVISOR)
            currentRadius++
        }

        return res
    }

    private fun handleFindNodesDown(bucket: Int): List<Enr> {
        return table.findDown(bucket).also {
            incomingMessages.add(MessageSizeEstimator.getFindNodesDownSize())
            outgoingMessages.addAll(MessageSizeEstimator.getNodesSize(it.size))
        }
    }

    fun initTasks(findFn: (Enr) -> List<Enr>): Unit {
        if (tasks.isNotEmpty()) error("Already initialized")
        tasks.let {
            it.add(RecursiveTableVisit(this) { enr ->
                findFn(enr).forEach { enrFound -> this.table.put(enrFound, this::ping) }
            })
            it.add(PingTableVisit(this))
        }
    }

    fun step(): Unit {
        tasks.forEach { it -> it.step() }
    }

    /**
     * @return peers surrounding [other] like in original Kademlia
     * find neighbors implementation
     */
    internal fun findNeighbors(other: Enr): List<Enr> {
        outgoingMessages.add(MessageSizeEstimator.getNeighborsSize())
        return router.route(other)?.handleFindNeighbors(enr.id)?.also {
            incomingMessages.addAll(MessageSizeEstimator.getNodesSize(it.size))
        } ?: emptyList()
    }

    private fun handleFindNeighbors(id: PeerId): List<Enr> {
        return table.findNeighbors(id).also {
            incomingMessages.add(MessageSizeEstimator.getNeighborsSize())
            outgoingMessages.addAll(MessageSizeEstimator.getNodesSize(it.size))
        }
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