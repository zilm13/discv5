package org.ethereum.discv5.core.task

import org.ethereum.discv5.core.Enr

class ImmediateProducer(private val result: List<Enr>) : ProducerTask<List<Enr>> {
    override fun step() {
        // do nothing
    }

    override fun isOver(): Boolean {
        return true
    }

    override fun getResult(): List<Enr> {
        return result
    }
}