package org.ethereum.discv5.sim

import io.libp2p.core.PeerId
import io.libp2p.core.multiformats.Multiaddr
import org.ethereum.discv5.core.Enr
import org.ethereum.discv5.core.KademliaTable
import org.ethereum.discv5.core.Node
import org.ethereum.discv5.core.Router
import org.ethereum.discv5.util.InsecureRandom
import org.ethereum.discv5.util.KeyUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

val RANDOM: InsecureRandom = InsecureRandom().apply { setInsecureSeed(1) }

val K_BUCKET = 16


/**
 * Executing neighborhood lookup functions on deterministic set of Kademlia tables
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KademliaTableTests {
    lateinit var table: KademliaTable

    /**
     * Generate 100 nodes, first is going to be home node,
     * others will be used to fill Kademlia `table` for this node.
     */
    @BeforeAll
    internal fun setup() {
        val peers = (0 until (100)).map {
            val ip = "127.0.0.1"
            val privKey = KeyUtils.genPrivKey(RANDOM)
            val addr = Multiaddr("/ip4/$ip/tcp/$it")
            val enr = Enr(
                addr,
                PeerId(KeyUtils.privToPubCompressed(privKey))
            )
            Node(enr, privKey, RANDOM, Router(RANDOM))
        }
        table = KademliaTable(
            peers[0].enr,
            K_BUCKET,
            256,
            { enr, cb -> cb(true) },
            peers.subList(1, peers.size).map { it.enr }
        )
    }

    @Test
    fun testFindStrict() {
        assertEquals(K_BUCKET, table.find(256).size)
        assertEquals(K_BUCKET, table.find(255).size)
        assertEquals(10, table.find(254).size)
        assertEquals(10, table.find(253).size)
        assertEquals(3, table.find(252).size)
        assertEquals(1, table.find(251).size)
        assertEquals(2, table.find(250).size)
        assertEquals(0, table.find(249).size)
        assertEquals(1, table.find(248).size)
        assertEquals(0, table.find(247).size)
        assertEquals(0, table.find(246).size)
        assertEquals(0, table.find(245).size)
    }

    @Test
    @Disabled("Not a test, comment to run")
    fun kademliaFillMeasure() {
        val home = Enr(
            Multiaddr("/ip4/127.0.1.1/tcp/0"),
            PeerId(KeyUtils.privToPubCompressed(KeyUtils.genPrivKey(RANDOM)))
        )
        val kademlia = KademliaTable(
            home,
            K_BUCKET,
            256,
            { enr, cb -> cb(true) },
            emptyList()
        )
        for (i in 0..2000000) {
//            if (i % 100000 == 0) {
            if (i % 10000 == 0) {
                println("For $i nodes inserted ${kademlia.findAll().size} kept in KademliaTable")
            }
            val privKey = KeyUtils.genPrivKey(RANDOM)
            val ip = "127.0.0.${(i / 50000) + 1}"
            val addr = Multiaddr("/ip4/$ip/tcp/${i % 50000}")
            val enr = Enr(
                addr,
                PeerId(KeyUtils.privToPubCompressed(privKey))
            )
            kademlia.put(enr) { _, cb -> cb(true) }
        }
    }
}