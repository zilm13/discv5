package org.ethereum.discv5

import io.libp2p.core.PeerId
import io.libp2p.core.multiformats.Multiaddr
import org.ethereum.discv5.core.BUCKETS_COUNT
import org.ethereum.discv5.core.Enr
import org.ethereum.discv5.core.KademliaTable
import org.ethereum.discv5.core.Node
import org.ethereum.discv5.core.Router
import org.ethereum.discv5.util.InsecureRandom
import org.ethereum.discv5.util.KeyUtils
import org.ethereum.discv5.util.formatTable
import kotlin.math.pow
import kotlin.math.roundToInt

val PEER_COUNT = 100
val RANDOM: InsecureRandom = InsecureRandom().apply { setInsecureSeed(1) }
val ROUNDS_COUNT = 100
val ROUNDTRIP_MS = 100

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
            .forEach { peer.table.put(it.enr) { true } }
        if (index > 0 && index % 1000 == 0) {
            println("$index peer tables filled")
        }
    }
//    printKademliaTableStats(peers)
//    System.exit(-1)

    println("Run simulation with FINDNODE(distances) API method according to updated Discovery V5 protocol")
    runSimulationImpl(peers, ROUNDS_COUNT)
    val trafficFindNodeStrict = gatherTrafficStats(peers)
    val latencyFindNodeStrict = gatherLatencyStats(peers)
    peers.forEach(Node::resetAll)
    println("trafficFindNodeStrict")
    trafficFindNodeStrict.forEach { println(it) }
    println("latencyFindNodeStrict")
    latencyFindNodeStrict.forEach { println(it) }
}

fun gatherTrafficStats(peers: List<Node>): List<Int> {
    return peers.map { calcTraffic(it) }.sorted()
}

fun gatherLatencyStats(peers: List<Node>): List<Int> {
    return peers.map { calcTotalTime(it) }.sorted()
}

fun runSimulationImpl(peers: List<Node>, rounds: Int) {
    peers.forEach(Node::initTasks)
    for (i in 0 until rounds) {
        println("Simulating round #${i + 1}")
        peers.forEach(Node::step)
    }
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
    return node.outgoingMessages.size * ROUNDTRIP_MS
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
