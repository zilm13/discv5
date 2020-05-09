package org.ethereum.discv5.core

import io.libp2p.core.PeerId
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.util.LinkedList

/**
 * Enr storage with custom properties according to Kademlia protocol
 * See [put]
 */
class Bucket(
    private val maxSize: Int,
    private val payload: LinkedList<Enr> = LinkedList(),
    private val logger: Logger
) : List<Enr> by payload {

    /**
     * Puts enr to the tail of bucket. If enr is already in bucket,
     * it's moved from its current position to tail. If bucket is already full,
     * in original Kademlia we test head candidate, whether its alive.
     * If yes, enr is dropped, otherwise head is removed and enr is added to the tail.
     */
    fun put(enr: Enr, liveCheck: (Enr, (Boolean) -> Unit) -> Unit) {
        if (containsById(enr.id)) {
            removeById(enr.id)
        }
        if (size == maxSize) {
            val tested = payload.peekLast()
            liveCheck(tested) {
                if (!it) {
                    logger.trace("Dead enr ${payload.peekLast().toId()} removed in favor of ${enr.toId()}")
                    payload.remove(tested)
                    payload.add(enr)
                }
            }
        }
        if (size < maxSize) {
            payload.add(enr)
        }
    }

    internal fun findById(id: PeerId): Enr? {
        return payload.find { it.id == id }
    }

    internal fun containsById(id: PeerId): Boolean {
        return payload.find { it.id == id } != null
    }

    internal fun removeById(id: PeerId): Boolean {
        return payload.removeIf { it.id == id }
    }
}

/**
 * Kademlia table according to original Kademlia protocol
 */
class KademliaTable(
    var home: Enr,
    private val bucketSize: Int,
    private val numberBuckets: Int,
    liveCheck: (Enr, (Boolean) -> Unit) -> Unit,
    bootNodes: Collection<Enr> = ArrayList()
) {
    private val buckets: MutableMap<Int, Bucket> = HashMap()
    private val logger = LogManager.getLogger("Table${home.toId()}")

    init {
        bootNodes.forEach { put(it, liveCheck) }
    }

    fun put(enr: Enr, liveCheck: (Enr, (Boolean) -> Unit) -> Unit) {
        if (home.id == enr.id) {
            return
        }
        val distance = home.to(enr)
        buckets.computeIfAbsent(distance) { Bucket(bucketSize, logger = logger) }.put(enr, liveCheck)
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

    /**
     * @return enr with input id if it's found in table
     */
    fun findOne(id: PeerId): Enr? {
        val distance = home.id.to(id)
        return buckets[distance]?.findById(id)
    }

    fun findAll(): List<Enr> {
        return (1..numberBuckets).flatMap { find(it) }
    }

    fun exists(enr: Enr): Boolean {
        val distance = home.to(enr)
        return buckets[distance]?.containsById(enr.id) ?: false
    }

    fun remove(enr: Enr): Boolean {
        val distance = home.to(enr)
        return buckets[distance]?.removeById(enr.id) ?: false
    }

    companion object {
        /**
         * Sorts [candidates] by distance from [center] and
         * and returns up to [limit] size
         */
        fun filterNeighborhood(center: PeerId, candidates: Collection<Enr>, limit: Int): List<Enr> {
            return candidates.distinct().sortedBy { center.to(it.id) }.take(limit)
        }
    }
}