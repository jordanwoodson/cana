package io.github.samolego.canta.util

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class HiddenApiInitializerTest {
    @Test fun concurrentPackageProfileAndSafetyInitializationRegisterCompleteSetOnce() {
        val calls = AtomicInteger()
        val initializer = HiddenApiInitializer { prefixes ->
            assertEquals(setOf("Landroid/content/pm/", "Landroid/os/IUserManager", "Landroid/app/admin/IDevicePolicyManager",
                "Landroid/net/", "Landroid/permission/"), prefixes.toSet())
            calls.incrementAndGet()
            true
        }
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        try {
            val tasks = (0 until 8).map { pool.submit { start.await(); initializer.ensureReady() } }
            start.countDown()
            tasks.forEach { it.get(5, TimeUnit.SECONDS) }
            assertEquals(1, calls.get())
        } finally { pool.shutdownNow() }
    }

    @Test fun failedRegistrationIsNotReadyAndCanBeRetried() {
        val attempts = AtomicInteger()
        val initializer = HiddenApiInitializer { attempts.incrementAndGet() > 1 }
        assertTrue(runCatching { initializer.ensureReady() }.isFailure)
        initializer.ensureReady()
        initializer.ensureReady()
        assertEquals(2, attempts.get())
    }
}
