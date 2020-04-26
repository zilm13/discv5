package org.ethereum.discv5.core.task

class ParallelProducerTask<R>(private val subTasks: List<ProducerTask<R>>) : ProducerTask<List<R>>,
    ParallelTask(subTasks) {
    override fun getResult(): List<R> {
        if (!isOver()) {
            error("Task is not over. Query result when the task is over!")
        }

        return subTasks.map { it.getResult() }.toList()
    }
}