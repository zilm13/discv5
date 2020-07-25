package org.ethereum.discv5.util.filter

import io.libp2p.etc.types.toBytesBigEndian
import org.apache.tuweni.bytes.Bytes
import org.ethereum.discv5.util.Bitlist
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicInteger

class State(val level: Int, val index: Int)
class Node(val exists: Boolean, val low: Int, val high: Int, val level: Int, val index: Int) {
    constructor(exists: Int, low: Int, high: Int, level: Int, index: Int) : this(exists == 1, low, high, level, index)
    constructor(
        exists: Boolean,
        low: Int,
        high: Int,
        level: Int,
        index: Int,
        parentIndex: Int,
        clockValue: Int
    ) : this(exists, low, high, level, index) {
        this.parentIndex = parentIndex
        this.clockValue = clockValue
    }

    var parentIndex: Int? = null
    var clockValue: Int? = null

    override fun toString(): String {
        return "Node(exists=$exists, low=$low, high=$high, level=$level, index=$index, parentIndex=$parentIndex, clockValue=$clockValue)"
    }
}

/**
 * Adaptive Range Filter
 * Tree structure similar to Bloom Filter but for range queries
 * effectively stores ranges with boolean flag
 * insert(3, 6, false) => query (4, 6) = false; query(7, 9) = true
 *
 * Some notes:
 * - As false-positives are allowed, all domain range is true by default [0, domain]
 * - Only unsigned integers are allowed
 *
 * For more information about Adaptive Range Filter check "Adaptive Range Filters for Cold Data:Avoiding Trips to Siberia"
 * by Karolina Alexiou, Donald Kossmann, Per-Ake Larson
 * <a href="http://www.vldb.org/pvldb/vol6/p1714-kossmann.pdf">http://www.vldb.org/pvldb/vol6/p1714-kossmann.pdf</a>
 */
class AdaptiveRangeFilter(
    private val domain: Int,
    private val maxSize: Int = Int.MAX_VALUE,
    private val shape: MutableList<MutableMap<Int, Boolean>> = ArrayList(),
    private val leaves: MutableList<MutableMap<Int, Boolean>> = ArrayList()
) {
    init {
        if (shape.isEmpty()) { // if started from the blank
            grow()
            insertValue(0, 0, false)
            insertValue(0, 1, false)
            insertLeafValue(0, 0, true)
            insertLeafValue(0, 1, true)
        } else { // Adjust to maxSize if needed
            shrunkToMaxSize()
        }
    }

    fun serialize(): List<ByteArray> {
        val res = ArrayList<ByteArray>()
        res.add(domain.toBytesBigEndian())

        val lastLevel = shape.size - 1
        val shapeBits =
            Bitlist(calculateShapeSerializationBitSize(), Long.MAX_VALUE)
        val shapeBitCounter = AtomicInteger(0)
        // parse 0 level
        serializeShapeNode(-1, 0, shapeBits, shapeBitCounter)
        // parse other levels
        for ((levelIndex, level) in shape.withIndex()) {
            if (levelIndex == lastLevel) {
                break
            }
            for (index in level.keys.sorted()) {
                if (!level[index]!!) {
                    continue // skip leafs
                }
                serializeShapeNode(levelIndex, index, shapeBits, shapeBitCounter)
            }
        }
        res.add(shapeBits.serialize().toArray())

        val leavesBits =
            Bitlist(calculateLeavesSerializationBitSize(), Long.MAX_VALUE)
        var leavesBitCounter = 0
        for (level in leaves) {
            for ((_, value) in level) {
                if (value) {
                    leavesBits.setBit(leavesBitCounter)
                }
                leavesBitCounter++
            }
        }
        // TODO: merge shape and leaves serialization in one byte array
        res.add(leavesBits.serialize().toArray())

        return res
    }

    private fun serializeShapeNode(
        level: Int,
        index: Int,
        shapeBits: Bitlist,
        shapeBitCounter: AtomicInteger
    ) {
        // check children
        val leftChild = shape[level + 1][index * 2]!!
        val rightChild = shape[level + 1][index * 2 + 1]!!
        if (leftChild) {
            if (rightChild) {
                shapeBits.setBit(shapeBitCounter.getAndIncrement())
                shapeBits.setBit(shapeBitCounter.getAndIncrement())
            } else {
                shapeBits.setBit(shapeBitCounter.getAndIncrement())
                shapeBitCounter.incrementAndGet()
            }
        } else {
            if (rightChild) {
                shapeBitCounter.incrementAndGet()
                shapeBits.setBit(shapeBitCounter.getAndIncrement())
            } else {
                shapeBitCounter.incrementAndGet()
            }
        }
    }

    private fun calculateShapeSerializationBitSize(): Int {
        return shape.map { level ->
            level.values.count()
        }
            .sum()
    }

    private fun calculateLeavesSerializationBitSize(): Int {
        return leaves.map { level ->
            level.values.count()
        }
            .sum()
    }

    fun estimateSerializedSize(): Int {
        var size = 0
        size += 32 // low
        size += 32 // domain
        size += shape.map { level ->
            level.values.sumBy { if (it) 1 else 2 }
        } // true = node, false = leaf, extra bit for value
            .sum()

        return size / Byte.SIZE_BITS
    }

    private fun grow() {
        shape.add(HashMap());
        leaves.add(HashMap());
    }

    fun query(point: Int): Boolean {
        return pointQuery(point)
    }

    fun query(query: Pair<Int, Int>): Boolean {
        return if (query.first == query.second) {
            pointQuery(query.first)
        } else {
            rangeQuery(query)
        };
    }

    fun insert(range: Pair<Int, Int>, value: Boolean) {
        val low = range.first
        val high = range.second
        if (high < low) {
            error("Range should be a point or correct low to high range")
        }
        if (low < 0) {
            error("Points should be in positive range")
        }
        val hist: MutableList<State> = ArrayList()
        var currentLevel = 0
        var currentIndex = 0
        while (true) {
            val navigateLow = navigate(low, currentLevel, currentIndex, hist, true)
            currentLevel = navigateLow.level
            currentIndex = navigateLow.index
            if (navigateLow.low < low || navigateLow.high > high) {
                expandLeafNode(currentLevel, currentIndex, navigateLow.exists)
            } else {
                break
            }
        }
        val finalNode = navigate(low, currentLevel, currentIndex, hist, true)
        setLeafValue(finalNode.level, finalNode.index, value)

        // trying to collapse smth
        collapse(finalNode.level, finalNode.index)
        if (finalNode.high < high) {
            insert(Pair(finalNode.high + 1, high), value)
        }

        // optimize if tree is too big
        shrunkToMaxSize()
    }

    /**
     * Shrunk tree with a stupid-most algo:
     * 1) find top left-most node
     * 2) mark it and its sibling as true as we are ok with false-positives
     * 3) try to collapse smth in tree
     * 4) check if size is less than maxSize. If not, start from (1)
     */
    private fun shrunkToMaxSize() {
        while (estimateSerializedSize() > maxSize) {
            val topLevel = shape.size - 1
            val leftMostIndex = shape[topLevel].keys.min()!!
            setLeafValue(topLevel, leftMostIndex, true)
            setLeafValue(topLevel, leftMostIndex + 1, true)
            collapse(topLevel, leftMostIndex)
        }
    }

    /**
     * Trying to collapse tree from node and upwards
     */
    private fun collapse(level: Int, index: Int) {
        if (level == 0) {
            return
        }
        var currentIndex = index
        if (currentIndex % 2 == 1) {
            currentIndex--
        }
        if (shape[level][currentIndex] != null && shape[level][currentIndex + 1] != null
            && shape[level][currentIndex] == false && shape[level][currentIndex + 1] == false
        ) {
            if (leaves[level][currentIndex] == leaves[level][currentIndex + 1]) { // collapse
                shape[level - 1][currentIndex / 2] = false
                leaves[level - 1][currentIndex / 2] = leaves[level][currentIndex]!!
                shape[level].remove(currentIndex)
                shape[level].remove(currentIndex + 1)
                leaves[level].remove(currentIndex)
                leaves[level].remove(currentIndex + 1)
                if (isLastLevel(level) && shape[level].isEmpty()) {
                    removeLast(shape)
                    removeLast(leaves)
                }
                collapse(level - 1, currentIndex / 2)
            }
        }
    }

    private fun expandLeafNode(level: Int, index: Int, value: Boolean) {
        // TODO: check exists
        shape[level][index] = true
        leaves[level].remove(index)
        insertValue(level + 1, index * 2, false)
        insertValue(level + 1, index * 2 + 1, false)
        insertLeafValue(level + 1, index * 2, value)
        insertLeafValue(level + 1, index * 2 + 1, value)
    }

    private fun insertValue(level: Int, index: Int, value: Boolean) {
        if ((shape.size - 1) < level) {
            grow()
        }
        shape[level][index] = value
    }

    private fun setLeafValue(level: Int, index: Int, value: Boolean) {
        leaves[level][index] = value
    }

    private fun insertLeafValue(level: Int, index: Int, value: Boolean) {
        if ((leaves.size - 1) < level) {
            grow()
        }
        setLeafValue(level, index, value)
    }

    private fun isLeaf(level: Int, index: Int): Boolean {
        return if (level < shape.size) {
            false == shape[level][index]
        } else {
            false
        }
    }

    private fun isLastLevel(level: Int): Boolean {
        return shape.size == (level + 1)
    }

    private fun treeBitSize(): Int {
        var sum = 0
        for (i in 0 until shape.size) {
            sum += shape[i].size
        }
        for (i in 0 until leaves.size) {
            sum += leaves[i].size
        }

        return sum
    }

    private fun getLeaf(level: Int, index: Int): Boolean {
        return leaves[level][index]!!
    }

    private fun rangeQuery(query: Pair<Int, Int>): Boolean {
        val low = query.first
        val high = query.second
        val hist: MutableList<State> = ArrayList()
        var res = navigate(low, 0, 0, hist, true)
        var exists: Boolean = res.exists
        while (!exists && res.high < high) {
            val state: State = getRestartPoint(res.high + 1, hist)
            res = navigate(res.high + 1, state.level, state.index, hist, true)
            exists = res.exists
        }

        return exists
    }

    private fun getRestartPoint(bound: Int, history: MutableList<State>): State {
        var state = State(0, 0)
        while (history.size > 0) { //find where to re-start the search
            state = history.last()
            val range = getRange(state.level, state.index);
            if (range.second >= bound) { //there is stuff to be found here
                removeLast(history)
                break;
            } else {
                removeLast(history)
            }
        }
        return state
    }

    private fun <T> removeLast(list: MutableList<T>) {
        if (list.isNotEmpty()) {
            list.removeAt(list.size - 1)
        }
    }

    internal fun navigate(
        key: Int,
        level: Int,
        index: Int,
        history: MutableList<State>,
        useHistory: Boolean
    ): Node {
        //we are at level level, looking at index, index + 1, the children
        var actualIndex = index
        if (index % 2 == 1) {
            actualIndex-- // if we are on the right child, start from the left
        }
        val range = getRange(level, actualIndex);
        val low = range.first
        val high = range.second
        val middle = lowMiddle(low, high);
        val nextLevel = level + 1;
        var nextIndex = getLeftChildIndex(level, actualIndex);

        if (key <= middle) {
            if (isLeaf(level, actualIndex)) {
                return Node(getLeaf(level, actualIndex), low, middle, level, actualIndex)
            }
        } else {  //go to the right child
            if (isLeaf(level, actualIndex + 1)) {
                return Node(getLeaf(level, actualIndex + 1), middle + 1, high, level, actualIndex + 1)
            }
            nextIndex += 2
        }

        return navigate(key, nextLevel, nextIndex, history, useHistory);
    }

    private fun getLeftChildIndex(parentLevel: Int, parentIndex: Int): Int {
        return parentIndex * 2
    }

    private fun lowMiddle(low: Int, high: Int): Int {
        return low + ((high - low) shr 1)
    }

    /**
     * returns the range of values covered
     * by the nodes [index, index + 1] in level `level`
     */
    private fun getRange(level: Int, index: Int): Pair<Int, Int> {
        val indexS = index shr 1
        val step = (domain + 1) shr level
        return Pair(indexS * step, (indexS + 1) * step - 1)
    }

    /**
     * simulates execution of range query on Synopsis
     * (may result false positive; must not result in false negative)
     */
    private fun pointQuery(key: Int): Boolean {
        return navigate(key, 0, 0, ArrayList(), true).exists
    }

    fun prettyPrint() {
        println("TRIE (size = ${treeBitSize()})")
        val positions = listOf(0, 1)
        prettyPrint(0, positions)
    }

    private fun prettyPrint(level: Int, positions: List<Int>) {
        //we should not rely on the lenght of the shape/leaf vector
        //though, we should take care not to create extra levels by checking before doing the vector.append...
        val spaces = 1 shl (shape.size - level)
        val sep = " "
        var half = ""
        for (i in 0 until ((spaces shr 1) - 1)) {
            half += sep
        }

        //print current level (without leaves for the time being)
        var previous = -1;
        for (i in 0 until positions.size) {
            val position = positions[i]
            if (previous < position - 1) {
                for (j in previous until (position - 1)) {
                    print(half + sep + sep + half);
                }
            }
            var bool = "1";
            if (isLeaf(level, i)) {
                bool = "0"
            }
            print(half + sep + bool + half)
            previous = position;
        }
        println()
        //find the new positions :)
        val newPositions = ArrayList<Int>()
        for (i in 0 until shape[level].size) {
            if (!isLeaf(level, i)) {
                newPositions.add(2 * positions[i])
                newPositions.add((2 * positions[i]) + 1)
            }
        }

        if (level < shape.size - 1) {
            prettyPrint(level + 1, newPositions);
        }
    }

    companion object {
        fun deserialize(data: List<ByteArray>, maxSize: Int = Int.MAX_VALUE): AdaptiveRangeFilter {
            val domain = BigInteger(data[0]).toInt()

            // shape
            var currentLevel = 0
            var nextLevelElements = 0
            var currentShapeBitIndex = 0
            val shapeBits = Bitlist.fromBytes(Bytes.wrap(data[1]), Long.MAX_VALUE)
            val shape = ArrayList<MutableMap<Int, Boolean>>()
            var indexList = listOf(0, 1)
            var nextIndexList = ArrayList<Int>()
            while (currentShapeBitIndex < shapeBits.currentSize) {
                if (indexList.isEmpty()) {
                    break // we have 00's in the end
                }
                shape.add(HashMap())
                for (index in indexList) {
                    shape[currentLevel][index] = shapeBits.getBit(currentShapeBitIndex)
                    if (shape[currentLevel][index]!!) {
                        nextLevelElements += 2
                        nextIndexList.add(index * 2)
                        nextIndexList.add(index * 2 + 1)
                    }
                    currentShapeBitIndex++
                }
                indexList = nextIndexList
                nextIndexList = ArrayList()
                currentLevel++
            }

            // leaves
            val leaves = ArrayList<MutableMap<Int, Boolean>>()
            val leavesBits = Bitlist.fromBytes(Bytes.wrap(data[2]), Long.MAX_VALUE)
            var currentBitIndex = 0
            for (level in shape) {
                val levelMap = HashMap<Int, Boolean>()
                leaves.add(levelMap)
                for ((index, value) in level) {
                    if (!value) { // Leaf!
                        levelMap[index] = leavesBits.getBit(currentBitIndex)
                        currentBitIndex++
                    }
                }
            }

            return AdaptiveRangeFilter(domain, maxSize, shape, leaves)
        }
    }
}