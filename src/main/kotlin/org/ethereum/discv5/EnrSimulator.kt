package org.ethereum.discv5

import io.libp2p.core.PeerId
import org.ethereum.discv5.core.MetaKey
import org.ethereum.discv5.core.Node
import org.ethereum.discv5.core.Router
import org.ethereum.discv5.util.RoundCounter
import org.ethereum.discv5.util.calcTraffic
import org.ethereum.discv5.util.formatTable
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

/**
 * Simulator which uses ENR attribute fields for advertisement
 */
// TODO: estimate traffic by delta with and without seq changes
class EnrSimulator {
    fun runEnrUpdateSimulationUntilDistributed(peers: List<Node>, rounds: RoundCounter): List<Pair<Int, Int>> {
        peers.forEach(Node::initTasks)
        println("Making $SUBNET_SHARE_PCT% of peers with ENR from subnet")
        peers.shuffled(RANDOM).take((peers.size * SUBNET_SHARE_PCT / 100.0).roundToInt()).forEach {
            it.updateEnr(
                it.enr.seq.inc(),
                HashMap<ByteArray, ByteArray>().apply {
                    put(MetaKey.SUBNET.of, SUBNET_13)
                }
            )
        }

        val subnetIds = peers.filter { it.enr.seq == BigInteger.valueOf(2) }.map { it.enr.id }.toSet()
        assert(subnetIds.isNotEmpty())
        println("Total subnet peers count: ${subnetIds.size}")
        val enrStats = ArrayList<Pair<Int, Int>>()
        enrStats.add(calcEnrSubnetPeersStats(peers, subnetIds))
        while (rounds.hasNext()) {
            val current = rounds.next()
            println("Simulating round #$current")
            peers.forEach(Node::step)
            enrStats.add(calcEnrSubnetPeersStats(peers, subnetIds))
            if (enrStats.last().second * 100.0 / (enrStats.last().first + enrStats.last().second) > TARGET_PERCENTILE) {
                break
            }
        }

        return enrStats
    }

    fun runEnrUpdateSimulationWTraffic(peers: List<Node>, rounds: RoundCounter, router: Router): List<Pair<Int, Int>> {
        peers.forEach(Node::initTasks)
        println("Making $SUBNET_SHARE_PCT% of peers with ENR from subnet")
        val subnetPeers = peers.shuffled(RANDOM).take((peers.size * SUBNET_SHARE_PCT / 100.0).roundToInt())
        // TODO: Comment if don't need to change subnet
        changeSubnetForPeers(subnetPeers)

        val subnetIds = subnetPeers.map { it.enr.id }.toSet()
        assert(subnetIds.isNotEmpty())
        println("Total subnet peers count: ${subnetIds.size}")
        while (rounds.hasNext()) {
            val current = rounds.next()
            println("Simulating round #$current")
            peers.forEach(Node::step)
        }

        println("Total traffic for ${subnetIds.size} advertised nodes: ${subnetIds.mapNotNull { router.resolve(it) }
            .map { node -> calcTraffic(node) }.sum()}")
        println("Total traffic for ${peers.size} all nodes: ${peers.map { calcTraffic(it) }.sum()}")

        return listOf(Pair(0, 0))
    }

    private fun changeSubnetForPeers(peers: List<Node>) {
        peers.forEach {
            it.updateEnr(
                it.enr.seq.inc(),
                HashMap<ByteArray, ByteArray>().apply {
                    put(MetaKey.SUBNET.of, SUBNET_13)
                }
            )
        }
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

    fun calcEnrSubnetPeersStats(peers: List<Node>, subnetIds: Set<PeerId>): Pair<Int, Int> {
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

    fun printSubnetPeersStats(enrStats: List<Pair<Int, Int>>) {
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
}