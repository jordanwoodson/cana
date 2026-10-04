package io.github.samolego.canta.ops

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.IBinder
import io.github.samolego.canta.R
import io.github.samolego.canta.data.SettingsStore
import io.github.samolego.canta.util.RemovalRecommendation
import io.github.samolego.canta.util.BloatData
import io.github.samolego.canta.util.BloatListRepository
import io.github.samolego.canta.util.TimedCache
import io.github.samolego.canta.util.shizuku.ShizukuPackageInstallerUtils
import io.github.samolego.canta.util.shizuku.ShizukuUserUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.lsposed.hiddenapibypass.HiddenApiBypass
import io.github.samolego.canta.util.HiddenApiAccess
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/** Unavailable safety data refuses mutation; an empty successful query means no holder/admin. */
class SafetyInspector(context: Context, private val shell: ShellRunner) {
    private val context = context.applicationContext
    val alwaysProtected = SafetyPolicy.protectedPackages(context.packageName)

    /** Role holders, keyboards, admins, installed set and bloat metadata from one privileged query. */
    private class Baseline(val snapshot: SafetySnapshot, val metadata: Map<String, BloatData>)

    // Querying the platform costs about ten shell commands. A batch shares one baseline per profile
    // instead of paying that for every package; the batch drops it again when it finishes.
    private val baselines = TimedCache<Int, Baseline>(BASELINE_TTL_MS)

    suspend fun inspect(names: List<String>, userId: Int, removing: Boolean = false): Map<String, SafetyAssessment> = withContext(Dispatchers.IO) {
        if (names.all { it in alwaysProtected }) return@withContext names.associateWith { SafetyAssessment(protected = true) }
        try {
            val baseline = baselines.getOrCompute(userId) { queryBaseline(userId) }
            val allowUnsafe = SettingsStore.getInstance().allowUnsafeUninstallsFlow.first()
            names.associateWith { name ->
                val data = baseline.metadata[name]
                val dependents = SafetyPolicy.installedDependents(name, baseline.metadata, baseline.snapshot.installedPackages)
                val assessment = SafetyPolicy.assess(name, baseline.snapshot, dependents, userId)
                if (removing && !allowUnsafe && data?.removal == RemovalRecommendation.UNSAFE)
                    assessment.copy(error = context.getString(R.string.unsafe_uninstall_blocked)) else assessment
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            names.associateWith { SafetyAssessment(protected = it in alwaysProtected,
                error = context.getString(R.string.safety_query_failed, (e.cause ?: e).message.orEmpty())) }
        }
    }

    /** Forget cached platform state, e.g. after a batch may have moved roles to other packages. */
    fun invalidate() = baselines.invalidate()

    private suspend fun queryBaseline(userId: Int): Baseline {
        val profile = ShizukuUserUtils.getUsers().firstOrNull { it.id == userId }
            ?: error(context.getString(R.string.profile_missing))
        check(!profile.blocksShell && !profile.blocksUninstall) { context.getString(R.string.safety_profile_restricted) }
        val protected = alwaysProtected.toMutableSet()
        protected += handlers(userId, listOf("-a", "android.intent.action.INSTALL_PACKAGE", "-d", "content://cana.invalid/update.apk", "-t", "application/vnd.android.package-archive"))
        protected += handlers(userId, listOf("-a", "android.intent.action.MANAGE_PERMISSIONS"))
        protected += handlers(userId, listOf("-a", "android.settings.SETTINGS"))
        if (Build.VERSION.SDK_INT >= 29) ShizukuPackageInstallerUtils.permissionControllerPackage()?.let { protected += it }
        val roles = listOf("HOME", "SMS", "DIALER", "BROWSER", "ASSISTANT").associateWith { role ->
            if (Build.VERSION.SDK_INT >= 29) checked(listOf("cmd", "role", "get-role-holders", "--user", "$userId", "android.app.role.$role"))
                .lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            else legacyRole(role, userId)
        }
        val keyboards = SafetyPolicy.keyboardPackages(
            checked(listOf("settings", "--user", "$userId", "get", "secure", "default_input_method")).trim(),
            checked(listOf("settings", "--user", "$userId", "get", "secure", "enabled_input_methods")).trim())
        val installed = ShizukuPackageInstallerUtils.getInstalledPackages(0, userId)
            .filter { (it.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_INSTALLED != 0 }.map { it.packageName }.toSet()
        val admins = activeAdmins(userId)
        if (profile.kind == io.github.samolego.canta.util.apps.UserProfile.Kind.WORK) protected += admins
        val snapshot = SafetySnapshot(protected, roles, keyboards, admins, installed)
        val json = BloatListRepository(context).load(false)
        val metadata = buildMap {
            for (name in json.keys()) json.optJSONObject(name)?.let { put(name, BloatData.fromJson(it)) }
        }
        return Baseline(snapshot, metadata)
    }

    suspend fun requireAllowed(name: String, userId: Int, approved: Set<String>, removing: Boolean = false) {
        val report = inspect(listOf(name), userId, removing).getValue(name)
        check(report.permits(approved)) {
            when {
                report.protected -> context.getString(R.string.safety_protected, name)
                report.error != null -> report.error
                else -> context.getString(R.string.safety_confirmation_required, name)
            }
        }
    }

    suspend fun requireUnprotected(name: String, userId: Int) {
        val report = inspect(listOf(name), userId).getValue(name)
        check(!report.protected && report.error == null) { report.error ?: context.getString(R.string.safety_protected, name) }
    }

    private suspend fun checked(args: List<String>): String {
        val result = shell.exec(args)
        check(result.success) { result.message }
        return result.stdout
    }

    private suspend fun handlers(userId: Int, intent: List<String>): Set<String> {
        val output = checked(listOf("cmd", "package", "query-activities", "--brief", "--components", "--query-flags", "8704", "--user", "$userId") + intent)
        return output.lineSequence().map { it.trim() }.filter { '/' in it }.map { it.substringBefore('/') }.toSet()
    }

    private suspend fun legacyRole(role: String, userId: Int): Set<String> = when (role) {
        "ASSISTANT" -> checked(listOf("settings", "--user", "$userId", "get", "secure", "assistant"))
            .trim().takeIf { '/' in it }?.substringBefore('/')?.let { setOf(it) }.orEmpty()
        else -> {
            val intent = when (role) {
                "HOME" -> listOf("-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME")
                "SMS" -> listOf("-a", "android.intent.action.SENDTO", "-d", "smsto:1")
                "DIALER" -> listOf("-a", "android.intent.action.DIAL", "-d", "tel:1")
                else -> listOf("-a", "android.intent.action.VIEW", "-d", "https://example.invalid")
            }
            val output = checked(listOf("cmd", "package", "resolve-activity", "--brief", "--components", "--user", "$userId") + intent)
            output.lineSequence().map { it.trim() }.filter { '/' in it }.map { it.substringBefore('/') }.toSet()
        }
    }

    internal fun activeAdmins(userId: Int): Set<String> {
        HiddenApiAccess.ensureReady()
        val stub = Class.forName("android.app.admin.IDevicePolicyManager\$Stub")
        val service = stub.getMethod("asInterface", IBinder::class.java).invoke(null,
            ShizukuBinderWrapper(SystemServiceHelper.getSystemService(Context.DEVICE_POLICY_SERVICE)))
        @Suppress("UNCHECKED_CAST")
        val admins = HiddenApiBypass.invoke(Class.forName("android.app.admin.IDevicePolicyManager"), service,
            "getActiveAdmins", userId) as List<ComponentName>?
        return admins.orEmpty().map { it.packageName }.toSet()
    }

    companion object { private const val BASELINE_TTL_MS = 60_000L }
}
