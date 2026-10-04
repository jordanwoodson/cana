package io.github.samolego.canta.ops

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.LocaleList
import androidx.test.platform.app.InstrumentationRegistry
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.request.ErrorResult
import coil3.request.SuccessResult
import io.github.samolego.canta.extension.getAllPackagesInfo
import io.github.samolego.canta.util.apps.AppInfo
import io.github.samolego.canta.util.apps.appIconRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

/** Isolated emulator, Work user 10, and scripts/create-icon-test-fixtures.sh APKs in /data/local/tmp. */
class AppIconDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val name = "io.github.jordanwoodson.cana.iconfixture"

    @Test fun realRemovedIconsStayVisibleAndRefreshAfterAnApkUpdate() = runBlocking {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val loader = ImageLoader.Builder(context).build()
        suspend fun app() = withContext(Dispatchers.IO) {
            context.packageManager.getAllPackagesInfo().single { it.packageName == name }
        }
        suspend fun icon(app: AppInfo, user: Int = 0): SuccessResult = withContext(Dispatchers.Main) {
            val result = loader.execute(appIconRequest(context, app, user, 64))
            assertTrue(result.toString(), result is SuccessResult)
            result as SuccessResult
        }
        fun center(result: SuccessResult) = (result.image as BitmapImage).bitmap.getPixel(32, 32)
        try {
            shell("pm uninstall $name")
            success("pm install -t --user 0 /data/local/tmp/cana-icon-v1.apk")
            success("cmd package install-existing --user 10 $name")
            val installed = app()
            assertFalse(installed.isUninstalled)
            assertNotNull(installed.applicationInfo)
            val first = icon(installed)
            assertEquals(Color.rgb(233, 30, 99), center(first))
            assertEquals(DataSource.MEMORY_CACHE, icon(installed).dataSource)

            // Both profiles share the APK, but the work badge must have its own cached image.
            val work = icon(installed, 10)
            assertNotEquals(first.memoryCacheKey, work.memoryCacheKey)
            assertNotEquals(DataSource.MEMORY_CACHE, work.dataSource)
            assertFalse((first.image as BitmapImage).bitmap.sameAs((work.image as BitmapImage).bitmap))
            assertEquals(DataSource.MEMORY_CACHE, icon(installed, 10).dataSource)

            success("pm uninstall --user 0 $name")
            val removed = app()
            assertTrue(removed.isUninstalled)
            assertNotNull(removed.applicationInfo)
            // This is the lookup Cana used before retaining ApplicationInfo for its own profile.
            try {
                context.packageManager.getApplicationIcon(name)
                fail("The removed package must not resolve through an installed-only name lookup")
            } catch (_: PackageManager.NameNotFoundException) { }
            assertEquals(Color.rgb(233, 30, 99), center(icon(removed)))

            success("pm install -r -t --user 10 /data/local/tmp/cana-icon-v2.apk")
            val updated = app()
            assertEquals(2L, updated.versionCode)
            assertTrue(updated.isUninstalled)
            assertNotEquals(removed.apkIdentity, updated.apkIdentity)
            assertNotEquals(appIconRequest(context, removed, 0, 64).memoryCacheKey,
                appIconRequest(context, updated, 0, 64).memoryCacheKey)
            assertEquals(Color.rgb(33, 150, 243), center(icon(updated)))

            success("cmd package install-existing --user 0 $name")
            val restored = app()
            assertFalse(restored.isUninstalled)
            assertEquals(Color.rgb(33, 150, 243), center(icon(restored)))
        } finally {
            loader.shutdown()
            shell("pm uninstall $name")
        }
    }

    @Test fun failedIconLoadsAreRetriedAndNeverReuseAnotherProfilesMetadata() = runBlocking {
        val attempts = AtomicInteger()
        val info = object : ApplicationInfo() {
            override fun loadIcon(pm: PackageManager): Drawable {
                if (attempts.incrementAndGet() == 1) throw Resources.NotFoundException("Icon unavailable")
                return ColorDrawable(Color.CYAN)
            }
        }.apply { packageName = "test.retry.icon" }
        val app = AppInfo("Retry", info.packageName, "1", 1, false, false, false, null,
            applicationInfo = info, apkIdentity = "retry-icon")
        val loader = ImageLoader.Builder(context).build()
        try {
            val request = appIconRequest(context, app, 0, 48)
            assertTrue(loader.execute(request) is ErrorResult)
            assertTrue(loader.execute(request) is SuccessResult)
            assertEquals(2, attempts.get())
            assertEquals(DataSource.MEMORY_CACHE, (loader.execute(request) as SuccessResult).dataSource)

            val missing = app.copy(packageName = "com.android.settings", applicationInfo = null)
            assertTrue("Missing work metadata must not resolve the personal profile's installed app",
                loader.execute(appIconRequest(context, missing, 10, 48)) is ErrorResult)
        } finally { loader.shutdown() }
    }

    @Test fun resourceConfigurationAndTargetSizeHaveSeparateCacheKeys() {
        val app = AppInfo("Icon", "test.configuration.icon", "1", 1, false, false, false, null,
            apkIdentity = "configuration-icon")
        val french = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocales(LocaleList(Locale.FRENCH))
        })
        val normal = appIconRequest(context, app, 0, 48).memoryCacheKey
        assertNotEquals(normal, appIconRequest(french, app, 0, 48).memoryCacheKey)
        assertNotEquals(normal, appIconRequest(context, app, 0, 64).memoryCacheKey)
        assertNotEquals(normal, appIconRequest(context, app, 10, 48).memoryCacheKey)
    }

    @Test fun workBadgingCannotModifyASharedPersonalBitmap() = runBlocking {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        val original = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val info = object : ApplicationInfo() {
            override fun loadIcon(pm: PackageManager): Drawable = BitmapDrawable(context.resources, bitmap)
        }.apply { packageName = "test.shared.bitmap" }
        val app = AppInfo("Shared bitmap", info.packageName, "1", 1, false, false, false, null,
            applicationInfo = info, apkIdentity = "shared-bitmap")
        val loader = ImageLoader.Builder(context).build()
        try {
            assertTrue(loader.execute(appIconRequest(context, app, 0, 64)) is SuccessResult)
            val work = loader.execute(appIconRequest(context, app, 10, 64))
            assertTrue(work.toString(), work is SuccessResult)
            assertTrue("Work badging must not mutate the package's shared source bitmap", bitmap.sameAs(original))
            assertFalse(original.sameAs(((work as SuccessResult).image as BitmapImage).bitmap))
        } finally { loader.shutdown() }
    }

    private fun success(command: String) {
        val output = shell(command)
        assertTrue(output, output.contains("Success") || output.contains("installed for user"))
    }

    private fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).use {
        FileInputStream(it.fileDescriptor).bufferedReader().use { reader -> reader.readText() }
    }
}
