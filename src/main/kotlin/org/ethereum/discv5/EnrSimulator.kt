package org.ethereum.discv5

import io.libp2p.core.PeerId
import org.ethereum.discv5.core.MetaKey
import org.ethereum.discv5.core.Node
import org.ethereum.discv5.util.formatTable
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

/**
 * Simulator which uses ENR attribute fields for advertisement
 */
class EnrSimulator {
    fun runEnrUpdateSimulation(peers: List<Node>, rounds: Int): List<Pair<Int, Int>> {
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
                    put(MetaKey.SUBNET.of, SUBNET_13)
                }
            )
        }

        val subnetIds = peers.filter { it.enr.seq == BigInteger.valueOf(2) }.map { it.enr.id }.toSet()
        assert(subnetIds.isNotEmpty())
        println("Total subnet peers count: ${subnetIds.size}")
        val enrStats = ArrayList<Pair<Int, Int>>()
        enrStats.add(calcEnrSubnetPeersStats(peers, subnetIds))
        for (i in 51 until (51 + TIMEOUT_STEP)) {
            println("Simulating round #${i + 1}")
            peers.forEach(Node::step)
            enrStats.add(calcEnrSubnetPeersStats(peers, subnetIds))
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