package org.ethereum.discv5.util

interface MessageSizeEstimator {
    fun estimate(input: Int = 0): Int

    companion object {
        private val findNodesSizeEstimator = FindNodesSizeEstimator()
        private val findNodesDownSizeEstimator = FindNodesDownSizeEstimator()
        private val findNeighborhoodSizeEstimator = FindNeighborhoodSizeEstimator()
        private val nodesSizeEstimator = NodesSizeEstimator()
        fun getFindNodesSize(): Int {
            return findNodesSizeEstimator.estimate()
        }

        fun getFindNodesDownSize(): Int {
            return findNodesDownSizeEstimator.estimate()
        }

        fun getNeighborhoodSize(): Int {
            return findNeighborhoodSizeEstimator.estimate()
        }

        fun getNodesSize(nodesCount: Int): List<Int> {
            // Maximum 4 nodes per message
            return (1..nodesCount).chunked(4).map { nodesSizeEstimator.estimate(it.size) }
        }
    }
}

class FindNodesSizeEstimator : MessageSizeEstimator {
    override fun estimate(input: Int): Int {
        return 73
    }
}

class FindNodesDownSizeEstimator : MessageSizeEstimator {
    override fun estimate(input: Int): Int {
        return 73
    }
}

class FindNeighborhoodSizeEstimator : MessageSizeEstimator {
    override fun estimate(input: Int): Int {
        return 105
    }
}

class NodesSizeEstimator : MessageSizeEstimator {
    override fun estimate(input: Int): Int {
        return 74 + input * 168
    }

}