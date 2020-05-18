package org.ethereum.discv5

import io.libp2p.core.PeerId
import org.ethereum.discv5.core.MetaKey
import org.ethereum.discv5.core.Node
import org.ethereum.discv5.util.ByteArrayWrapper
import org.ethereum.discv5.util.RoundCounter
import org.ethereum.discv5.util.calcTraffic
import org.ethereum.discv5.util.formatTable
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

/**
 * Simulator which uses ENR attribute fields for advertisement
 */
class EnrSimulator {
    fun runEnrUpdateSimulationUntilDistributed(peers: List<Node>, rounds: RoundCounter): List<Pair<Int, Int>> {
        peers.forEach(Node::initTasks)
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

        // TODO: Uncomment to test Ping Shower
//        subnetNodes.forEach { it.firePingShower() }

        val subnetIds = subnetNodes.map { it.enr.id }.toSet()
        assert(subnetIds.isNotEmpty())
        println("Total subnet peers count: ${subnetIds.size}")
        val enrStats = ArrayList<Pair<Int, Int>>()
        enrStats.add(calcEnrSubnetPeersStats(peers, subnetIds))
        while (rounds.hasNext()) {
            val current = rounds.next()
            println("Simulating round #$current")
            peers.forEach(Node::step)
            enrStats.add(calcEnrSubnetPeersStats(peers, subnetIds))

            // TODO: uncomment to print traffic stats on each step
//            println("Cumulative traffic for ${subnetIds.size} advertised nodes: " +
//                    "${subnetNodes.map { node -> calcTraffic(node) }.sum()}"
//            )

            // TODO: comment to avoid quit on TARGET_PERCENTILE'% distribution reached
            if (enrStats.last().second * 100.0 / (enrStats.last().first + enrStats.last().second) > TARGET_PERCENTILE) {
                break
            }
        }

        println("Cumulative traffic for ${subnetIds.size} advertised nodes: ${subnetNodes.map { node -> calcTraffic(node) }
            .sum()}")
        println("Cumulative fresh ENR queries: ${subnetNodes.map { node -> node.enrQueries.filter { it == 2L }.count() }
            .sum()}")
        return enrStats
    }

    fun runEnrSubnetSearch(
        peers: List<Node>,
        rounds: RoundCounter,
        searcherCount: Int,
        requireAds: Int
    ): List<Pair<Int, Int>> {
        val currentRound = AtomicInteger(0)
        val stepsSpent = AtomicInteger(0)
        println("Making $searcherCount peers find at least $requireAds subnet peers each")
        val searchers = peers.shuffled(RANDOM).take(searcherCount)
        val remaining = AtomicInteger(searcherCount)

        // TODO: comment to skip search
        searchers.forEach {

            // TODO: replace standard search to use experimental
//            it.findEnrSubnetExperimental(SUBNET_13, requireAds) { set ->
            it.findEnrSubnet(SUBNET_13, requireAds) { set ->
                println("Peer ${it.enr.toId()} found subnet peers: ${set.map { it.toString() }.joinToString(",")}")
                remaining.decrementAndGet()
            }
        }
        while (rounds.hasNext()) {
            val current = rounds.next()
            currentRound.set(current)
            println("Simulating round #${current + 1}")
            if (remaining.get() == 0) {
                println("$searcherCount peers found each $requireAds subnet peers")
                break
            }
            peers.forEach(Node::step)
            stepsSpent.addAndGet(remaining.get())
        }

        println("For $searcherCount searchers there were spent ${stepsSpent.get()} steps by searchers")
        println("Total traffic for ${searchers.size} searchers: ${searchers.map { calcTraffic(it) }.sum()}")
        return listOf(Pair(0, 0))
    }

    private fun changeSubnetForPeers(peers: List<Node>) {
        peers.forEach {
            it.updateEnr(
                it.enr.seq.inc(),
                HashMap<ByteArrayWrapper, ByteArrayWrapper>().apply {
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

        // TODO: Uncomment to print share of sequence 2 peers on each step
//        println("%: ${secondCounter.get() * 100.0 / (firstCounter.get() + secondCounter.get())}")
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