package org.ethereum.discv5.core.task

import java.util.Queue

open class ParallelQueueTask(private val subTasks: Queue<Task>, private val parallelism: Int) : Task {
    private val current = HashSet<Task>()

    override fun step() {
        if (isOver()) {
            return
        }
        while (current.size < parallelism && subTasks.isNotEmpty()) {
            current.add(subTasks.poll())
        }
        current.forEach { it.step() }
        current.removeAll { it.isOver() }
    }

    override fun isOver(): Boolean {
        return subTasks.isEmpty() && current.isEmpty()
    }
}
