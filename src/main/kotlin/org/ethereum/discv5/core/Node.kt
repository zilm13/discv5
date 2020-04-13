package org.ethereum.discv5.core

import io.libp2p.core.crypto.PrivKey
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
     * Performs FINDNODE request and its handling
     */
    internal fun findNodes(other: Enr): List<Enr> {
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

        val response = router.route(this, other, FindNodeMessage(buckets.toList()))
        response.map { handle(it, other) }
        return response.map {(it as NodesMessage).peers}.flatten()
    }

    private fun handleFindNodes(message: FindNodeMessage): List<Message> {
        return table.find(message.buckets).stream().limit(K_BUCKET.toLong()).toList().chunked(4).map {
            NodesMessage(it)
        }
    }

    fun handle(messages: List<Message>, initiator: Enr): List<Message> {
        return messages.map { handle(it, initiator) }.flatten()
    }

    fun handle(message: Message, initiator: Enr): List<Message> {
        return when (message.type) {
            MessageType.FINDNODE -> handleFindNodes(message as FindNodeMessage)
            MessageType.NODES -> handleNodes(message as NodesMessage, initiator)
            else -> error("Not expected")
        }
    }

    private fun handleNodes(message: NodesMessage, initiator: Enr): List<Message> {
        this.table.put(initiator, this::ping)
        message.peers.forEach { enrFound -> this.table.put(enrFound, this::ping) }
        return emptyList()
    }


    fun initTasks(): Unit {
        if (tasks.isNotEmpty()) error("Already initialized")
        tasks.let {
            it.add(RecursiveTableVisit(this) {enr ->
                this.findNodes(enr)
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
