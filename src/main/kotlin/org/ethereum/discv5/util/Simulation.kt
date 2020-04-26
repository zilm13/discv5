package org.ethereum.discv5.util

import org.ethereum.discv5.LATENCY_LEG_MS
import org.ethereum.discv5.LATENCY_MULTI_EACH_MS
import org.ethereum.discv5.core.BUCKETS_COUNT
import org.ethereum.discv5.core.KademliaTable
import org.ethereum.discv5.core.Node
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.pow
import kotlin.math.roundToInt

// * Various simulation setup utility functions *

fun calcTraffic(node: Node): Long {
    return node.incomingMessages.sum() + node.outgoingMessages.sum()
}

fun calcTotalTime(node: Node): Int {
    return node.roundtripLatency
        .filter { it.isNotEmpty() }
        .map { list -> LATENCY_LEG_MS + LATENCY_MULTI_EACH_MS * (list.size - 1) }
        .sum()
}

fun calcKademliaPeers(table: KademliaTable): Int {
    var res = 0
    for (i in BUCKETS_COUNT downTo 1) {
        res += table.find(i).size
    }
    return res
}

fun calcPeerDistribution(count: Int, alpha: Double, xm: Double, xFull: Double): List<Int> {
    val step = 10.0 / count
    val res: MutableList<Int> = ArrayList()
    val distribution = calcDistribution(step, alpha, xm, xFull)
    (0 until count).chunked((count * step).toInt()).forEachIndexed { index, chunk ->
        chunk.map { res.add((distribution[index] * count * 1000 / (xFull * count)).roundToInt()) }
    }
    return res
}

fun calcDistribution(step: Double, alpha: Double, xm: Double, xFull: Double): List<Double> {
    val reverseCdf: MutableList<Double> = ArrayList()
    var y = 0.0
    var xFullReached = false
    while (y < 1) {
        if (xFullReached) {
            reverseCdf.add(xFull)
        } else {
            val nextX = calcParetoCdfReversed(y, alpha, xm)
            if (nextX > xFull) {
                xFullReached = true
                reverseCdf.add(xFull)
            } else {
                reverseCdf.add(nextX)
            }

        }
        y += step
    }

    return reverseCdf
}

fun calcParetoCdfReversed(y: Double, alpha: Double, xm: Double): Double {
    return xm / (1 - y).pow(1 / alpha)
}

class RoundCounter(private val initSize: Int) {
    private val current = AtomicInteger(0)

    fun next(): Int {
        if (!hasNext()) {
            error("It's over!")
        }

        return current.getAndIncrement()
    }

    fun hasNext(): Boolean {
        return current.get() < initSize
    }

    fun remaining(): Int {
        return initSize - current.get()
    }
}