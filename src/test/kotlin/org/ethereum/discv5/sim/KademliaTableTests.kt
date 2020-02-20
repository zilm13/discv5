package org.ethereum.discv5.sim

import io.libp2p.core.PeerId
import io.libp2p.core.multiformats.Multiaddr
import org.ethereum.discv5.core.Enr
import org.ethereum.discv5.core.KademliaTable
import org.ethereum.discv5.core.Node
import org.ethereum.discv5.core.simTo
import org.ethereum.discv5.util.InsecureRandom
import org.ethereum.discv5.util.KeyUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

val RANDOM: InsecureRandom = InsecureRandom().apply { setInsecureSeed(1) }

val K_BUCKET = 16
val DISTANCE_DIVISOR = 1

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KademliaTableTests {
    lateinit var table: KademliaTable

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
            Node(enr, privKey)
        }
        table = KademliaTable(
            peers[0].enr,
            K_BUCKET,
            256,
            DISTANCE_DIVISOR,
            peers.subList(1, peers.size).map { it.enr }
        )
    }

    @Test
    fun testFindStrict() {
        assertEquals(K_BUCKET, table.findStrict(256).size)
        assertEquals(K_BUCKET, table.findStrict(255).size)
        assertEquals(10, table.findStrict(254).size)
        assertEquals(10, table.findStrict(253).size)
        assertEquals(3, table.findStrict(252).size)
        assertEquals(1, table.findStrict(251).size)
        assertEquals(2, table.findStrict(250).size)
        assertEquals(0, table.findStrict(249).size)
        assertEquals(1, table.findStrict(248).size)
        assertEquals(0, table.findStrict(247).size)
        assertEquals(0, table.findStrict(246).size)
        assertEquals(0, table.findStrict(245).size)
    }

    @Test
    fun testFindDown() {
        assertEquals(K_BUCKET, table.findDown(256).size)
        assertEquals(K_BUCKET, table.findDown(255).size)
        assertEquals(K_BUCKET, table.findDown(254).size)
        assertEquals(K_BUCKET, table.findDown(253).size)
        assertEquals(7, table.findDown(252).size)
        assertEquals(4, table.findDown(251).size)
        assertEquals(3, table.findDown(250).size)
        assertEquals(1, table.findDown(249).size)
        assertEquals(1, table.findDown(248).size)
        assertEquals(0, table.findDown(247).size)
    }

    @Test
    fun testFindNeighbors() {
        val peerIdMap: MutableMap<Int, PeerId> = HashMap()
        while ((245..256).subtract(peerIdMap.keys).isNotEmpty()) {
            val privKey = KeyUtils.genPrivKey(RANDOM)
            val peerId = PeerId(KeyUtils.privToPubCompressed(privKey))
            peerIdMap.putIfAbsent(peerId.simTo(table.home.id, DISTANCE_DIVISOR), peerId)
        }
        assertEquals(K_BUCKET, table.findNeighbors(peerIdMap[256]!!).size)
        assertEquals(K_BUCKET, table.findNeighbors(peerIdMap[255]!!).size)
        assertEquals(K_BUCKET, table.findNeighbors(peerIdMap[254]!!).size)
        assertEquals(K_BUCKET, table.findNeighbors(peerIdMap[253]!!).size)
        assertEquals(K_BUCKET, table.findNeighbors(peerIdMap[252]!!).size)
        assertEquals(K_BUCKET, table.findNeighbors(peerIdMap[251]!!).size)
        assertEquals(K_BUCKET, table.findNeighbors(peerIdMap[250]!!).size)
        assertEquals(K_BUCKET, table.findNeighbors(peerIdMap[249]!!).size)
        assertEquals(K_BUCKET, table.findNeighbors(peerIdMap[248]!!).size)
        assertEquals(K_BUCKET, table.findNeighbors(peerIdMap[247]!!).size)
        assertEquals(K_BUCKET, table.findNeighbors(peerIdMap[246]!!).size)
        assertEquals(K_BUCKET, table.findNeighbors(peerIdMap[245]!!).size)
    }
}