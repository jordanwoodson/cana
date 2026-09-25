package io.github.samolego.canta.ops

import io.github.samolego.canta.util.apps.updateApkSize
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
}
