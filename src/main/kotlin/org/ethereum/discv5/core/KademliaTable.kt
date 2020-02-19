package org.ethereum.discv5.core

import io.libp2p.core.PeerId
import io.libp2p.core.multiformats.Multiaddr
import java.lang.Integer.min
import java.util.LinkedList

class Bucket(
    private val maxSize: Int,
    private val payload: LinkedList<Enr> = LinkedList()
) : List<Enr> by payload {
    fun put(enr: Enr) {
        if (contains(enr)) {
            payload.remove(enr)
        }
        if (size == maxSize) {
            payload.removeLast()
        }
        payload.add(enr)
    }
}

class KademliaTable(
    private val home: Enr,
    private val bucketSize: Int,
    private val numberBuckets: Int,
    private val distanceDivisor: Int,
    private val bootNodes: Collection<Enr> = ArrayList()
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
        buckets.computeIfAbsent(distance) { Bucket(bucketSize) }.put(enr)
    }

    /**
     * @return up to limit ENRs starting from startBucket and going down until 1st bucket is reached
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