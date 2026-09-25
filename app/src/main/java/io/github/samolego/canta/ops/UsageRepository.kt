package io.github.samolego.canta.ops

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import io.github.samolego.canta.util.apps.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UsageRepository(private val context: Context) {
    /** Null means unavailable, whereas an empty map means access was granted but no use was recorded. */
    suspend fun lastUsed(userId: Int): Map<String, Long>? = withContext(Dispatchers.IO) {
        // cmd usagestats has no per-user query on Android 15. Never substitute personal data.
        if (userId != UserProfile.currentUserId) return@withContext null
        val ops = context.getSystemService(AppOpsManager::class.java)
        if (ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) != AppOpsManager.MODE_ALLOWED)
            return@withContext null
        val now = System.currentTimeMillis()
        context.getSystemService(UsageStatsManager::class.java)
            .queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 366L * 86_400_000, now)
            ?.groupBy { it.packageName }?.mapValues { (_, stats) -> stats.maxOf { it.lastTimeUsed } }
    }
}
