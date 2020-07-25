package org.ethereum.discv5.util.filter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AdaptiveRangeFilterTests {

    @Test
    fun testInvalidRangeInsert() {
        val thrown: IllegalStateException = assertThrows {
            val filter = AdaptiveRangeFilter(1_000_000)
            filter.insert(Pair(6, 3), true)
        }

        assertTrue(thrown.message!!.contains("correct low to high range"));
    }

    @Test
    fun testSimpleRangeInsert() {
        val filter = AdaptiveRangeFilter(15)
        filter.insert(Pair(3, 6), true)
        filter.insert(Pair(0, 2), false)
        filter.prettyPrint()
        assertFalse(filter.query(Pair(0, 1)))
        assertTrue(filter.query(Pair(2, 3)))
        assertTrue(filter.query(Pair(5, 7)))
        assertFalse(filter.query(Pair(2, 2)))
    }

    @Test
    fun testTreeCollapse() {
        val filter = AdaptiveRangeFilter(15)
        filter.insert(Pair(3, 6), true)
        filter.insert(Pair(0, 2), false)
        filter.prettyPrint()
        filter.insert(Pair(0, 7), false) // should collapse left branch
        filter.prettyPrint()
        val leftNode = filter.navigate(0, 0, 0, ArrayList(), true)
        assertEquals(0, leftNode.low)
        assertEquals(7, leftNode.high)
        assertFalse(leftNode.exists)
    }

    @Test
    fun testOnePoint() {
        val filter = AdaptiveRangeFilter(15)
        filter.insert(Pair(0, 15), false)
        filter.insert(Pair(8, 8), true)
        filter.prettyPrint()
        assertFalse(filter.query(Pair(0, 7)))
        assertFalse(filter.query(Pair(9, 15)))
        assertTrue(filter.query(Pair(8, 8)))
    }

    @Test
    fun testSerialization() {
        val oldFilter = AdaptiveRangeFilter(15)
        oldFilter.insert(Pair(3, 6), true)
        oldFilter.insert(Pair(0, 2), false)
        oldFilter.prettyPrint()
        val filter = AdaptiveRangeFilter.deserialize(oldFilter.serialize())
        filter.prettyPrint()
        assertFalse(filter.query(Pair(0, 1)))
        assertTrue(filter.query(Pair(2, 3)))
        assertTrue(filter.query(Pair(5, 7)))
        assertFalse(filter.query(Pair(2, 2)))
    }

    @Test
    fun testSerialization2() {
        val oldFilter = AdaptiveRangeFilter(15)
        oldFilter.insert(Pair(3, 6), true)
        oldFilter.insert(Pair(0, 2), false)
        oldFilter.prettyPrint()
        oldFilter.insert(Pair(0, 7), false) // should collapse left branch
        oldFilter.prettyPrint()
        val filter = AdaptiveRangeFilter.deserialize(oldFilter.serialize())
        filter.prettyPrint()
        val leftNode = filter.navigate(0, 0, 0, ArrayList(), true)
        assertEquals(0, leftNode.low)
        assertEquals(7, leftNode.high)
        assertFalse(leftNode.exists)
    }

    @Test
    fun testRewrite() {
        val filter = AdaptiveRangeFilter(15)
        filter.insert(Pair(8, 9), false)
        assertFalse(filter.query(Pair(8, 9)))
        filter.insert(Pair(8, 9), true)
        assertTrue(filter.query(Pair(8, 9)))
    }

    @Test
    fun testSuperSet() {
        val filter = AdaptiveRangeFilter(15)
        filter.insert(Pair(3, 6), false)
        filter.insert(Pair(2, 8), false)
        assertFalse(filter.query(Pair(2, 2)))
        assertFalse(filter.query(Pair(3, 3)))
        assertFalse(filter.query(Pair(6, 6)))
        assertFalse(filter.query(Pair(8, 8)))
        assertFalse(filter.query(Pair(5, 5)))
    }

    @Test
    fun testSubSet() {
        val filter = AdaptiveRangeFilter(15)
        filter.insert(Pair(2, 8), false)
        filter.insert(Pair(3, 6), false)
        assertFalse(filter.query(Pair(2, 2)))
        assertFalse(filter.query(Pair(3, 3)))
        assertFalse(filter.query(Pair(6, 6)))
        assertFalse(filter.query(Pair(8, 8)))
        assertFalse(filter.query(Pair(5, 5)))
    }

    @Test
    fun testTwoInsertsNoOverlap() {
        val filter = AdaptiveRangeFilter(15)
        filter.insert(Pair(1, 2), false)
        filter.insert(Pair(3, 4), false)
        assertFalse(filter.query(Pair(2, 3)))
    }

    @Test
    fun testTwoInsertsNoOverlapReverse() {
        val filter = AdaptiveRangeFilter(15)
        filter.insert(Pair(3, 4), false)
        filter.insert(Pair(1, 2), false)
        assertFalse(filter.query(Pair(2, 3)))
    }

    @Test
    fun testTwoInsertsOverlap() {
        val filter = AdaptiveRangeFilter(15)
        filter.insert(Pair(3, 10), false)
        filter.insert(Pair(1, 7), false)
        assertFalse(filter.query(Pair(1, 1)))
        assertFalse(filter.query(Pair(10, 10)))
        assertFalse(filter.query(Pair(3, 3)))
        assertFalse(filter.query(Pair(7, 7)))
        assertFalse(filter.query(Pair(5, 5)))
    }

    @Test
    fun testThreeInsertsNoOverlap() {
        val filter = AdaptiveRangeFilter(15)
        filter.insert(Pair(1, 2), false)
        filter.insert(Pair(3, 4), false)
        filter.insert(Pair(5, 6), false)
        assertFalse(filter.query(Pair(1, 1)))
        assertFalse(filter.query(Pair(3, 3)))
        assertFalse(filter.query(Pair(4, 6)))
        assertTrue(filter.query(Pair(7, 7)))
        assertTrue(filter.query(Pair(10, 10)))
    }

    @Test
    fun testLargeRangeInsert() {
        val filter = AdaptiveRangeFilter(32767)
        filter.insert(Pair(1, 5), false)
        filter.insert(Pair(3, 100), false)
        filter.insert(Pair(75, 100), false)
        filter.insert(Pair(500, 1000), false)
        filter.insert(Pair(2000, 9000), false)
        assertFalse(filter.query(Pair(1, 99)))
        assertFalse(filter.query(Pair(600, 600)))
        assertFalse(filter.query(Pair(5000, 8000)))
        assertTrue(filter.query(Pair(900, 1100)))
        assertTrue(filter.query(Pair(0, 0)))
    }

    @Test
    fun testSizeLimit() {
        val filter = AdaptiveRangeFilter(32767, 25)
        filter.insert(Pair(1, 5), false)
        filter.insert(Pair(3, 100), false)
        filter.insert(Pair(75, 100), false)
        filter.insert(Pair(500, 1000), false)
        filter.insert(Pair(2000, 9000), false)
        filter.prettyPrint()
        assertTrue(filter.query(Pair(1, 1)))
        assertFalse(filter.query(Pair(2, 99)))
        assertFalse(filter.query(Pair(600, 600)))
        assertFalse(filter.query(Pair(5000, 8000)))
        assertTrue(filter.query(Pair(900, 1100)))
        assertTrue(filter.query(Pair(0, 0)))
    }
}
