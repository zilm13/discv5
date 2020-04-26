package org.ethereum.discv5.core.task

interface ProducerTask<R> : Task {
    fun getResult(): R
}