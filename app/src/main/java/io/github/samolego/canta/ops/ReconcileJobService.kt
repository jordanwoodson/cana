package io.github.samolego.canta.ops

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import io.github.samolego.canta.util.LogUtils
import kotlinx.coroutines.*

/** Keeps the process alive while boot/binder reconciliation finishes its journaled work. */
class ReconcileJobService : JobService() {
    private var work: Job? = null
    override fun onStartJob(params: JobParameters): Boolean {
        work = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { CanaServices.getInstance().reconcileNow() }
            finally { withContext(Dispatchers.Main) { jobFinished(params, false) } }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { work?.cancel(); return true }
    companion object {
        fun schedule(context: Context) {
            // Settings can clear shell-created metered rules in its own boot receiver.
            // Check again after boot work has settled, including when Shizuku started early.
            for ((id, delay) in listOf(0xCA1A to 1_000L, 0xCA1B to 120_000L)) {
                val job = JobInfo.Builder(id, ComponentName(context, ReconcileJobService::class.java))
                    .setPersisted(true).setMinimumLatency(delay).build()
                val result = context.getSystemService(JobScheduler::class.java).schedule(job)
                if (result != JobScheduler.RESULT_SUCCESS) LogUtils.e("ReconcileJob", "Android refused the reconciliation job")
            }
        }
    }
}
