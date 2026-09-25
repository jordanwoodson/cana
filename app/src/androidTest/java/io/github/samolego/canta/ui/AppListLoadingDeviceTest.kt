package io.github.samolego.canta.ui

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.samolego.canta.ui.viewmodel.AppListViewModel
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppListLoadingDeviceTest {
    @Test fun cancelledBadgeLoadIsRetriedOnReturn() = runBlocking {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val metadataStarted = CompletableDeferred<Unit>()
        var calls = 0
        val model = AppListViewModel { _, _ ->
            if (++calls == 1) {
                metadataStarted.complete(Unit)
                awaitCancellation()
            }
            JSONObject("""{"com.android.settings":{"list":"Aosp","description":"Loaded again","removal":"Unsafe"}}""")
        }
        val loading = launch(Dispatchers.Main) { model.loadInstalled(context.packageManager, context) }
        withTimeout(60_000) { metadataStarted.await() }
        assertTrue(model.appList.isNotEmpty())
        loading.cancelAndJoin()
        assertTrue("Returning to the page must retry unfinished metadata", model.needsReload)
        model.loadInstalled(context.packageManager, context)
        assertFalse(model.needsReload)
        assertEquals("Loaded again", model.appList.single { it.packageName == "com.android.settings" }.description)
    }
}
