package io.github.samolego.canta.data

import androidx.datastore.core.DataStoreFactory
import io.github.samolego.canta.data.proto.NetworkBlock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class PrivacyReviewStoreTest {
    @Test fun undoingRenewedConsentRestoresUnreviewedIntentInsteadOfReusingNewConsent() = runBlocking {
        val directory = Files.createTempDirectory("privacy-undo-consent").toFile()
        val job = SupervisorJob()
        try {
            val store = PrivacyStore(DataStoreFactory.create(PrivacySerializer,
                scope = CoroutineScope(Dispatchers.IO + job), produceFile = { directory.resolve("privacy.pb") }))
            store.bindInstallation("device-A")
            store.setBlock("test.app", 10, 10123, true)
            val prior = store.blocks.first().single()
            store.saveReviewed(prior.toBuilder().setIdentity("reviewed-install").setSafetyConditions("reviewed")
                .addApprovals("10:test.app:role:BROWSER").setStatus("verified").build(), false)
            store.restoreIntent("test.app", 10, 10123, true, false, prior)
            assertTrue(store.blocks.first().single().identity.isEmpty())
            assertTrue(store.blocks.first().single().approvalsList.isEmpty())
            assertEquals("needs_review", store.blocks.first().single().status)
        } finally { job.cancelAndJoin(); directory.deleteRecursively() }
    }
    @Test fun restoredConsentIsQuarantinedAndFailureAndForgetSurviveReopen() = runBlocking {
        val directory = Files.createTempDirectory("privacy-review").toFile()
        val firstJob = SupervisorJob()
        val secondJob = SupervisorJob()
        try {
            val file = directory.resolve("privacy.pb")
            fun store(job: Job) = PrivacyStore(DataStoreFactory.create(PrivacySerializer,
                scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file }))
            val first = store(firstJob)
            first.bindInstallation("device-A")
            first.saveReviewed(NetworkBlock.newBuilder().setPackageName("test.app").setUserId(10)
                .setAppId(10123).setIdentity("old-install").setSafetyConditions("reviewed")
                .addApprovals("browser:test.app").setStatus("pending").build(), false)
            first.status("test.app", 10, false, "failed", "Unavailable")
            firstJob.cancelAndJoin()
            val reopened = store(secondJob)
            assertEquals("failed", reopened.blocks.first().single().status)
            reopened.bindInstallation("device-B")
            assertEquals("needs_review", reopened.blocks.first().single().status)
            assertTrue(reopened.blocks.first().single().identity.isEmpty())
            assertTrue(reopened.blocks.first().single().approvalsList.isEmpty())
            // Forgetting saved intent must not need the package to still exist or its old appId.
            reopened.forget("test.app", 10, false)
            assertTrue(reopened.blocks.first().isEmpty())
        } finally { firstJob.cancelAndJoin(); secondJob.cancelAndJoin(); directory.deleteRecursively() }
    }
}
