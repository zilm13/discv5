package org.ethereum.discv5.util

/**
 * Estimates size of network message of appropriate type.
 */
interface MessageSizeEstimator {
    fun estimate(input: Int = 0): Int

    companion object {
        private val findNodesSizeEstimator = FindNodesSizeEstimator()
        private val nodesSizeEstimator = NodesSizeEstimator()

        fun getFindNodesSize(count: Int): Int {
            return findNodesSizeEstimator.estimate(count)
        }

        fun getNodesSize(nodesCount: Int): List<Int> {
            // Maximum 4 nodes per message
            return (1..nodesCount).chunked(4).map { nodesSizeEstimator.estimate(it.size) }
        }
    }
}

/**
 * Estimates FindNode size. Value is taken from implementation
 */
class FindNodesSizeEstimator : MessageSizeEstimator {
    override fun estimate(input: Int): Int {
        return 72 + input
    }
}

/**
 * Estimates one NODES message size. Value is taken from implementation and is average.
 */
class NodesSizeEstimator : MessageSizeEstimator {
    override fun estimate(input: Int): Int {
        return 74 + input * 168
    }

}