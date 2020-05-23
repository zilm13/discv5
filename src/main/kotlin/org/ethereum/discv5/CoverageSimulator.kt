package org.ethereum.discv5

import io.libp2p.core.PeerId
import io.libp2p.core.multiformats.Multiaddr
import org.ethereum.discv5.core.Enr
import org.ethereum.discv5.core.FindNodeByAttributeMessage
import org.ethereum.discv5.core.FindNodeMessage
import org.ethereum.discv5.core.MetaKey
import org.ethereum.discv5.core.Node
import org.ethereum.discv5.core.NodesMessage
import org.ethereum.discv5.core.Router
import org.ethereum.discv5.core.to
import org.ethereum.discv5.util.ByteArrayWrapper
import org.ethereum.discv5.util.KeyUtils
import org.ethereum.discv5.util.calcPeerDistribution
import org.ethereum.discv5.util.formatTable
import kotlin.math.roundToInt

/**
 * Light version of simulator dedicated to simulation of the best approach
 * of covering maximum share of nodes for better FINDNODEATT strategy
 */
fun main(args: Array<String>) {
    println("Creating private key - enr pairs for $PEER_COUNT nodes")
    val router = Router(RANDOM)
    val peers = (0 until (PEER_COUNT)).map {
        val ip = "127.0.0.${(it / 50000) + 1}"
        val addr = Multiaddr("/ip4/$ip/tcp/${it % 50000}")
        val privKey = KeyUtils.genPrivKey(RANDOM)
        val enr = Enr(
            addr,
            PeerId(KeyUtils.privToPubCompressed(privKey))
        )
        Node(enr, privKey, RANDOM, router).apply {
            router.register(this)
        }
    }

    println("Making $SUBNET_SHARE_PCT% of peers with ENR from subnet")
    val subnetNodes = peers.shuffled(RANDOM).take((peers.size * SUBNET_SHARE_PCT / 100.0).roundToInt())
    subnetNodes.forEach {
        it.updateEnr(
            it.enr.seq.inc(),
            HashMap<ByteArrayWrapper, ByteArrayWrapper>().apply {
                put(MetaKey.SUBNET.of, SUBNET_13)
            }
        )
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
    val fullPeers = ArrayList<Node>()
    val fullPeerDistributionSize = peerDistribution.last()
    peers.forEachIndexed() { index, peer ->
        (1..peerDistribution[index]).map { peers[RANDOM.nextInt(PEER_COUNT)] }
            .forEach { peer.table.put(it.enr) { enr, cb -> cb(true) } }
        if (peerDistribution[index] == fullPeerDistributionSize) {
            fullPeers.add(peer)
        }
        if (index > 0 && index % 1000 == 0) {
            println("$index peer tables filled")
        }
    }

// TODO: Uncomment when need to see only initial fill of Kademlia tables
//    printKademliaTableStats(peers)
//    System.exit(-1)

    // XXX: in searching for a better network lookup strategy we shouldn't duplicate
    // recursive Kademlia table lookup for new nodes, so we start only from full peers
    val searchers = fullPeers.shuffled(RANDOM).take(SEACHERS_COUNT)
    // 1st round
    val statAccumulator = HashMap<Enr, List<Int>>()
    searchers.forEach { searcher ->
        statAccumulator[searcher.enr] = searcherFunctionExpand(searcher, router)
    }

    printSearcherStats(statAccumulator)
}

fun searcherFunctionSimple(searcher: Node, router: Router): List<Int> {
    val found = HashSet<PeerId>()
    val stats = ArrayList<Int>()
    searcher.table.findAll().shuffled(RANDOM).forEach { enr ->
        router.route(searcher, enr, FindNodeByAttributeMessage(Pair(MetaKey.SUBNET.of, SUBNET_13)))
            .filterIsInstance<NodesMessage>()
            .flatMap { msg -> msg.peers }
            .forEach { peer -> found.add(peer.id) }
        stats.add(found.size)
    }

    return stats
}

/**
 * We do search in two steps:
 * 1. send FINDNODEATT to everyone we know from our Kademlia table
 * 2. send FINDNODE(distance) to everyone in our Kademlia table, then send FIDNODEATT to the result
 *      start from distance 256 and decrease it when all nodes are covered
 */
fun searcherFunctionExpand(searcher: Node, router: Router): List<Int> {
    val found = HashSet<PeerId>()
    val stats = ArrayList<Int>()
    searcher.table.findAll().shuffled(RANDOM).forEach { enr ->
        router.route(searcher, enr, FindNodeByAttributeMessage(Pair(MetaKey.SUBNET.of, SUBNET_13)))
            .filterIsInstance<NodesMessage>()
            .flatMap { msg -> msg.peers }
            .forEach { peer -> found.add(peer.id) }
        stats.add(found.size)
    }
    outer@ for (distance in 256 downTo 245) {
        for (enr in searcher.table.findAll().shuffled(RANDOM)) {
            val roundCandidates =
                router.route(searcher, enr, FindNodeMessage(listOf(distance, distance - 1, distance - 2)))
                    .filterIsInstance<NodesMessage>()
                    .flatMap { msg -> msg.peers }
            stats.add(found.size)
            roundCandidates.forEach { candidate ->
                router.route(searcher, candidate, FindNodeByAttributeMessage(Pair(MetaKey.SUBNET.of, SUBNET_13)))
                    .filterIsInstance<NodesMessage>()
                    .flatMap { msg -> msg.peers }
                    .forEach { peer -> found.add(peer.id) }
                stats.add(found.size)
            }
            if (stats.size > 2000) {
                break@outer
            }
        }
    }

    return stats
}

/**
 * We do search in two steps:
 * 1. send FINDNODEATT to everyone we know from our Kademlia table
 * 2. send FINDNODE(distance) to everyone in our Kademlia table, then send FIDNODEATT to the result
 *      we start from the distance = 256 for the closest nodes as our closest environment has most intersections
 *      and from distance = smallestDistance for the furthermost nodes as their closest environment has less intersections with us
 */
fun searcherFunctionSpiral(searcher: Node, router: Router): List<Int> {
    val found = HashSet<PeerId>()
    val stats = ArrayList<Int>()
    searcher.table.findAll().shuffled(RANDOM).forEach { enr ->
        router.route(searcher, enr, FindNodeByAttributeMessage(Pair(MetaKey.SUBNET.of, SUBNET_13)))
            .filterIsInstance<NodesMessage>()
            .flatMap { msg -> msg.peers }
            .forEach { peer -> found.add(peer.id) }
        stats.add(found.size)
    }
    val smallestDistance = searcher.table.findAll().last().to(searcher.enr)
    val middle = (256 - smallestDistance) / 2 + smallestDistance
    for (distance in 256 downTo smallestDistance) {
        searcher.table.findAll().shuffled(RANDOM).forEach { enr ->
            var spiralDistance = distance
            if (enr.to(searcher.enr) > middle) {
                spiralDistance = smallestDistance + (256 - distance)
            }
            val roundCandidates = router.route(searcher, enr, FindNodeMessage(listOf(spiralDistance)))
                .filterIsInstance<NodesMessage>()
                .flatMap { msg -> msg.peers }
            stats.add(found.size)
            roundCandidates.forEach { candidate ->
                router.route(searcher, candidate, FindNodeByAttributeMessage(Pair(MetaKey.SUBNET.of, SUBNET_13)))
                    .filterIsInstance<NodesMessage>()
                    .flatMap { msg -> msg.peers }
                    .forEach { peer -> found.add(peer.id) }
                stats.add(found.size)
            }
        }
    }

    return stats
}

fun printSearcherStats(data: Map<Enr, List<Int>>) {
    println("Searchers stats")
    println("===================================")
    val searchersOrdered = data.keys.toList()
    val header = "Round #\t" + searchersOrdered.map { it.toId() }.joinToString("\t") + "\n"
    val total = PEER_COUNT * SUBNET_SHARE_PCT / 100
    var index = 0
    var stats = ""
    while (true) {
        var dataFound = false
        var line = (index + 1).toString()
        searchersOrdered.forEach { searcher ->
            line += "\t"
            val searcherData = data[searcher] ?: error("Not expected")
            if (index < searcherData.size) {
                line += "%.2f".format(searcherData[index] * 100.0 / total) + "%"
                dataFound = true
            } else {
                line += " "
            }
        }
        if (!dataFound) {
            break
        }
        stats += line + "\n"
        ++index
    }
    println((header + stats).formatTable(true))
}