package org.ethereum.discv5.util

import java.security.Provider
import java.security.SecureRandom
import java.security.SecureRandomSpi
import java.util.Random
import java.util.stream.DoubleStream
import java.util.stream.IntStream
import java.util.stream.LongStream

/**
 * Bad hack of [SecureRandom] to get SecureRandom with deterministic results.
 * Use [setInsecureSeed] with some seed after creation of [InsecureRandom] to get usable instance
 */
class InsecureRandom : SecureRandom {
    constructor() : super()
    constructor(seed: ByteArray?) : super(seed)
    constructor(secureRandomSpi: SecureRandomSpi?, provider: Provider?) : super(secureRandomSpi, provider)

    private var delegate: Random? = null

    fun setInsecureSeed(seed: Long) {
        delegate = Random(seed)
    }

    private fun checkDelegate() {
        if (delegate == null) {
            throw RuntimeException("setInsecureSeed(Long) should be called before usage")
        }
    }

    override fun nextBoolean(): Boolean {
        checkDelegate()
        return delegate!!.nextBoolean()
    }

    override fun getAlgorithm(): String {
        throw RuntimeException("Not supported by insecure random")
    }

    override fun setSeed(seed: ByteArray?) {
        throw RuntimeException("Not supported by insecure random, use setInsecureSeed(Long) instead")
    }

    override fun setSeed(seed: Long) {
        if (seed == 0L) {
            super.setSeed(seed)
        } else {
            throw RuntimeException("Not supported by insecure random, use setInsecureSeed(Long) instead")
        }
    }

    override fun nextGaussian(): Double {
        checkDelegate()
        return delegate!!.nextGaussian()
    }

    override fun nextDouble(): Double {
        checkDelegate()
        return delegate!!.nextDouble()
    }

    override fun nextBytes(bytes: ByteArray?) {
        checkDelegate()
        delegate!!.nextBytes(bytes)
    }

    override fun nextInt(): Int {
        checkDelegate()
        return delegate!!.nextInt()
    }

    override fun nextInt(bound: Int): Int {
        checkDelegate()
        return delegate!!.nextInt(bound)
    }

    override fun ints(streamSize: Long): IntStream {
        checkDelegate()
        return delegate!!.ints(streamSize)
    }

    override fun ints(): IntStream {
        checkDelegate()
        return delegate!!.ints()
    }

    override fun ints(streamSize: Long, randomNumberOrigin: Int, randomNumberBound: Int): IntStream {
        checkDelegate()
        return delegate!!.ints(streamSize, randomNumberOrigin, randomNumberBound)
    }

    override fun ints(randomNumberOrigin: Int, randomNumberBound: Int): IntStream {
        checkDelegate()
        return delegate!!.ints(randomNumberOrigin, randomNumberBound)
    }

    override fun nextLong(): Long {
        checkDelegate()
        return delegate!!.nextLong()
    }

    override fun nextFloat(): Float {
        checkDelegate()
        return delegate!!.nextFloat()
    }

    override fun longs(streamSize: Long): LongStream {
        checkDelegate()
        return delegate!!.longs(streamSize)
    }

    override fun longs(): LongStream {
        checkDelegate()
        return delegate!!.longs()
    }

    override fun longs(streamSize: Long, randomNumberOrigin: Long, randomNumberBound: Long): LongStream {
        checkDelegate()
        return delegate!!.longs(streamSize, randomNumberOrigin, randomNumberBound)
    }

    override fun longs(randomNumberOrigin: Long, randomNumberBound: Long): LongStream {
        checkDelegate()
        return delegate!!.longs(randomNumberOrigin, randomNumberBound)
    }

    override fun doubles(streamSize: Long): DoubleStream {
        checkDelegate()
        return delegate!!.doubles(streamSize)
    }

    override fun doubles(): DoubleStream {
        checkDelegate()
        return delegate!!.doubles()
    }

    override fun doubles(streamSize: Long, randomNumberOrigin: Double, randomNumberBound: Double): DoubleStream {
        checkDelegate()
        return delegate!!.doubles(streamSize, randomNumberOrigin, randomNumberBound)
    }

    override fun doubles(randomNumberOrigin: Double, randomNumberBound: Double): DoubleStream {
        checkDelegate()
        return delegate!!.doubles(randomNumberOrigin, randomNumberBound)
    }

    override fun generateSeed(numBytes: Int): ByteArray {
        throw RuntimeException("Not supported by insecure random")
    }
}