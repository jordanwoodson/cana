package io.github.samolego.canta.ops

import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.samolego.canta.MainActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeNotNull
import org.junit.Test
import rikka.shizuku.Shizuku

/** Prepares the debug installation on the isolated release-test emulator through the manager UI. */
class WorkProfileAuthorizationTest {
    @Test fun allowDebugAppInManagedProfile() = runBlocking {
        val workUser = InstrumentationRegistry.getArguments().getString("workUser")?.toInt()
        assumeNotNull(workUser)
        check(Build.HARDWARE in setOf("goldfish", "ranchu") && workUser != 0 && Process.myUid() / 100_000 == workUser)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MainActivity::class.java).use {
            withTimeout(30_000) { while (!Shizuku.pingBinder()) delay(200) }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                instrumentation.runOnMainSync { Shizuku.requestPermission(971) }
                withTimeout(30_000) {
                    while (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                        val root = instrumentation.uiAutomation.rootInActiveWindow
                        if (root?.packageName == "moe.shizuku.privileged.api") {
                            var allow = root.findAccessibilityNodeInfosByText("Allow all the time").firstOrNull()
                            while (allow != null && !allow.isClickable) allow = allow.parent
                            allow?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                        }
                        delay(250)
                    }
                }
            }
        }
    }
}
