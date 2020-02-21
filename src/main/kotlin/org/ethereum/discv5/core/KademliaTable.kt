package org.ethereum.discv5.core

import io.libp2p.core.PeerId
import io.libp2p.core.multiformats.Multiaddr
import java.lang.Integer.min
import java.util.LinkedList
import java.util.Random

/**
 * Enr storage with custom properties according to Kademlia protocol
 * See [put]
 */
class Bucket(
    private val maxSize: Int,
    private val payload: LinkedList<Enr> = LinkedList()
) : List<Enr> by payload {

    /**
     * Puts enr to the tail of bucket. If enr is already in bucket,
     * it's moved from its current position to tail. If bucket is already full,
     * in original Kademlia we test head candidate, whether its alive.
     * If yes, enr is dropped, otherwise head is removed and enr is added to the tail.
     *
     * For simulation purposes we use Random.nextBoolean to check whether head is alive.
     * It would be more realistic if the chance of dropping peer will decrease with
     * each check (peer which is online for 2 hours will be more likely online in an hour
     * than the peer which is online for 1 hour) but such simulation could be very
     * complex and set aside for further improvements of simulation
     */
    fun put(enr: Enr, rnd: Random) {
        if (contains(enr)) {
            payload.remove(enr)
        }
        if (size == maxSize && rnd.nextBoolean()) {
            payload.removeLast()
        }
        if (size < maxSize) {
            payload.add(enr)
        }
    }
}

/**
 * Kademlia table according to original Kademlia protocol
 * There is a simulation feature configured by [distanceDivisor]:
 * it reduces number of possible buckets and distances
 */
class KademliaTable(
    val home: Enr,
    private val bucketSize: Int,
    private val numberBuckets: Int,
    private val distanceDivisor: Int,
    bootNodes: Collection<Enr> = ArrayList(),
    private val rnd: Random
) {
    private val buckets: MutableMap<Int, Bucket> = HashMap()

    init {
        bootNodes.forEach { put(it) }
    }

    fun put(enr: Enr) {
        if (home == enr) {
            return
        }
        val distance = home.simTo(enr, distanceDivisor)
        buckets.computeIfAbsent(distance) { Bucket(bucketSize) }.put(enr, rnd)
    }

    /**
     * @return up to limit ENRs starting from startBucket and going down
     * until bucketSize peers found or 1st bucket is reached
     */
    fun findDown(startBucket: Int, limit: Int = bucketSize): List<Enr> {
        if (startBucket == 0) {
            return listOf(home)
        }
        var total = 0
        var currentBucket = startBucket
        val result: MutableList<Enr> = ArrayList()
        while (total < limit && currentBucket > 0) {
            buckets[currentBucket]?.let {
                val needed = min(limit - result.size, it.size)
                for (i in 0 until needed) {
                    result.add(it[i])
                    total++
                }
            }
            currentBucket--
        }
        return result
    }

    /**
     * @return all ENRs in input bucket
     */
    fun findStrict(bucket: Int): List<Enr> {
        if (bucket == 0) {
            return listOf(home)
        }

        return ArrayList<Enr>().apply {
            buckets[bucket]?.let {
                this.addAll(it)
            }
        }
    }

    fun findAll(): List<Enr> {
        return (1..numberBuckets).flatMap { findStrict(it) }
    }

    /**
     * @return Up to limit ENRs surrounding id
     */
    fun findNeighbors(id: PeerId, limit: Int = bucketSize): List<Enr> {
        val startNode = Enr(Multiaddr(ByteArray(0)), id)
        val startBucket = home.simTo(startNode, distanceDivisor)
        var currentRadius = 0
        val res: MutableList<Enr> = ArrayList()
        while (res.size < limit) {
            val candidates: MutableSet<Enr> = HashSet()
            val topBucket = startBucket + currentRadius
            val bottomBucket = startBucket - currentRadius
            if (topBucket <= numberBuckets) {
                findStrict(topBucket).forEach { candidates.add(it) }
            }
            if (bottomBucket in 1 until topBucket) {
                findStrict(bottomBucket).forEach { candidates.add(it) }
            }
            // Have not executed any of 2 above ifs
            if (topBucket > numberBuckets && bottomBucket < 1) {
                break
            }
            filterNeighborhood(startNode, res, candidates, limit, distanceDivisor)
            currentRadius++
        }

        return res
    }

    fun exists(enr: Enr): Boolean {
        val distance = home.simTo(enr, distanceDivisor)
        return buckets[distance]?.find { it == enr }?.let { true } ?: false
    }

    companion object {
        /**
         * Sorts [candidates] by distance from [center] and
         * puts them in [neighbors] up to [limit] size for this collection
         */
        fun filterNeighborhood(
            center: Enr,
            neighbors: MutableList<Enr>,
            candidates: Collection<Enr>,
            limit: Int,
            distanceDivisor: Int
        ) {
            val sortedCandidates = candidates.sortedBy { center.simTo(it, distanceDivisor) }
            for (it in sortedCandidates) {
                neighbors.add(it)
                if (neighbors.size == limit) {
                    break
                }
            }
        }
    }
}