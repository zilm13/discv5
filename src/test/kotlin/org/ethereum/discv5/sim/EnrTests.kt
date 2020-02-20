package org.ethereum.discv5.sim

import io.libp2p.core.PeerId
import io.libp2p.core.multiformats.Multiaddr
import org.ethereum.discv5.core.Enr
import org.ethereum.discv5.core.simTo
import org.ethereum.discv5.core.to
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Node distance [Enr.to] and its reduced version [Enr.simTo] tests
 */
class EnrTests {
    @Test
    fun testEnrTo() {
        val nodeId0 = PeerId.fromHex("0000000000000000000000000000000000000000000000000000000000000000")
        val nodeId1a = PeerId.fromHex("0000000000000000000000000000000000000000000000000000000000000001")
        val nodeId1b = PeerId.fromHex("1000000000000000000000000000000000000000000000000000000000000000")
        val nodeId1s = PeerId.fromHex("1111111111111111111111111111111111111111111111111111111111111111")
        val nodeId9s = PeerId.fromHex("9999999999999999999999999999999999999999999999999999999999999999")
        val nodeIdfs = PeerId.fromHex("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff")
        val some = Multiaddr("/ip4/127.0.0.1/tcp/1234")
        assertEquals(
            0, Enr(
                some,
                nodeId1a
            ).to(Enr(some, nodeId1a))
        )
        assertEquals(
            1, Enr(
                some,
                nodeId0
            ).to(Enr(some, nodeId1a))
        )
        assertEquals(
            253, Enr(
                some,
                nodeId0
            ).to(Enr(some, nodeId1b))
        )
        assertEquals(
            253, Enr(
                some,
                nodeId0
            ).to(Enr(some, nodeId1s))
        )
        assertEquals(
            256, Enr(
                some,
                nodeId0
            ).to(Enr(some, nodeId9s))
        )
        assertEquals(
            256, Enr(
                some,
                nodeId0
            ).to(Enr(some, nodeIdfs))
        )
        assertEquals(
            255, Enr(
                some,
                nodeId9s
            ).to(Enr(some, nodeIdfs))
        )
    }

    @Test
    fun testEnrSimTo() {
        val nodeId0 = PeerId.fromHex("0000000000000000000000000000000000000000000000000000000000000000")
        val nodeId1a = PeerId.fromHex("0000000000000000000000000000000000000000000000000000000000000001")
        val nodeId1b = PeerId.fromHex("1000000000000000000000000000000000000000000000000000000000000000")
        val nodeId1s = PeerId.fromHex("1111111111111111111111111111111111111111111111111111111111111111")
        val nodeId9s = PeerId.fromHex("9999999999999999999999999999999999999999999999999999999999999999")
        val nodeIdfs = PeerId.fromHex("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff")
        val some = Multiaddr("/ip4/127.0.0.1/tcp/1234")
        val divisor: Int = 8
        assertEquals(
            0, Enr(
                some,
                nodeId1a
            ).simTo(Enr(some, nodeId1a), divisor)
        )
        assertEquals(
            1, Enr(
                some,
                nodeId0
            ).simTo(Enr(some, nodeId1a), divisor)
        )
        assertEquals(
            32, Enr(
                some,
                nodeId0
            ).simTo(Enr(some, nodeId1b), divisor)
        )
        assertEquals(
            32, Enr(
                some,
                nodeId0
            ).simTo(Enr(some, nodeId1s), divisor)
        )
        assertEquals(
            32, Enr(
                some,
                nodeId0
            ).simTo(Enr(some, nodeId9s), divisor)
        )
        assertEquals(
            32, Enr(
                some,
                nodeId0
            ).simTo(Enr(some, nodeIdfs), divisor)
        )
        assertEquals(
            32, Enr(
                some,
                nodeId9s
            ).simTo(Enr(some, nodeIdfs), divisor)
        )
    }
}