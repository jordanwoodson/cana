package io.github.samolego.canta.ops

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/** Android can run the immediate and delayed reconciliation jobs at the same time. */
internal class ReconcileWork(private val scope: CoroutineScope) {
    // Accessed on the service's Main dispatcher, including completion callbacks.
    private val jobs = mutableMapOf<Int, Job>()
    fun start(id: Int, execute: suspend () -> Unit, finished: () -> Unit) {
        stop(id)
        lateinit var job: Job
        job = scope.launch(start = CoroutineStart.LAZY) {
            try { execute() }
            finally {
                if (jobs[id] === job) {
                    jobs.remove(id)
                    if (!job.isCancelled) finished()
                }
            }
        }
        jobs[id] = job
        job.start()
    }
    fun stop(id: Int) { jobs.remove(id)?.cancel() }
}
