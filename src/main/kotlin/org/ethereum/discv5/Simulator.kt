package org.ethereum.discv5

import io.libp2p.core.PeerId
import io.libp2p.core.multiformats.Multiaddr
import org.ethereum.discv5.core.BUCKETS_COUNT
import org.ethereum.discv5.core.Enr
import org.ethereum.discv5.core.KademliaTable
import org.ethereum.discv5.core.Node
import org.ethereum.discv5.util.InsecureRandom
import org.ethereum.discv5.util.KeyUtils
import org.ethereum.discv5.util.formatTable
import java.security.SecureRandom
import kotlin.math.pow
import kotlin.math.roundToInt

val PEER_COUNT = 1_000
val RANDOM : InsecureRandom = InsecureRandom().apply { setInsecureSeed(1) }
val ROUNDS_COUNT = 100
val ROUNDTRIP_MS = 100

fun main(args: Array<String>) {
    println("Creating private key - enr pairs for $PEER_COUNT nodes")
    val peers = (0 until (PEER_COUNT)).map {
        val ip = "127.0.0.1"
        val privKey = KeyUtils.genPrivKey(RANDOM)
        val addr = Multiaddr("/ip4/$ip/tcp/$it")
        val enr = Enr(
            addr,
            PeerId(KeyUtils.privToPubCompressed(privKey))
        )
        Node(enr, privKey)
    }
    val peersMap = peers.map { it.enr to it }.toMap()

    println("Calculating distribution of peer tables fullness")
    // It's believed that p2p node uptime is complied with Pareto distribution
    // See Stefan Saroiu, Krishna P. Gummadi, Steven D. Gribble "A Measurement Study of Peer-to-Peer File Sharing Systems"
    // https://www.researchgate.net/publication/2854843_A_Measurement_Study_of_Peer-to-Peer_File_Sharing_Systems
    // For simulations lets assume that it's Pareto with alpha = 0.18 and xm = 1.
    // With such distribution 40% of peers are mature enough to pass through its Kademlia table all peers in network.

    val peerDistribution = calcPeerDistribution(
        PEER_COUNT,
        0.18,
        1.0,
        240.0
    )
    assert(peers.size == peerDistribution.size)
    println("Filling peer's Kademlia tables according to distribution")
    peers.forEachIndexed() { index, peer ->
        (1..peerDistribution[index]).map { peers[RANDOM.nextInt(PEER_COUNT)] }.forEach { peer.table.put(it.enr) }
        if (index > 0 && index % 1000 == 0) {
            println("$index peer tables filled")
        }
    }
//    printKademliaTableStats(peers)

    println("Run simulation with FINDNODE(distance) API method according to Discovery V5 protocol")
    runFindNodesStrictSimulation(peers, peersMap)
    val trafficFindNodeStrict = gatherTrafficStats(peers)
    val latencyFindNodeStrict = gatherLatencyStats(peers)
    peers.forEach(Node::resetStats)

    println("Run simulation with FINDNODE(peerId) API method similar to original Kademlia protocol")
    runFindNeighborsSimulation(peers, peersMap)
    val trafficFindNeighbors = gatherTrafficStats(peers)
    val latencyFindNeighbors = gatherLatencyStats(peers)
    peers.forEach(Node::resetStats)

//    FIXME
//    runFindNodesDownSimulation(peers, peersMap)

    println("trafficFindNodeStrict")
    trafficFindNodeStrict.forEach { println(it) }

    println("latencyFindNodeStrict")
    latencyFindNodeStrict.forEach { println(it) }

    println("trafficFindNeighbors")
    trafficFindNeighbors.forEach { println(it) }

    println("latencyFindNeighbors")
    latencyFindNeighbors.forEach { println(it) }

//    println("Gathering statistics")
//    val header = "Peer #\t Traffic\n"
//    val stats = peers.mapIndexed { index, peer ->
//        Pair(index.toString(),calcTraffic(
//            peer
//        ))
//    }.sortedBy { pair -> pair.second }.map {pair -> pair.first + "\t" + pair.second}.joinToString("\n")
//    println((header + stats).formatTable(true))

}

fun gatherTrafficStats(peers: List<Node>): List<Int> {
    return peers.map { calcTraffic(it) }.sorted()
}

fun gatherLatencyStats(peers: List<Node>): List<Int> {
    return peers.map { calcTotalTime(it) }.sorted()
}

fun runFindNodesStrictSimulation(peers: List<Node>, peersMap: Map<Enr, Node>, rounds: Int = ROUNDS_COUNT) {
    runSimulationImpl({ node, anotherNode -> node.findNodesStrict(anotherNode) }, peers, peersMap, rounds)
}

fun runFindNodesDownSimulation(peers: List<Node>, peersMap: Map<Enr, Node>, rounds: Int = ROUNDS_COUNT) {
    runSimulationImpl({ node, anotherNode -> node.findNodesDown(anotherNode) }, peers, peersMap, rounds)
}

fun runFindNeighborsSimulation(peers: List<Node>, peersMap: Map<Enr, Node>, rounds: Int = ROUNDS_COUNT) {
    runSimulationImpl({ node, anotherNode -> node.findNeighbors(anotherNode) }, peers, peersMap, rounds)
}

fun runSimulationImpl(nodeTask: (Node, Node) -> Unit, peers: List<Node>, peersMap: Map<Enr, Node>, rounds: Int) {
    for (i in 0 until rounds) {
        println("Simulating round #${i + 1}")
        peers.forEach {
            val allEnrs = it.table.findAll()
            val nextEnr = allEnrs[i % allEnrs.size]
            val nextNode: Node = peersMap[nextEnr]!!
            nodeTask(it, nextNode)
        }
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
        res += table.findStrict(i).size
    }
    return res
}

fun calcPeerDistribution(count: Int, alpha: Double, xm: Double, xFull: Double): List<Int> {
    val step = 0.01 // 100 Steps
    val res: MutableList<Int> = ArrayList()
    val distribution = calcDistribution(step, alpha, xm, xFull)
    (0 until count).chunked((count * step).toInt()).forEachIndexed { index, chunk ->
        chunk.map { res.add((distribution[index] * count / xFull).roundToInt()) }
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