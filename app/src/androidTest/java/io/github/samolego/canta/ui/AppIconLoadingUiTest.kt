package io.github.samolego.canta.ui

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.github.samolego.canta.ui.component.AppIconImage
import io.github.samolego.canta.ui.component.AppTile
import io.github.samolego.canta.ui.theme.CantaTheme
import io.github.samolego.canta.util.apps.AppInfo
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AppIconLoadingUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun packageIconsAreLoadedOffTheUiThread() {
        val loadedOn = AtomicReference<Thread>()
        val renderedOn = AtomicReference<Thread>()
        val info = object : ApplicationInfo() {
            override fun loadIcon(pm: PackageManager): Drawable {
                loadedOn.set(Thread.currentThread())
                return object : ColorDrawable(Color.MAGENTA) {
                    override fun draw(canvas: Canvas) {
                        renderedOn.set(Thread.currentThread())
                        super.draw(canvas)
                    }
                }
            }
        }.apply { packageName = "test.icon.background" }
        val app = AppInfo("Icon test", info.packageName, "1", 1, false, false, false, null,
            applicationInfo = info, apkIdentity = "background-test")
        compose.setContent { CantaTheme { Box(Modifier.testTag("icon")) { AppIconImage(app) } } }
        compose.waitUntil(5_000) { loadedOn.get() != null && renderedOn.get() != null }
        assertNotSame("PackageManager icon loading must not block Compose", Looper.getMainLooper().thread, loadedOn.get())
        assertNotSame("Icon rasterization must not block Compose", Looper.getMainLooper().thread, renderedOn.get())
        waitForColor(androidx.compose.ui.graphics.Color.Magenta)
    }

    @Test fun profileSwitchCannotShowThePreviousProfilesLateIcon() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        fun app(slow: Boolean) = AppInfo("Icon", "test.profile.icon", "1", 1, false, false, false, null,
            applicationInfo = object : ApplicationInfo() {
                override fun loadIcon(pm: PackageManager): Drawable {
                    if (slow) {
                        started.countDown()
                        check(release.await(10, TimeUnit.SECONDS))
                    }
                    return ColorDrawable(if (slow) Color.RED else Color.BLUE)
                }
            }.apply { packageName = "test.profile.icon" }, apkIdentity = "profile-switch")
        val current = mutableStateOf(app(true))
        val user = mutableStateOf(0)
        try {
            compose.setContent { CantaTheme {
                Box(Modifier.testTag("icon")) { AppIconImage(current.value, user.value) }
            } }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            compose.runOnIdle { current.value = app(false); user.value = 10 }
            waitForColor(androidx.compose.ui.graphics.Color.Blue)
            release.countDown()
            compose.waitForIdle()
            waitForColor(androidx.compose.ui.graphics.Color.Blue)
        } finally { release.countDown() }
    }

    @Test fun incompleteMetadataKeepsTheAppVisibleAndSelectable() {
        val app = AppInfo("test.unreadable.app", "test.unreadable.app", "unknown", 0, true, false, false,
            null, metadataIncomplete = true)
        var opened = false
        compose.setContent { CantaTheme {
            AppTile(appInfo = app, isSelected = false, onCheckChanged = {}, onShowDialog = { opened = true })
        } }
        compose.onNodeWithText("Some app details unavailable").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(opened) }
    }

    private fun waitForColor(expected: androidx.compose.ui.graphics.Color) {
        compose.waitUntil(5_000) {
            val pixels = compose.onNodeWithTag("icon").captureToImage().toPixelMap()
            val center = pixels[pixels.width / 2, pixels.height / 2]
            kotlin.math.abs(center.red - expected.red) < 0.02f &&
                kotlin.math.abs(center.green - expected.green) < 0.02f &&
                kotlin.math.abs(center.blue - expected.blue) < 0.02f
        }
    }
}
