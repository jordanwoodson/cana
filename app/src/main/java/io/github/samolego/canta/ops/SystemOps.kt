package io.github.samolego.canta.ops

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import io.github.samolego.canta.R
import io.github.samolego.canta.data.HistoryStore
import io.github.samolego.canta.data.proto.OperationRecord
import io.github.samolego.canta.util.LogUtils
import io.github.samolego.canta.util.apps.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

/** Global controls intentionally do not use the app-management profile. */
class SystemOps(private val context: Context, private val shell: ShellRunner, private val history: HistoryStore,
    private val journal: OperationJournal) {
    suspend fun snapshot(control: SystemControl): JSONObject = withContext(Dispatchers.IO) {
        JSONObject().put("control", control.name).apply {
            if (control == SystemControl.DATA_SAVER) {
                val result = shell.exec(listOf("cmd", "netpolicy", "get", "restrict-background"))
                check(result.success) { result.message }
                put("dataSaver", SystemPolicy.dataSaver(result.stdout))
            } else put("settings", JSONObject().apply {
                control.keys.forEach { key -> put(key, Settings.Global.getString(context.contentResolver, key) ?: JSONObject.NULL) }
            })
        }
    }
    suspend fun apply(control: SystemControl, values: Map<String, String?> = emptyMap(), dataSaver: Boolean? = null): OperationResult =
        journal.run("", UserProfile.currentUserId, "system", UUID.randomUUID().toString(), snapshot = { snapshot(control) }) { before ->
            require(values.keys.all { it in control.keys })
            require(if (control == SystemControl.DATA_SAVER) dataSaver != null && values.isEmpty() else dataSaver == null && values.isNotEmpty())
            write(values, dataSaver)
            val expected = JSONObject(before.toString())
            if (dataSaver != null) expected.put("dataSaver", dataSaver)
            else values.forEach { (key, value) -> expected.getJSONObject("settings").put(key, value ?: JSONObject.NULL) }
            verified(expected, snapshot(control))
        }

    suspend fun undo(record: OperationRecord, batchId: String = UUID.randomUUID().toString()): OperationResult {
        val known = history.records.first()
        if (record !in known || record.action != "system") return OperationResult(false, context.getString(R.string.undo_unsupported))
        if (known.any { it.undoOf == record.id && it.completed && it.success }) return OperationResult(true, context.getString(R.string.operation_skipped), skipped = true)
        val prior = runCatching { JSONObject(record.previousState) }.getOrNull()
            ?: return OperationResult(false, context.getString(R.string.undo_unsupported))
        val control = runCatching { SystemControl.valueOf(prior.getString("control")) }.getOrNull()
            ?: return OperationResult(false, context.getString(R.string.undo_unsupported))
        return journal.run("", UserProfile.currentUserId, "system", batchId, record.id, snapshot = { snapshot(control) }) {
            val values = if (control == SystemControl.DATA_SAVER) emptyMap() else control.keys.associateWith { key ->
                val settings = prior.getJSONObject("settings")
                require(settings.has(key))
                if (settings.isNull(key)) null else settings.getString(key)
            }
            write(values, if (control == SystemControl.DATA_SAVER) prior.getBoolean("dataSaver") else null)
            verified(prior, snapshot(control))
        }
    }

    private suspend fun write(values: Map<String, String?>, dataSaver: Boolean?) {
        if (dataSaver != null) {
            val result = shell.exec(listOf("cmd", "netpolicy", "set", "restrict-background", dataSaver.toString()))
            check(result.success) { result.message }
        }
        for ((key, value) in values) {
            LogUtils.i("SystemOps", "Write global $key=$value")
            if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED) {
                check(Settings.Global.putString(context.contentResolver, key, value)) { context.getString(R.string.operation_not_applied) }
            } else {
                val args = if (value == null) listOf("settings", "delete", "global", key) else listOf("settings", "put", "global", key, value)
                val result = shell.exec(args)
                check(result.success) { result.message }
            }
        }
    }
    private fun verified(expected: JSONObject, actual: JSONObject): OperationResult {
        val success = SnapshotState.equal(expected, actual)
        return OperationResult(success, context.getString(if (success) R.string.operation_success else R.string.operation_not_applied))
    }
}
