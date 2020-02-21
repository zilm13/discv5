package org.ethereum.discv5.core

import io.libp2p.core.crypto.PrivKey
import io.libp2p.core.multiformats.Multiaddr
import org.ethereum.discv5.util.MessageSizeEstimator
import java.util.Random

val K_BUCKET = 16
val BUCKETS_COUNT = 256
val DISTANCE_DIVISOR = 1 // Reduces number of possible distances with numbers greater than 1


data class Node(val enr: Enr, val privKey: PrivKey, val rnd: Random) {
    val table = KademliaTable(
        enr,
        K_BUCKET,
        BUCKETS_COUNT,
        DISTANCE_DIVISOR,
        listOf(),
        rnd
    )
    val outgoingMessages: MutableList<Int> = ArrayList()
    val incomingMessages: MutableList<Int> = ArrayList()

    fun resetStats() {
        outgoingMessages.clear()
        incomingMessages.clear()
    }

    /**
     * @return peers surrounding [other] using [KademliaTable.findStrict]
     * to match Discovery V5 spec.
     */
    fun findNodesStrict(other: Node): List<Enr> {
        val startNode = Enr(Multiaddr(ByteArray(0)), enr.id)
        val startBucket = other.enr.simTo(enr, DISTANCE_DIVISOR)
        var currentRadius = 0
        val res: MutableList<Enr> = ArrayList()
        while (res.size < K_BUCKET) {
            val candidates: MutableSet<Enr> = HashSet()
            val topBucket = startBucket + currentRadius
            val bottomBucket = startBucket - currentRadius
            if (topBucket <= BUCKETS_COUNT) {
                outgoingMessages.add(MessageSizeEstimator.getFindNodesSize())
                val topBucketNodes = other.table.findStrict(topBucket)
                incomingMessages.addAll(MessageSizeEstimator.getNodesSize(topBucketNodes.size))
                topBucketNodes.forEach { candidates.add(it) }
            }
            if (bottomBucket in 1 until topBucket) {
                outgoingMessages.add(MessageSizeEstimator.getFindNodesSize())
                val bottomBucketNodes = other.table.findStrict(bottomBucket)
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
     * @return peers surrounding [other] using [KademliaTable.findDown]. Experimental.
     */
    fun findNodesDown(other: Node): List<Enr> {
        val startNode = Enr(Multiaddr(ByteArray(0)), enr.id)
        val startBucket = other.enr.simTo(enr, DISTANCE_DIVISOR)
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
                    val enrs = other.table.findDown(bottomBucket)
                    incomingMessages.addAll(MessageSizeEstimator.getNodesSize(enrs.size))
                    if (enrs.isNotEmpty()) {
                        val lastDistance = enrs.last().simTo(other.enr, DISTANCE_DIVISOR)
                        val lastBucketNottFull = enrs.size == K_BUCKET && lastDistance != bottomBucket
                        var enrsWDistance = enrs.map { Pair(it.simTo(other.enr, DISTANCE_DIVISOR), it) }
                        if (lastBucketNottFull) {
                            enrsWDistance = enrsWDistance.filter { it.first != lastDistance }
                        }
                        enrsWDistance.groupBy { it.first }.forEach { bucket, pairsList ->
                            fullBucketsCache[bucket] =
                                pairsList.map { it.second }
                        }
                    }
                }
                fullBucketsCache[bottomBucket]?.forEach { candidates.add(it) }
                fullBucketsCache.remove(bottomBucket)
            }
            if (topBucket in BUCKETS_COUNT downTo (bottomBucket + 1)) { // skip when topBucket == bottomBucket
                outgoingMessages.add(MessageSizeEstimator.getFindNodesDownSize())
                val topBucketNodes = other.table.findDown(topBucket)
                incomingMessages.addAll(MessageSizeEstimator.getNodesSize(topBucketNodes.size))
                topBucketNodes.filter { other.enr.simTo(it, DISTANCE_DIVISOR) == topBucket }
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

    /**
     * @return peers surrounding [other] like in original Kademlia
     * find neighbors implementation
     */
    fun findNeighbors(other: Node): List<Enr> {
        outgoingMessages.add(MessageSizeEstimator.getNeighborsSize())
        val nodes = other.table.findNeighbors(enr.id)
        incomingMessages.addAll(MessageSizeEstimator.getNodesSize(nodes.size))
        return nodes
    }
}