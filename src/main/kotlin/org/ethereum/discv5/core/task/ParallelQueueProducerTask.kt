package org.ethereum.discv5.core.task

import java.util.Queue

class ParallelQueueProducerTask<R>(private val subTasks: Queue<ProducerTask<R>>, parallelism: Int) :
    ProducerTask<List<R>>,
    ParallelQueueTask(subTasks as Queue<Task>, parallelism) {
    private val tasksCopy = subTasks.toList()

    override fun getResult(): List<R> {
        if (!isOver()) {
            error("Task is not over. Query result when the task is over!")
        }

        return tasksCopy.map { it.getResult() }.toList()
    }
}