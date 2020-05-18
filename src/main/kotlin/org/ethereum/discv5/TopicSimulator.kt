package org.ethereum.discv5

import io.libp2p.core.PeerId
import org.ethereum.discv5.core.AD_LIFE_STEPS
import org.ethereum.discv5.core.K_BUCKET
import org.ethereum.discv5.core.Node
import org.ethereum.discv5.core.Router
import org.ethereum.discv5.util.RoundCounter
import org.ethereum.discv5.util.calcTraffic
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

val TOPIC_SUCCESSFUL_SHARE_PCT = 99  // Topic considered advertised for group of peers when XX% of them placed ads
val TOPIC_SUCCESSFUL_MEDIAS = 5 // Require at least XX media for each advertiser for successful topic placement

/**
 * Simulation which uses Discovery V5 TOPIC advertisements
 */
class TopicSimulator {
    fun runTopicAdSimulationUntilSuccessfulPlacement(
        peers: List<Node>,
        rounds: RoundCounter,
        router: Router
    ): List<Pair<Int, Int>> {
        peers.forEach(Node::initTasks)
        val subnetPeers = peers.shuffled(RANDOM).take((peers.size * SUBNET_SHARE_PCT / 100.0).roundToInt())
        println("Making $SUBNET_SHARE_PCT% of peers (${subnetPeers.size}) advertise their subnet")
        val subnetPeersAdvertised = HashMap<PeerId, MutableSet<PeerId>>()
        val currentRound = AtomicInteger(0)
        val stepsSpent = AtomicInteger(0)
        val successfulAds = AtomicInteger(0)
        val media = HashSet<PeerId>()

        // TODO: Comment to skip register topic
        registerTopicSubtaskUntilSuccess(
            rounds.peekNext(),
            currentRound,
            subnetPeers,
            media,
            stepsSpent,
            successfulAds,
            subnetPeersAdvertised
        )
        while (rounds.hasNext()) {
            val registeredSuccessfully = subnetPeersAdvertised.filter { it.value.size >= TOPIC_SUCCESSFUL_MEDIAS }.size
            if (registeredSuccessfully >= (subnetPeers.size * TOPIC_SUCCESSFUL_SHARE_PCT / 100.0)) {
                println("For ${subnetPeers.size} subnet peers $registeredSuccessfully have more than $TOPIC_SUCCESSFUL_MEDIAS ad placements")
                break
            }
            val current = rounds.next()
            currentRound.set(current)
            println("Simulating round #${current + 1}")
            peers.forEach(Node::step)
        }

        // TODO: Uncomment to fill media with set of predefined Base58 values, see below, how to extract during advertisement stage
//    "BFNyGxKNZTbzT5UpxoqWRAstefXtF7VAFCAu5hBs16kW,AubFaWkWGsFZvL6SKtURipbvk5ucJcedUSX3BBzmrb2K,BSSpLZrRfENjpN3neEV1LsJwqv5kqvbEX4ocF9gLcGJP,BJApgtzegeFGjzo2ayy5QugzrpXxmPwtSZkzJJARBBx2,AnVqoaBJo6X5V5b28tw3ZF31L99K7C9hiCfcqfieQtmY,BLwFJcsdbTD8nDXdBougPy6Yt2GCw1TKpjBUkQfF5yNw,BKacpPCMy4z8U6LsMtHbkUD8vWryEARFUaMGYKRKxDKv,BN9QhVxbLfSmkRqMwrgMaGRN3C8MXR8WhRifcsKf5ZdC,Bbi3XLttpKLjVoTFinYwfXLQRF5D8aBDdvUVixAhKxAR,BDa65Sw5h4ZDKJ32pQeA7pRmXx6uynUhdLANCfYNPxwB,ApLdRuBN5Tss1BmxQQYPh6WZVMYnJdicFGFt4Wo6XR5t,BayS9LMm72BvnjUyb7h4LBCwCPWvQz8mArZMvq7ZdM1b,Ak9J1LjBkwjsZMFqHNjqwqJnRiHG3ruSBDycqSXVeG9m,BbeACZZM3xhNWH3K62PxuXfhFyZdZHGWAaoSDBZvRDGg,BDJWdknubo3FXgCvqav7FUHWPU7R1MhgEXQt5isEM2zR,AtGyqwCjR7NoansShbZ3jPg5kx4DHx2vBjf7wKJhanoN,AtJu8GQCTBDu8uNWdSXQgAzZD1bSKiWfrGy6pDAb3zKF,AuB8P598Kn5U8Rn5ktZo6EuKjLhVKf5A8ANxVbBbVqYq,BCPTt1uiBfahhZckurGkBhmApHaSFxDajjysp3MspeUk,Aoq5t1tizwKuebRD8HksyV337x6muRjsKy448B75MyLN,BYBSV2ZjXWGuP1vbkFA5CKQQCRkN4UMMpNregCyYYZM6,BP7waBKH5GiyB62JrMCzDtk6v7d65a9KKgM4YXuhRuVa,AwtHmTB6B9FtPXP4je7rxPPsUuaMDqGWF95c2KSjbLxm,BaFYWyNjvPZ9RBmwgZJkBqpmNFwHBrxpZ1pbHWHpMsAZ,BGnExcJvFdwz2jLJHoyhzk59JsZrZQUuuBRTFNHNm3GW,BkZEMoV5Q6LscsNTQkLuwRV4BWUkUbUATc6kBPcjYrrd,BZyC9JoSoFzKoMfDKXVicssK2Y9CnqRMLqp26tpWdLak,BKmCYUVJq4X1o6LhMJVPzXqsDpcRdbc4MsJC5GR5Twwx,BYFEpYzCxAH8tqbETcfLc9j99bGntoVgwVVfWEADAZ4b,BGRyNoHdGVTeNDC5A1dS6gwtPhpsc6mtvzTeFaJqbBXU,BfPKkxS1CHaPZ78iwzkFZnnF8eRLRbmiw8pbY2MMTheg,BG51XR3XP5bLrbDg95Xy99fHmtbZb3ta4RMdb3K16oVv,B3cAmXTHEkTD4crPm87Lnmj5RQeyKVMWzrzuHfj9KBRD,BABQ2cm9Wu2qR2AwL1KJGMUW35nT1DP9twtW9SUcQHzv,BSaTEu4WjwCUHh5bXKcxrRdaQkwpi2YgbF3fQKdTaZyp,BiAXib1VXp1kFKABnzp5XajdaxvezmBaT89awSrtTC6Y,BYAeeHNn5wYdiguaP8mNi9guriwGwJu3mkqhrz6EK4pj,B5KATaEpLuRiGz6workVK4hTcdhnmNTw1GvEqqxmEVS1"
//        .split(",").map{PeerId.fromBase58(it)}.forEach { media.add(it) }

        println("For ${successfulAds.get() * AD_LIFE_STEPS} steps of advertisements there were spent ${stepsSpent.get()} steps by advertisers")
        println("Total traffic for all ${peers.size} nodes: ${peers.map { calcTraffic(it) }.sum()}")
        println("Total traffic for ${subnetPeers.size} advertised nodes: ${subnetPeers.map { calcTraffic(it) }.sum()}")
        println("Total traffic for ${media.size} media nodes: ${media.mapNotNull { router.resolve(it) }
            .map { calcTraffic(it) }.sum()}")

        // TODO: Uncomment to print media Ids for subsequent measurement of traffic without advertisement
//    println("Media ids: ${media.map { it.toString() }.joinToString(",")}")

        return listOf(Pair(0, 0))
    }

    fun runTopicSearch(
        peers: List<Node>,
        rounds: RoundCounter,
        searcherCount: Int,
        requireAds: Int
    ): List<Pair<Int, Int>> {
        val currentRound = AtomicInteger(0)
        val stepsSpent = AtomicInteger(0)
        println("Making $searcherCount peers find at least $requireAds topic ads each")
        val searchers = peers.shuffled(RANDOM).take(searcherCount)
        val remaining = AtomicInteger(searcherCount)

        // TODO: comment to skip search
        searchers.forEach {
            it.findTopic(SUBNET_13, K_BUCKET, requireAds) { set ->
                println("Peer ${it.enr.toId()} found ads: ${set.map { it.toString() }.joinToString(",")}")
                remaining.decrementAndGet()
            }
        }
        while (rounds.hasNext()) {
            val current = rounds.next()
            currentRound.set(current)
            println("Simulating round #${current + 1}")
            if (remaining.get() == 0) {
                println("$searcherCount peers found each $requireAds ads")
                break
            }
            peers.forEach(Node::step)
            stepsSpent.addAndGet(remaining.get())
        }

        println("For $searcherCount searchers there were spent ${stepsSpent.get()} steps by searchers")
        println("Total traffic for ${searchers.size} searchers: ${searchers.map { calcTraffic(it) }.sum()}")
        return listOf(Pair(0, 0))
    }

    private fun registerTopicSubtask(
        taskRound: Int,
        currentRound: AtomicInteger,
        subnetPeers: List<Node>,
        media: MutableSet<PeerId>,
        stepsSpent: AtomicInteger,
        successfulAds: AtomicInteger
    ) {
        println("Registering topic ads on round $taskRound")
        subnetPeers.forEach { node ->
            println("Registering topic ads on peer ${node.enr.toId()} on round ${currentRound.get()}")
            node.registerTopic(SUBNET_13, K_BUCKET, false) {
                stepsSpent.addAndGet(currentRound.get() - taskRound)
                successfulAds.addAndGet(it.filter { bool -> bool.second }.count())
                it.forEach { pair -> media.add(pair.first.id) }
                println(
                    "On round ${currentRound.get()} register topic task started at round $taskRound on ${node.enr.toId()} is finished with following results: ${it.joinToString(
                        ","
                    )}"
                )
            }
        }
    }

    private fun registerTopicSubtaskUntilSuccess(
        taskRound: Int,
        currentRound: AtomicInteger,
        subnetPeers: List<Node>,
        media: MutableSet<PeerId>,
        stepsSpent: AtomicInteger,
        successfulAds: AtomicInteger,
        subnetPeersAdvertised: HashMap<PeerId, MutableSet<PeerId>>
    ) {
        println("Registering topic ads on round $taskRound")
        subnetPeers.forEach { node ->
            println("Registering topic ads on peer ${node.enr.toId()} on round ${currentRound.get()}")
            node.registerTopic(SUBNET_13, K_BUCKET, false) {
                stepsSpent.addAndGet(currentRound.get() - taskRound)
                successfulAds.addAndGet(it.filter { pair -> pair.second }.count())
                it.forEach { pair -> media.add(pair.first.id) }
                val successfulMedias = it.filter { pair -> pair.second }.map { pair -> pair.first.id }.toMutableSet()
                if (subnetPeersAdvertised.contains(node.enr.id)) {
                    subnetPeersAdvertised[node.enr.id]?.addAll(successfulMedias)
                } else {
                    subnetPeersAdvertised[node.enr.id] = successfulMedias
                }
                println(
                    "On round ${currentRound.get()} register topic task started at round $taskRound on ${node.enr.toId()} is finished with following results: ${it.joinToString(
                        ","
                    )}"
                )
            }
        }
    }
}