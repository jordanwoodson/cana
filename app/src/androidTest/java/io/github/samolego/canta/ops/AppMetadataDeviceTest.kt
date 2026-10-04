package io.github.samolego.canta.ops

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import androidx.test.platform.app.InstrumentationRegistry
import io.github.samolego.canta.util.BloatData
import io.github.samolego.canta.util.RemovalRecommendation
import io.github.samolego.canta.util.apps.AppInfo
import org.junit.Assert.*
import org.junit.Test

class AppMetadataDeviceTest {
    private val pm get() = InstrumentationRegistry.getInstrumentation().targetContext.packageManager

    @Test fun ownProfileRemovedAppsKeepTheirApplicationInfo() {
        val info = pm.getPackageInfo("com.android.settings", PackageManager.MATCH_UNINSTALLED_PACKAGES)
        val app = AppInfo.fromPackageInfo(info, pm, isUninstalled = true)
        assertSame(info.applicationInfo, app.applicationInfo)
        assertFalse(app.metadataIncomplete)
    }

    @Test fun brokenLabelKeepsPackageStateAndGuidanceAlongsideReadableApps() {
        val broken = PackageInfo().apply {
            packageName = "test.broken.label"
            versionName = "2"
            longVersionCode = 2
            applicationInfo = object : ApplicationInfo() {
                override fun loadLabel(pm: PackageManager): CharSequence = throw Resources.NotFoundException("Broken label")
            }.apply {
                packageName = "test.broken.label"
                flags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_SUSPENDED
                enabled = false
            }
        }
        val data = BloatData(null, "Keep the original guidance", RemovalRecommendation.EXPERT)
        val readable = pm.getPackageInfo("com.android.settings", 0)
        val apps = listOf(broken, readable).map {
            AppInfo.fromPackageInfo(it, pm, false, mapOf(broken.packageName to data), otherUser = true)
        }
        assertEquals(2, apps.size)
        val app = apps.first()
        assertEquals(broken.packageName, app.name)
        assertTrue(app.isSystemApp)
        assertTrue(app.appTypeKnown)
        assertTrue(app.isDisabled)
        assertTrue(app.isSuspended)
        assertFalse(app.isUninstalled)
        assertEquals(data, app.bloatData)
        assertTrue(app.metadataIncomplete)
        assertEquals("com.android.settings", apps.last().packageName)
    }

    @Test fun missingApplicationInfoStillProducesAnInventoryEntry() {
        val info = PackageInfo().apply { packageName = "test.missing.metadata" }
        val app = AppInfo.fromPackageInfo(info, pm, isUninstalled = true)
        assertEquals(info.packageName, app.packageName)
        assertEquals(info.packageName, app.name)
        assertTrue(app.isUninstalled)
        assertNull(app.applicationInfo)
        assertFalse(app.appTypeKnown)
        assertTrue(app.metadataIncomplete)
    }
}
