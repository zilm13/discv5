package org.ethereum.discv5.sim

import io.libp2p.core.PeerId
import io.libp2p.core.multiformats.Multiaddr
import org.ethereum.discv5.core.Enr
import org.ethereum.discv5.core.Node
import org.ethereum.discv5.core.simTo
import org.ethereum.discv5.util.KeyUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Similar to [KademliaTableTests] executing neighborhood lookup functions on deterministic set of nodes
 * but doing it on higher level of abstraction, on [Node] instances testing full lookup cycle
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NodeTests {
    private lateinit var node: Node
    private var otherNodeMap: MutableMap<Int, Node> = HashMap()

    /**
     * Generate 100 nodes, first is going to be `node`, others will be used to fill its Kademlia table.
     * After that generate set of peers, each on different distance from `node` covering distances from 245 to 256.
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
            Node(enr, privKey, RANDOM)
        }
        node = Node(peers[0].enr, peers[0].privKey, RANDOM)
        peers.subList(1, peers.size).map { it.enr }.forEach { node.table.put(it) }
        while ((245..256).subtract(otherNodeMap.keys).isNotEmpty()) {
            val privKey = KeyUtils.genPrivKey(RANDOM)
            val peerId = PeerId(KeyUtils.privToPubCompressed(privKey))
            val distance = peerId.simTo(node.table.home.id, DISTANCE_DIVISOR)
            otherNodeMap.computeIfAbsent(distance) {
                Node(
                    Enr(Multiaddr("/ip4/127.0.0.2/tcp/${distance - 1}"), peerId),
                    privKey,
                    RANDOM
                )
            }
        }

    }

    @Test
    fun testFindStrict() {
        testImpl() { node, otherNode -> node!!.findNodesStrict(otherNode) }
    }

    /**
     * We should have the same results for any @param findFunction implementation
     */
    fun testImpl(findFunction: (Node?, Node) -> List<Enr>) {
        val nodes256 = findFunction(otherNodeMap[256], node)
        assertEquals(256, node.enr.simTo(nodes256.first(), DISTANCE_DIVISOR))
        assertEquals(256, node.enr.simTo(nodes256.last(), DISTANCE_DIVISOR))
        assertEquals(K_BUCKET, nodes256.size)
        val nodes255 = findFunction(otherNodeMap[255], node)
        assertEquals(255, node.enr.simTo(nodes255.first(), DISTANCE_DIVISOR))
        assertEquals(255, node.enr.simTo(nodes255.last(), DISTANCE_DIVISOR))
        assertEquals(K_BUCKET, nodes255.size)
        val nodes254 = findFunction(otherNodeMap[254], node)
        assertEquals(254, node.enr.simTo(nodes254.first(), DISTANCE_DIVISOR))
        assertEquals(253, node.enr.simTo(nodes254.last(), DISTANCE_DIVISOR))
        assertEquals(K_BUCKET, nodes254.size)
        val nodes253 = findFunction(otherNodeMap[253], node)
        assertEquals(253, node.enr.simTo(nodes253.first(), DISTANCE_DIVISOR))
        assertEquals(254, node.enr.simTo(nodes253.last(), DISTANCE_DIVISOR))
        assertEquals(K_BUCKET, nodes253.size)
        val nodes252 = findFunction(otherNodeMap[252], node)
        assertEquals(252, node.enr.simTo(nodes252.first(), DISTANCE_DIVISOR))
        assertEquals(250, node.enr.simTo(nodes252.last(), DISTANCE_DIVISOR))
        assertEquals(K_BUCKET, nodes252.size)
        val nodes251 = findFunction(otherNodeMap[251], node)
        assertEquals(251, node.enr.simTo(nodes251.first(), DISTANCE_DIVISOR))
        assertEquals(253, node.enr.simTo(nodes251.last(), DISTANCE_DIVISOR))
        assertEquals(K_BUCKET, nodes251.size)
        val nodes250 = findFunction(otherNodeMap[250], node)
        assertEquals(250, node.enr.simTo(nodes250.first(), DISTANCE_DIVISOR))
        assertEquals(253, node.enr.simTo(nodes250.last(), DISTANCE_DIVISOR))
        assertEquals(K_BUCKET, nodes250.size)
        val nodes249 = findFunction(otherNodeMap[249], node)
        assertEquals(248, node.enr.simTo(nodes249.first(), DISTANCE_DIVISOR))
        assertEquals(253, node.enr.simTo(nodes249.last(), DISTANCE_DIVISOR))
        assertEquals(K_BUCKET, nodes249.size)
    }

    @Test
    fun testFindDown() {
        testImpl() { node, otherNode -> node!!.findNodesDown(otherNode) }
    }

    @Test
    fun testFindNeighbors() {
        testImpl() { node, otherNode -> node!!.findNeighbors(otherNode) }
    }
}