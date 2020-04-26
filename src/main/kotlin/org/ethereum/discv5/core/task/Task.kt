package org.ethereum.discv5.core.task

interface Task {
    fun step(): Unit
    fun isOver(): Boolean {
        return false
    }
}