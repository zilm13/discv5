package org.ethereum.discv5.core.task

open class ParallelTask(private val subTasks: List<Task>) : Task {
    override fun step() {
        subTasks.forEach { it.step() }
    }

    override fun isOver(): Boolean {
        return subTasks.all { it.isOver() }
    }
}