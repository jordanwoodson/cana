package io.github.samolego.canta.ops

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import io.github.samolego.canta.R
import io.github.samolego.canta.data.HistoryStore
import io.github.samolego.canta.data.proto.OperationRecord
import io.github.samolego.canta.util.LogUtils
import io.github.samolego.canta.util.apps.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

data class GrantStatus(val secureSettings: Boolean, val usageStats: Boolean)

class SelfGrants(context: Context, private val shell: ShellRunner, private val history: HistoryStore) {
    private val context = context.applicationContext
    private val mutex = Mutex()
    private val _status = MutableStateFlow(status())
    val state = _status.asStateFlow()

    fun refreshStatus() { _status.value = status() }

    fun status(): GrantStatus {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        @Suppress("DEPRECATION")
        val usage = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return GrantStatus(
            context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED,
            usage == AppOpsManager.MODE_ALLOWED,
        )
    }

    suspend fun grantMissing(): List<OperationResult> = mutex.withLock {
        withContext(Dispatchers.IO + NonCancellable) {
            val before = status()
            val userId = UserProfile.currentUserId
            val batch = UUID.randomUUID().toString()
            val results = mutableListOf<OperationResult>()
            val commands = buildList {
                if (!before.secureSettings) add("self_grant_secure_settings" to
                    listOf("pm", "grant", "--user", userId.toString(), context.packageName, Manifest.permission.WRITE_SECURE_SETTINGS))
                if (!before.usageStats) add("self_grant_usage_stats" to
                    listOf("appops", "set", "--user", userId.toString(), context.packageName, "GET_USAGE_STATS", "allow"))
            }
            for ((action, argv) in commands) {
                val id = UUID.randomUUID().toString()
                try {
                    history.append(OperationRecord.newBuilder().setId(id).setBatchId(batch)
                        .setTimestampMs(System.currentTimeMillis()).setUserId(userId).setPackageName(context.packageName)
                        .setAction(action).setPreviousState("{\"granted\":false}").build())
                    val result = shell.exec(argv)
                    val after = status()
                    val granted = if (action == "self_grant_secure_settings") after.secureSettings else after.usageStats
                    val success = result.success && granted
                    val message = if (success) context.getString(R.string.operation_success)
                        else result.message.ifBlank { context.getString(R.string.operation_not_applied) }
                    history.complete(id, success, message, "{\"granted\":$granted}", granted)
                    results += OperationResult(success, message, granted)
                } catch (e: Exception) {
                    LogUtils.e("SelfGrants", "Could not grant or record $action", e)
                    results += OperationResult(false, e.message ?: context.getString(R.string.operation_failed))
                }
            }
            refreshStatus()
            results
        }
    }
}
