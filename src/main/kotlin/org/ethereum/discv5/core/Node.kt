package org.ethereum.discv5.core

import io.libp2p.core.PeerId
import io.libp2p.core.crypto.PrivKey
import io.libp2p.core.crypto.sha256
import io.libp2p.etc.types.copy
import org.apache.logging.log4j.LogManager
import org.ethereum.discv5.core.task.ImmediateProducer
import org.ethereum.discv5.core.task.ProducerTask
import org.ethereum.discv5.core.task.Task
import org.ethereum.discv5.core.task.regular.MessageRoundTripTask
import org.ethereum.discv5.core.task.regular.NodeUpdateTask
import org.ethereum.discv5.core.task.regular.PingTableVisit
import org.ethereum.discv5.core.task.regular.RecursiveTableVisit
import org.ethereum.discv5.core.task.topic.ParallelIdSearchTask
import org.ethereum.discv5.core.task.topic.TopicAdvertiseTask
import org.ethereum.discv5.core.task.topic.TopicFindTask
import org.ethereum.discv5.util.ByteArrayWrapper
import java.math.BigInteger
import java.util.LinkedList
import java.util.Random
import kotlin.math.roundToInt
import kotlin.streams.toList

val K_BUCKET = 16
val BUCKETS_COUNT = 256
val CHANCE_TO_FORGET = 0.2
val NEIGHBORS_DISTANCES_LIMIT = 3
val AD_RETRY_MAX_STEPS = 10
val AD_LIFE_STEPS = 60
val PARALLELISM = 3 // Parallelism of all actions

data class Node(var enr: Enr, val privKey: PrivKey, val rnd: Random, val router: Router) {
    val tasks: MutableList<Task> = ArrayList()
    val topics: MutableMap<ByteArrayWrapper, MutableMap<PeerId, Pair<Enr, Int>>> = HashMap()
    val table = KademliaTable(
        enr,
        K_BUCKET,
        BUCKETS_COUNT,
        { enr, cb -> ping(enr, cb) },
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
                this.findNodes(enr, cb = {})
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
        for (topic in topics) {
            val toRemove = HashSet<PeerId>()
            for (entry in topic.value) {
                if (entry.value.second == 1) {
                    toRemove.add(entry.key)
                } else {
                    entry.setValue(Pair(entry.value.first, entry.value.second - 1))
                }
            }
            toRemove.forEach { topic.value.remove(it) }
        }
        val toRemove = topics.filter { it.value.isEmpty() }.keys
        toRemove.forEach { topics.remove(it) }
    }

    fun resetAll() {
        tasks.clear()
        outgoingMessages.clear()
        incomingMessages.clear()
        roundtripLatency.clear()
        initTasks()
    }

    fun updateEnr(seq: BigInteger, meta: Map<ByteArray, ByteArray>) {
        this.enr = Enr(enr.addr, enr.id, seq, meta)
        table.updateHome(enr)
    }

    /**
     * Performs FINDNODE request and returns its result
     * @param other Enr identity of peer to send request to
     * @param center id used as a center of search, so lookup is searching for nodes closest to `center` PeerId
     * @param cb called when result is available
     */
    internal fun findNodes(other: Enr, center: PeerId = enr.id, cb: (List<Enr>) -> Unit) {
        val startBucket = other.id.to(center)
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

        tasks.add(MessageRoundTripTask(this, other, FindNodeMessage(buckets.toList())) {
            handle(it, other)
            it.map { message -> (message as NodesMessage).peers }.flatten().apply(cb)
        }.also {
            logger.trace("Task $it added")
        })
    }

    /**
     * Performs FINDNODE(0) request on other node to get its enr and handles it
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
            MessageType.REGTOPIC -> handleRegTopic(message as RegTopicMessage, initiator)
            MessageType.TICKET -> handleTicket(message as TicketMessage, initiator)
            MessageType.REGCONFIRMATION -> handleRegConfirmation(message as RegConfirmation, initiator)
            MessageType.TOPICQUERY -> handleTopicQuery(message as TopicQuery, initiator)
            else -> error("Not expected")
        }
    }

    private fun handleTopicQuery(topicQuery: TopicQuery, initiator: Enr): List<Message> {
        return topics[topicQuery.topic]?.map { it.value.first }?.toList()
            ?.chunked(4)?.map {
                NodesMessage(it)
            } ?: emptyList()
    }

    private fun handleRegConfirmation(regConfirmation: RegConfirmation, initiator: Enr): List<Message> {
        // Don't need anything here, all is done in task
        return emptyList()
    }

    private fun handleTicket(ticketMessage: TicketMessage, initiator: Enr): List<Message> {
        // Don't need anything here, all is done in task
        return emptyList()
    }

    private fun handleRegTopic(regTopicMessage: RegTopicMessage, initiator: Enr): List<Message> {
        table.findOne(initiator.id)?.takeIf { it.seq != regTopicMessage.enr.seq }?.let {
            tasks.add(NodeUpdateTask(initiator, this)
                .also {
                    logger.debug("Task $it added as REGTOPIC shows new sequence ${regTopicMessage.enr.seq}")
                })
        }
        val ticket = ByteArray(TicketMessage.getAverageTicketSize())
        // TODO: 100 advertisements per topic limit
        when {
            regTopicMessage.topic !in topics -> {
                topics[regTopicMessage.topic] =
                    HashMap<PeerId, Pair<Enr, Int>>().also { it[initiator.id] = Pair(initiator, AD_LIFE_STEPS) }
                return listOf(TicketMessage(ticket, 0), RegConfirmation(regTopicMessage.topic))
            }
            initiator.id !in topics[regTopicMessage.topic]!! -> {
                topics[regTopicMessage.topic]?.set(initiator.id, Pair(initiator, AD_LIFE_STEPS))
                return listOf(TicketMessage(ticket, 0), RegConfirmation(regTopicMessage.topic))
            }
            else -> {
                val registeredPair = topics[regTopicMessage.topic]!![initiator.id]!!
                return listOf(TicketMessage(ticket, registeredPair.second))
            }
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

    internal fun ping(other: Enr, cb: (Boolean) -> Unit) {
        tasks.add(MessageRoundTripTask(this, other, PingMessage(enr.seq)) {
            handle(it, other)
            it.isNotEmpty().apply(cb)
        }.also {
            logger.trace("Task $it added")
        })
    }

    /**
     * Performs REGTOPIC request and returns its result
     * @param radius Number of peers to place ad at
     * @param random Whether to place it within topic hash peer ids
     * or on random set of peers
     */
    internal fun registerTopic(topic: ByteArray, radius: Int, random: Boolean, cb: (List<Pair<Enr, Boolean>>) -> Unit) {
        val topicHash = sha256(topic)
        val topicId = PeerId(topicHash)
        val mediaSearchTask: ProducerTask<List<Enr>>
        if (random) {
            // FIXME: or use store of all known peers instead of only Kademlia?
            mediaSearchTask = ImmediateProducer(table.findAll().shuffled(rnd).take(radius))
        } else {
            val distance = topicId.to(enr.id)
            var candidates = table.find(distance)
            if (candidates.size < K_BUCKET) {
                candidates = KademliaTable.filterNeighborhood(topicId, table.findAll(), K_BUCKET)
            }
            val candidatesQueue = LinkedList(candidates)
            mediaSearchTask = ParallelIdSearchTask(this, topicId, candidatesQueue::poll, PARALLELISM, radius, rnd)
        }

        val task = TopicAdvertiseTask(
            this, mediaSearchTask,
            ByteArrayWrapper(topicHash), AD_RETRY_MAX_STEPS, PARALLELISM, cb
        ).also {
            logger.trace("Task $it added")
        }
        tasks.add(task)
    }

    // TODO: Random topic ad placement search compatibility
    internal fun findTopic(topic: ByteArray, radius: Int, count: Int, cb: (Set<PeerId>) -> Unit) {
        val topicHash = sha256(topic)
        val topicId = PeerId(topicHash)
        val mediaSearchTask: ProducerTask<List<Enr>>
        val distance = topicId.to(enr.id)
        var candidates = table.find(distance)
        if (candidates.size < K_BUCKET) {
            candidates = KademliaTable.filterNeighborhood(topicId, table.findAll(), K_BUCKET)
        }
        val candidatesQueue = LinkedList(candidates)
        mediaSearchTask = ParallelIdSearchTask(this, topicId, candidatesQueue::poll, PARALLELISM, radius, rnd)
        val task = TopicFindTask(
            this, mediaSearchTask,
            ByteArrayWrapper(topicHash), count, PARALLELISM, cb
        ).also {
            logger.trace("Task $it added")
        }
        tasks.add(task)
    }
}
