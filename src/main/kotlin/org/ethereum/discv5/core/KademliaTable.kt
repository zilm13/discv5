package org.ethereum.discv5.core

import io.libp2p.core.PeerId
import java.util.LinkedList

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
     */
    fun put(enr: Enr, liveCheck: (Enr) -> Boolean) {
        if (containsById(enr.id)) {
            removeById(enr.id)
        }
        if (size == maxSize && !liveCheck(payload.peekLast())) {
            payload.removeLast()
        }
        if (size < maxSize) {
            payload.add(enr)
        }
    }

    private fun containsById(id: PeerId): Boolean {
        return payload.find { it.id == id } != null
    }

    internal fun removeById(id: PeerId): Boolean {
        return payload.removeIf { it.id == id }
    }
}

/**
 * Kademlia table according to original Kademlia protocol
 * There is a simulation feature configured by [distanceDivisor]:
 * it reduces number of possible buckets and distances
 */
class KademliaTable(
    var home: Enr,
    private val bucketSize: Int,
    private val numberBuckets: Int,
    private val distanceDivisor: Int,
    private val liveCheck: (Enr) -> Boolean,
    bootNodes: Collection<Enr> = ArrayList()
) {
    private val buckets: MutableMap<Int, Bucket> = HashMap()

    init {
        bootNodes.forEach { put(it, liveCheck) }
    }

    fun put(enr: Enr, liveCheck: (Enr) -> Boolean) {
        if (home.id == enr.id) {
            return
        }
        val distance = home.simTo(enr, distanceDivisor)
        buckets.computeIfAbsent(distance) { Bucket(bucketSize) }.put(enr, liveCheck)
    }

    /**
     * Expects non-id updates
     */
    fun updateHome(enr: Enr) {
        assert(enr.id == home.id)
        this.home = enr
    }

    /**
     * @return all ENRs in input bucket
     */
    fun find(buckets: List<Int>): List<Enr> {
        return buckets.map(this@KademliaTable::find).flatten()
    }

    /**
     * @return all ENRs in input bucket
     */
    fun find(bucket: Int): List<Enr> {
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
        return (1..numberBuckets).flatMap { find(it) }
    }

    fun exists(enr: Enr): Boolean {
        val distance = home.simTo(enr, distanceDivisor)
        return buckets[distance]?.find { it == enr }?.let { true } ?: false
    }

    fun remove(enr: Enr): Boolean {
        val distance = home.simTo(enr, distanceDivisor)
        return buckets[distance]?.removeById(enr.id) ?: false
    }
}