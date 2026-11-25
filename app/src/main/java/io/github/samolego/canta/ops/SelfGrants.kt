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
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import org.json.JSONObject

data class GrantStatus(val secureSettings: Boolean, val usageStats: Boolean)

class SelfGrants(context: Context, private val shell: ShellRunner, private val history: HistoryStore) {
    private val context = context.applicationContext
    private val mutex = history.mutationMutex
    private val _status = MutableStateFlow(status())
    val state = _status.asStateFlow()

    fun refreshStatus() { _status.value = status() }

    private fun usageMode(): Int {
        @Suppress("DEPRECATION")
        return context.getSystemService(AppOpsManager::class.java)
            .checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    }

    fun status() = GrantStatus(
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED,
        usageMode() == AppOpsManager.MODE_ALLOWED,
    )

    internal fun snapshot(action: String): JSONObject {
        if (action == "self_grant_secure_settings") return JSONObject().put("granted", status().secureSettings)
        require(action == "self_grant_usage_stats")
        val mode = usageMode()
        return JSONObject().put("granted", mode == AppOpsManager.MODE_ALLOWED).put("appOpMode", when (mode) {
            AppOpsManager.MODE_ALLOWED -> "allow"
            AppOpsManager.MODE_IGNORED -> "ignore"
            AppOpsManager.MODE_ERRORED -> "deny"
            AppOpsManager.MODE_FOREGROUND -> "foreground"
            else -> "default"
        })
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
                history.withActiveOperation(id) {
                    try {
                        val previous = snapshot(action)
                        history.append(OperationRecord.newBuilder().setId(id).setBatchId(batch)
                            .setTimestampMs(System.currentTimeMillis()).setUserId(userId).setPackageName(context.packageName)
                            .setAction(action).setPreviousState(previous.toString()).build())
                        val result = shell.exec(argv)
                        val after = snapshot(action)
                        val granted = after.getBoolean("granted")
                        val changed = !SnapshotState.equal(previous, after)
                        val success = result.success && granted
                        val message = if (success) context.getString(R.string.operation_success)
                            else result.message.ifBlank { context.getString(R.string.operation_not_applied) }
                        history.complete(id, success, message, after.toString(), changed)
                        results += OperationResult(success, message, changed)
                    } catch (e: Exception) {
                        LogUtils.e("SelfGrants", "Could not grant or record $action", e)
                        results += OperationResult(false, e.message ?: context.getString(R.string.operation_failed))
                    }
                }
            }
            refreshStatus()
            results
        }
    }
}
