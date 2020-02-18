package org.ethereum.discv5

import io.libp2p.core.PeerId
import io.libp2p.core.multiformats.Multiaddr
import org.ethereum.discv5.core.BUCKETS_COUNT
import org.ethereum.discv5.core.Enr
import org.ethereum.discv5.core.KademliaTable
import org.ethereum.discv5.core.Node
import org.ethereum.discv5.util.KeyUtils
import java.security.SecureRandom
import kotlin.math.pow
import kotlin.math.roundToInt

val PEER_COUNT = 10_000
val RANDOM = SecureRandom(ByteArray(1) { 1.toByte() })
val ROUNDS_COUNT = 100

fun main(args: Array<String>) {
    println("Creating private key - enr pairs")
    val peers = (0 until (PEER_COUNT)).map {
        val ip = "127.0.0.1"
        val privKey =
            KeyUtils.genPrivKey(RANDOM)
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
    // For simulations lets assume that it's Pareto with alpha = 0.24 and xm = 10
    val peerDistribution = calcPeerDistribution(
        PEER_COUNT,
        0.24,
        10.0,
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

    for (i in 0 until ROUNDS_COUNT) {
        println("Simulating round #${i + 1}")
        peers.forEach {
            val allEnrs = it.table.findAll()
            val nextEnr = allEnrs[i % allEnrs.size]
            val nextNode: Node = peersMap[nextEnr]!!
            it.findNodesStrict(nextNode)
//            it.findNeighbors(nextNode)
//            it.findNodesDown(nextNode)
        }
    }

    // TODO: gather stats

//    val header = "Peer #\t Stored nodes\n"
//    val stats = peers.mapIndexed { index, peer ->
//        index.toString() + "\t" + calcKademliaPeers(
//            peer.table
//        )
//    }.joinToString("\n")
//    println((header + stats).formatTable(true))
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