package org.ethereum.discv5.core

import io.libp2p.core.crypto.PrivKey
import io.libp2p.etc.types.copy
import org.apache.logging.log4j.LogManager
import java.math.BigInteger
import java.util.Random
import kotlin.math.roundToInt
import kotlin.streams.toList

val K_BUCKET = 16
val BUCKETS_COUNT = 256
val DISTANCE_DIVISOR = 1 // Reduces number of possible distances with numbers greater than 1
val CHANCE_TO_FORGET = 0.2
val NEIGHBORS_DISTANCES_LIMIT = 3

data class Node(var enr: Enr, val privKey: PrivKey, val rnd: Random, val router: Router) {
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
    val roundtripLatency: MutableList<List<Unit>> = ArrayList()
    private val logger = LogManager.getLogger("Node${enr.toId()}")

    /**
     * TODO:
     * 1. Do we have table for all known peers, which is completely cleaned on restart
     * but used while it works? Is this table used as a source for tasks input?
     * 2. Each task should maintain number of parallel jobs, where number is configured
     */
    fun initTasks(): Unit {
        if (tasks.isNotEmpty()) error("Already initialized")
        tasks.let {
            it.add(RecursiveTableVisit(this) { enr ->
                this.findNodes(enr)
            })
            it.add(PingTableVisit(this))
        }
    }

    fun step(): Unit {
        tasks.removeAll {
            it.isOver().apply {
                if (this) {
                    logger.debug("Task $it will be removed because it's over")
                }
            }
        }
        // XXX: we could have duplicates if node was seen with new seq several times
        val firstOccurrence = HashSet<NodeUpdateTask>()
        tasks.removeAll { task ->
            (task is NodeUpdateTask && firstOccurrence.contains(task)).also {
                if (!it && task is NodeUpdateTask) {
                    firstOccurrence.add(task)
                }
            }
        }
        // XXX: new tasks could be added during any step but we shouldn't change current tasks
        val thisStepTasks = tasks.copy()
        thisStepTasks.forEach { it.step() }
    }

    fun resetAll() {
        tasks.clear()
        outgoingMessages.clear()
        incomingMessages.clear()
        roundtripLatency.clear()
    }

    fun updateEnr(seq: BigInteger, meta: Map<ByteArray, ByteArray>) {
        this.enr = Enr(enr.addr, enr.id, seq, meta)
        table.updateHome(enr)
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
        return response.map { (it as NodesMessage).peers }.flatten()
    }

    /**
     * Performs FINDNODE request on other node to get it and its handling
     */
    internal fun updateNode(other: Enr): Enr {
        val response = router.route(this, other, FindNodeMessage(listOf(0)))
        response.map { handle(it, other) }
        return response.map { (it as NodesMessage).peers }.flatten().get(0)
    }

    fun handle(messages: List<Message>, initiator: Enr): List<Message> {
        return messages.map { handle(it, initiator) }.flatten()
    }

    fun handle(message: Message, initiator: Enr): List<Message> {
        return when (message.type) {
            MessageType.FINDNODE -> handleFindNodes(message as FindNodeMessage)
            MessageType.NODES -> handleNodes(message as NodesMessage, initiator)
            MessageType.PING -> handlePing(message as PingMessage, initiator)
            MessageType.PONG -> handlePong(message as PongMessage, initiator)
            else -> error("Not expected")
        }
    }

    private fun handleFindNodes(message: FindNodeMessage): List<Message> {
        return table.find(message.buckets).stream().limit(K_BUCKET.toLong()).toList()
            .chunked(4).map {
                NodesMessage(it)
            }
    }

    private fun handleNodes(message: NodesMessage, initiator: Enr): List<Message> {
        this.table.put(initiator, this::ping)
        message.peers.forEach { enrFound -> this.table.put(enrFound, this::ping) }
        return emptyList()
    }

    /**
     * It would be more realistic if the chance of dropping peer will decrease with
     * each check (peer which is online for 2 hours will be more likely online in an hour
     * than the peer which is online for 1 hour) but such simulation could be very
     * complex and set aside for further improvements of simulation.
     **/
    private fun handlePing(message: PingMessage, initiator: Enr): List<Message> {
        val alive = rnd.nextInt((1 / CHANCE_TO_FORGET).roundToInt()) != 0
        val that = this
        return if (alive) {
            // Update initiator record if on record sequence not matches one from message
            table.findOne(initiator.id)?.takeIf { it.seq != message.seq }?.run {
                tasks.add(NodeUpdateTask(initiator, that)
                    .also {
                        logger.debug("Task $it added as PING shows new sequence ${message.seq}")
                    })
            }
            listOf(PongMessage(enr.seq))
        } else {
            emptyList()
        }
    }

    private fun handlePong(message: PongMessage, initiator: Enr): List<Message> {
        if (initiator.seq == message.seq) {
            this.table.put(initiator, this::ping)
        } else {
            tasks.add(NodeUpdateTask(initiator, this).also {
                logger.debug("Task $it added as PONG shows new sequence ${message.seq}")
            })
        }
        return emptyList()
    }

    internal fun ping(other: Enr): Boolean {
        val response = router.route(this, other, PingMessage(enr.seq))
        response.map { handle(it, other) }
        return response.isNotEmpty()
    }
}
