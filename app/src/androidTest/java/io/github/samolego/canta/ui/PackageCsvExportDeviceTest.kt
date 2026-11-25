package io.github.samolego.canta.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.platform.app.InstrumentationRegistry
import io.github.samolego.canta.ui.component.rememberPackageCsvExport
import io.github.samolego.canta.util.apps.UserProfile
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class PackageCsvExportDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pickerResultWritesOriginalProfileAfterRestoration() {
        assumeTrue(InstrumentationRegistry.getArguments().containsKey("workUser"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var request = 0
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>,
                    input: I, options: ActivityOptionsCompat?) {
                    assertTrue(input.toString().contains("-user0-"))
                    request = requestCode
                }
            }
        }
        var selected by mutableIntStateOf(0)
        lateinit var export: () -> Unit
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                export = rememberPackageCsvExport(selected, if (selected == 0) UserProfile.Kind.PERSONAL else UserProfile.Kind.WORK)
            }
        }
        compose.runOnIdle { export(); selected = UserProfile.currentUserId }
        restoration.emulateSavedInstanceStateRestore()
        val output = File.createTempFile("profile-export", ".csv", context.cacheDir)
        try {
            compose.runOnIdle {
                assertTrue(owner.activityResultRegistry.dispatchResult(request, Activity.RESULT_OK, Intent().setData(Uri.fromFile(output))))
            }
            compose.waitUntil(60_000) { output.readText().contains("0,personal,io.github.jordanwoodson.cana.fixture,") }
            assertFalse(output.readText().contains("${UserProfile.currentUserId},work,io.github.jordanwoodson.cana.fixture,"))
        } finally { output.delete() }
    }
}
