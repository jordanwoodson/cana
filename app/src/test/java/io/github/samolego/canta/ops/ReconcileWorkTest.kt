package io.github.samolego.canta.ops

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ReconcileWorkTest {
    @Test fun stoppingOneAndroidJobDoesNotCancelTheOtherOrReportStoppedJobFinished() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val work = ReconcileWork(scope)
            val firstStarted = CompletableDeferred<Unit>()
            val secondStarted = CompletableDeferred<Unit>()
            val releaseSecond = CompletableDeferred<Unit>()
            val firstStopped = CompletableDeferred<Unit>()
            val completed = mutableListOf<Int>()
            work.start(1, {
                firstStarted.complete(Unit)
                try { awaitCancellation() } finally { firstStopped.complete(Unit) }
            }, { completed += 1 })
            work.start(2, { secondStarted.complete(Unit); releaseSecond.await() }, { completed += 2 })
            firstStarted.await(); secondStarted.await()
            work.stop(1)
            yield()
            assertTrue("Stop must target its own job ID", firstStopped.isCompleted)
            assertTrue("Stopped jobs must not call jobFinished", completed.isEmpty())
            releaseSecond.complete(Unit)
            yield()
            assertEquals(listOf(2), completed)
        } finally { scope.cancel(); scope.coroutineContext[Job]!!.join() }
    }

    @Test fun cancelledOldAttemptDoesNotRemoveItsReplacement() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val work = ReconcileWork(scope)
            val entered = CompletableDeferred<Unit>()
            val releaseOld = CompletableDeferred<Unit>()
            val replacementStarted = CompletableDeferred<Unit>()
            val replacementStopped = CompletableDeferred<Unit>()
            val completed = mutableListOf<Int>()
            work.start(1, {
                entered.complete(Unit)
                withContext(NonCancellable) { releaseOld.await() }
            }, { completed += 1 })
            entered.await()
            work.stop(1)
            work.start(1, {
                replacementStarted.complete(Unit)
                try { awaitCancellation() } finally { replacementStopped.complete(Unit) }
            }, { completed += 2 })
            replacementStarted.await()
            releaseOld.complete(Unit)
            yield()
            work.stop(1)
            yield()
            assertTrue(replacementStopped.isCompleted)
            assertTrue(completed.isEmpty())
        } finally { scope.cancel(); scope.coroutineContext[Job]!!.join() }
    }
}
