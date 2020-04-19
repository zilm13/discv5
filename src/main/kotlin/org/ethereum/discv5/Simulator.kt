package org.ethereum.discv5

import io.libp2p.core.PeerId
import io.libp2p.core.multiformats.Multiaddr
import org.ethereum.discv5.core.BUCKETS_COUNT
import org.ethereum.discv5.core.Enr
import org.ethereum.discv5.core.KademliaTable
import org.ethereum.discv5.core.MetaKey
import org.ethereum.discv5.core.Node
import org.ethereum.discv5.core.Router
import org.ethereum.discv5.util.InsecureRandom
import org.ethereum.discv5.util.KeyUtils
import org.ethereum.discv5.util.formatTable
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.pow
import kotlin.math.roundToInt

val PEER_COUNT = 100
val RANDOM: InsecureRandom = InsecureRandom().apply { setInsecureSeed(1) }
val ROUNDS_COUNT = 100
val LATENCY_LEG_MS = 100
val LATENCY_MULTI_EACH_MS = 5 // 2 messages sent simultaneously = 100 + 5
val SUBNET_SHARE_PCT = 1 // Which part of all peers are validators from one tracked subnet, in %
val TARGET_PERCENTILE = 98
val TIMEOUT_STEP = 1000 // If target percentile is not achieved until TIMEOUT_STEP, simulation is stopped

fun main(args: Array<String>) {
    println("Creating private key - enr pairs for $PEER_COUNT nodes")
    val router = Router()
    val peers = (0 until (PEER_COUNT)).map {
        val ip = "127.0.0.1"
        val privKey = KeyUtils.genPrivKey(RANDOM)
        val addr = Multiaddr("/ip4/$ip/tcp/$it")
        val enr = Enr(
            addr,
            PeerId(KeyUtils.privToPubCompressed(privKey))
        )
        Node(enr, privKey, RANDOM, router).apply {
            router.register(this)
        }
    }

    // It's believed that p2p node uptime is complied with Pareto distribution
    // See Stefan Saroiu, Krishna P. Gummadi, Steven D. Gribble "A Measurement Study of Peer-to-Peer File Sharing Systems"
    // https://www.researchgate.net/publication/2854843_A_Measurement_Study_of_Peer-to-Peer_File_Sharing_Systems
    // For simulations lets assume that it's Pareto with alpha = 0.18 and xm = 1.
    // With such distribution 40% of peers are mature enough to pass through its Kademlia table all peers in network.
    val alpha = 0.18
    val xm = 1.0
    println("""Calculating distribution of peer tables fullness with alpha = $alpha and Xm = $xm""")
    val peerDistribution = calcPeerDistribution(
        PEER_COUNT,
        alpha,
        xm,
        240.0
    )
    assert(peers.size == peerDistribution.size)
    println("Filling peer's Kademlia tables according to distribution")
    peers.forEachIndexed() { index, peer ->
        (1..peerDistribution[index]).map { peers[RANDOM.nextInt(PEER_COUNT)] }
            .forEach { peer.table.put(it.enr) { enr, cb -> cb(true) } }
        if (index > 0 && index % 1000 == 0) {
            println("$index peer tables filled")
        }
    }
//    printKademliaTableStats(peers)
//    System.exit(-1)

    println("Run simulation with FINDNODE(distances) API method according to updated Discovery V5 protocol")
    val enrStats = runSimulationImpl(peers, ROUNDS_COUNT)
//    visualizeSubnetPeersStats(peers)
    printSubnetPeersStats(enrStats)
//    val trafficFindNodeStrict = gatherTrafficStats(peers)
//    val latencyFindNodeStrict = gatherLatencyStats(peers)
    peers.forEach(Node::resetAll)
//    println("trafficFindNodeStrict")
//    trafficFindNodeStrict.forEach { println(it) }
//    println("latencyFindNodeStrict")
//    latencyFindNodeStrict.forEach { println(it) }
}

fun gatherTrafficStats(peers: List<Node>): List<Int> {
    return peers.map { calcTraffic(it) }.sorted()
}

fun gatherLatencyStats(peers: List<Node>): List<Int> {
    return peers.map { calcTotalTime(it) }.sorted()
}

fun runSimulationImpl(peers: List<Node>, rounds: Int): ArrayList<Pair<Int, Int>> {
    assert(rounds > 50)
    peers.forEach(Node::initTasks)
    for (i in 0 until 50) {
        println("Simulating round #${i + 1}")
        peers.forEach(Node::step)
    }

    println("Making $SUBNET_SHARE_PCT% of peers with ENR from subnet")
    peers.shuffled(RANDOM).take((peers.size * SUBNET_SHARE_PCT / 100.0).roundToInt()).forEach {
        it.updateEnr(
            it.enr.seq.inc(),
            HashMap<ByteArray, ByteArray>().apply {
                put(MetaKey.SUBNET.of, ByteArray(1) { 13.toByte() })
            }
        )
    }

    val subnetIds = peers.filter { it.enr.seq == BigInteger.valueOf(2) }.map { it.enr.id }.toSet()
    assert(subnetIds.isNotEmpty())
    println("Total subnet peers count: ${subnetIds.size}")
    val enrStats = ArrayList<Pair<Int, Int>>()
    enrStats.add(calcSubnetPeersStats(peers, subnetIds))
    for (i in 51 until (51 + TIMEOUT_STEP)) {
        println("Simulating round #${i + 1}")
        peers.forEach(Node::step)
        enrStats.add(calcSubnetPeersStats(peers, subnetIds))
        if (enrStats.last().second * 100.0 / (enrStats.last().first + enrStats.last().second) > TARGET_PERCENTILE) {
            break
        }
    }

    return enrStats
}

fun visualizeSubnetPeersStats(peers: List<Node>) {
    val subnetIds = peers.filter { it.enr.seq == BigInteger.valueOf(2) }.map { it.enr.id }.toSet()
    println("Peer knowledge stats")
    println("===================================")
    val header = "Peer #\t Subnet\t Known peers from subnet\n"
    val stats = peers.map { peer ->
        peer.enr.id.toHex().substring(0, 6) + "\t" +
                (peer.enr.seq == BigInteger.valueOf(2)).let { if (it) "X" else " " } + "\t" +
                peer.table.findAll()
                    .filter { subnetIds.contains(it.id) }
                    .map { it.id.toHex().substring(0, 6) + "(" + it.seq + ")" }
                    .joinToString(", ")
    }.joinToString("\n")
    println((header + stats).formatTable(true))
}

fun calcSubnetPeersStats(peers: List<Node>, subnetIds: Set<PeerId>): Pair<Int, Int> {
    val firstCounter = AtomicInteger(0)
    val secondCounter = AtomicInteger(0)
    peers.forEach { peer ->
        peer.table.findAll()
            .filter { subnetIds.contains(it.id) }
            .forEach {
                when (it.seq) {
                    BigInteger.ONE -> firstCounter.incrementAndGet()
                    BigInteger.valueOf(2) -> secondCounter.incrementAndGet()
                }
            }
    }

    return Pair(firstCounter.get(), secondCounter.get())
}

fun printSubnetPeersStats(enrStats: ArrayList<Pair<Int, Int>>) {
    println("Peer knowledge stats")
    println("===================================")
    val header = "Round #\t Peers known from 1st subnet\t from 2nd subnet\t Total subnet peer enrs\n"
    val stats = enrStats.mapIndexed() { index, pair ->
        val total = pair.first + pair.second
        index.toString() + "\t" +
                "%.2f".format(pair.first * 100.0 / total) + "%\t" +
                "%.2f".format(pair.second * 100.0 / total) + "%\t" +
                total
    }.joinToString("\n")
    println((header + stats).formatTable(true))
}

fun printKademliaTableStats(peers: List<Node>) {
    println("Kademlia table stats for every peer")
    println("===================================")
    val header = "Peer #\t Stored nodes\n"
    val stats = peers.mapIndexed { index, peer ->
        index.toString() + "\t" + calcKademliaPeers(
            peer.table
        )
    }.joinToString("\n")
    println((header + stats).formatTable(true))
}

fun calcTraffic(node: Node): Int {
    return node.incomingMessages.sum() + node.outgoingMessages.sum()
}

fun calcTotalTime(node: Node): Int {
    return node.roundtripLatency
        .filter { it.isNotEmpty() }
        .map { list -> LATENCY_LEG_MS + LATENCY_MULTI_EACH_MS * (list.size - 1) }
        .sum()
}

fun calcKademliaPeers(table: KademliaTable): Int {
    var res = 0
    for (i in BUCKETS_COUNT downTo 1) {
        res += table.find(i).size
    }
    return res
}

fun calcPeerDistribution(count: Int, alpha: Double, xm: Double, xFull: Double): List<Int> {
    val step = 10.0 / count
    val res: MutableList<Int> = ArrayList()
    val distribution = calcDistribution(step, alpha, xm, xFull)
    (0 until count).chunked((count * step).toInt()).forEachIndexed { index, chunk ->
        chunk.map { res.add((distribution[index] * count * 1000 / (xFull * count)).roundToInt()) }
    }
    return res
}

fun calcDistribution(step: Double, alpha: Double, xm: Double, xFull: Double): List<Double> {
    val reverseCdf: MutableList<Double> = ArrayList()
    var y = 0.0
    var xFullReached = false
    while (y < 1) {
        if (xFullReached) {
            reverseCdf.add(xFull)
        } else {
            val nextX = calcParetoCdfReversed(y, alpha, xm)
            if (nextX > xFull) {
                xFullReached = true
                reverseCdf.add(xFull)
            } else {
                reverseCdf.add(nextX)
            }

        }
        y += step
    }

    return reverseCdf
}

fun calcParetoCdfReversed(y: Double, alpha: Double, xm: Double): Double {
    return xm / (1 - y).pow(1 / alpha)
}
