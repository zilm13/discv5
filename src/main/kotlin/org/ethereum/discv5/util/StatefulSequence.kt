package org.ethereum.discv5.util

import java.util.Queue

class StatefulSequence<T>(private val delegate: Sequence<T>) : Queue<T> {
    private val state = delegate.iterator()
    private var cache: T? = null

    override fun contains(element: T): Boolean {
        TODO("Not yet implemented")
    }

    override fun addAll(elements: Collection<T>): Boolean {
        TODO("Not yet implemented")
    }

    override fun clear() {
        TODO("Not yet implemented")
    }

    override fun element(): T {
        TODO("Not yet implemented")
    }

    override fun isEmpty(): Boolean {
        return cache == null && !state.hasNext()
    }

    override fun remove(): T {
        TODO("Not yet implemented")
    }

    override val size: Int
        get() = TODO("Not yet implemented")

    override fun containsAll(elements: Collection<T>): Boolean {
        TODO("Not yet implemented")
    }

    override fun iterator(): MutableIterator<T> {
        TODO("Not yet implemented")
    }

    override fun remove(element: T): Boolean {
        if (cache != null) {
            cache = null
        } else {
            state.next()
        }

        return true
    }

    override fun removeAll(elements: Collection<T>): Boolean {
        TODO("Not yet implemented")
    }

    override fun add(element: T): Boolean {
        TODO("Not yet implemented")
    }

    override fun offer(e: T): Boolean {
        TODO("Not yet implemented")
    }

    override fun retainAll(elements: Collection<T>): Boolean {
        TODO("Not yet implemented")
    }

    override fun peek(): T {
        if (cache == null) {
            cache = state.next()
        }
        return cache!!
    }

    override fun poll(): T {
        return if (cache != null) {
            val tmp = cache!!
            cache = null
            tmp
        } else {
            state.next()
        }
    }
}