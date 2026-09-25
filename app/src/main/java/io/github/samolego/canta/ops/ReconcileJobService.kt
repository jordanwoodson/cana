package io.github.samolego.canta.ops

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
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
            val scheduler = context.getSystemService(JobScheduler::class.java)
            val preferences = context.getSharedPreferences("reconcile", Context.MODE_PRIVATE)
            val now = SystemClock.elapsedRealtime()
            val last = preferences.getLong("delayed_check", -1)
            for ((id, delay) in listOf(0xCA1A to 1_000L, 0xCA1B to 120_000L)) {
                // A process started to run a job receives a sticky binder too. Do not replace
                // that running job or create an endless chain of delayed checks.
                if (scheduler.getPendingJob(id) != null) continue
                if (id == 0xCA1B && last >= 0 && now >= last && now - last < 300_000) continue
                val job = JobInfo.Builder(id, ComponentName(context, ReconcileJobService::class.java))
                    .setPersisted(true).setMinimumLatency(delay).build()
                val result = scheduler.schedule(job)
                if (result != JobScheduler.RESULT_SUCCESS) LogUtils.e("ReconcileJob", "Android refused the reconciliation job")
                else if (id == 0xCA1B) preferences.edit().putLong("delayed_check", now).apply()
            }
        }
    }
}
