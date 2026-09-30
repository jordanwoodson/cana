package io.github.samolego.canta.ops

import io.github.samolego.canta.util.apps.updateApkSize
import io.github.samolego.canta.util.apps.apkSize
import io.github.samolego.canta.util.apps.knownApkTotal
import org.junit.Assert.assertEquals
import org.junit.Test

class ApkSizesTest {
    @Test fun countsBaseAndUniqueSplitsOnlyForUpdatedSystemApps() {
        val lengths = mapOf("/data/app/test/base.apk" to 100L, "/data/app/test/lang.apk" to 25L)
        assertEquals(125L, updateApkSize(true, "/data/app/test/base.apk",
            listOf("/data/app/test/lang.apk", "/data/app/test/lang.apk")) { lengths[it] ?: 0 })
        assertEquals(0L, updateApkSize(false, "/data/app/test/base.apk", emptyList()) { 100 })
        assertEquals(0L, updateApkSize(true, "/system/app/test/base.apk", emptyList()) { 100 })
        assertEquals(0L, updateApkSize(true, null, emptyList()) { 100 })
    }

    @Test fun apkBytesIncludeTheBaseAndEveryUniqueSplit() {
        val sizes = mapOf("/app/base.apk" to 100L, "/app/en.apk" to 25L, "/app/arm.apk" to 75L)
        assertEquals(200L, apkSize("/app/base.apk", listOf("/app/en.apk", "/app/en.apk", "/app/arm.apk")) { sizes.getValue(it) })
    }

    @Test fun missingOrUnreadableApksAreUnknownRatherThanPartialTotals() {
        assertEquals(0L, apkSize(null, listOf("/app/en.apk")) { 25L })
        assertEquals(0L, apkSize("/app/base.apk", listOf("/app/en.apk")) { if (it.endsWith("base.apk")) 100L else 0L })
        assertEquals(0L, apkSize("/app/base.apk", emptyList()) { throw SecurityException("No access") })
        assertEquals(0L, apkSize("/app/base.apk", emptyList()) { -1L })
    }

    @Test fun unreadableUpdateSplitDoesNotUnderstateItsSize() {
        assertEquals(0L, updateApkSize(true, "/data/app/test/base.apk", listOf("/data/app/test/en.apk")) {
            if (it.endsWith("base.apk")) 100L else throw SecurityException("No access")
        })
    }

    @Test fun unknownUpdateSizesDoNotProduceAnApparentlyCompleteBannerTotal() {
        assertEquals(null, knownApkTotal(listOf(100L, 0L)))
        assertEquals(null, knownApkTotal(listOf(0L)))
        assertEquals(125L, knownApkTotal(listOf(100L, 25L)))
    }
}
