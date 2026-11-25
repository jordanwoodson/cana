package io.github.samolego.canta.ops

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.IBinder
import io.github.samolego.canta.R
import io.github.samolego.canta.data.SettingsStore
import io.github.samolego.canta.util.RemovalRecommendation
import io.github.samolego.canta.util.BloatListRepository
import io.github.samolego.canta.util.shizuku.ShizukuPackageInstallerUtils
import io.github.samolego.canta.util.shizuku.ShizukuUserUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lsposed.hiddenapibypass.HiddenApiBypass
import io.github.samolego.canta.util.HiddenApiAccess
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/** Unavailable safety data refuses mutation; an empty successful query means no holder/admin. */
class SafetyInspector(context: Context, private val shell: ShellRunner) {
    private val context = context.applicationContext
    val alwaysProtected = SafetyPolicy.protectedPackages(context.packageName)

    private val lists = BloatListRepository(context)

    suspend fun inspect(names: List<String>, userId: Int, removing: Boolean = false): Map<String, SafetyAssessment> = withContext(Dispatchers.IO) {
        if (names.all { it in alwaysProtected }) return@withContext names.associateWith { SafetyAssessment(protected = true) }
        try {
            val settings = SettingsStore.getInstance().snapshot()
            val metadata = lists.guidance(settings.bloatListUrl)
            val snapshot = querySnapshot(userId)
            val allowUnsafe = settings.allowUnsafeUninstalls
            names.associateWith { name ->
                val data = metadata[name]
                val dependents = SafetyPolicy.installedDependents(name, metadata, snapshot.installedPackages)
                val assessment = SafetyPolicy.assess(name, snapshot, dependents, userId)
                if (removing && !allowUnsafe && data?.removal == RemovalRecommendation.UNSAFE)
                    assessment.copy(error = context.getString(R.string.unsafe_uninstall_blocked)) else assessment
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            names.associateWith { SafetyAssessment(protected = it in alwaysProtected,
                error = context.getString(R.string.safety_query_failed, (e.cause ?: e).message.orEmpty())) }
        }
    }

    private suspend fun querySnapshot(userId: Int): SafetySnapshot {
        val profile = ShizukuUserUtils.getUsers().firstOrNull { it.id == userId }
            ?: error(context.getString(R.string.profile_missing))
        check(!profile.blocksShell && !profile.blocksUninstall) { context.getString(R.string.safety_profile_restricted) }
        val protected = alwaysProtected.toMutableSet()
        protected += ShizukuPackageInstallerUtils.activityHandlers(Intent(Intent.ACTION_INSTALL_PACKAGE)
            .setDataAndType(Uri.parse("content://cana.invalid/update.apk"), "application/vnd.android.package-archive"), userId)
        protected += ShizukuPackageInstallerUtils.activityHandlers(Intent("android.intent.action.MANAGE_PERMISSIONS"), userId)
        protected += ShizukuPackageInstallerUtils.activityHandlers(Intent(android.provider.Settings.ACTION_SETTINGS), userId)
        if (Build.VERSION.SDK_INT >= 29) ShizukuPackageInstallerUtils.permissionControllerPackage()?.let { protected += it }
        val roles = listOf("HOME", "SMS", "DIALER", "BROWSER", "ASSISTANT").associateWith { role ->
            if (Build.VERSION.SDK_INT >= 29) roleHolders(role, userId) else legacyRole(role, userId)
        }
        val keyboards = SafetyPolicy.keyboardPackages(
            checked(listOf("settings", "--user", "$userId", "get", "secure", "default_input_method")).trim(),
            checked(listOf("settings", "--user", "$userId", "get", "secure", "enabled_input_methods")).trim())
        val installed = ShizukuPackageInstallerUtils.getInstalledPackages(0, userId)
            .filter { (it.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_INSTALLED != 0 }.map { it.packageName }.toSet()
        val admins = activeAdmins(userId)
        if (profile.kind == io.github.samolego.canta.util.apps.UserProfile.Kind.WORK) protected += admins
        return SafetySnapshot(protected, roles, keyboards, admins, installed)
    }

    /** Android 10 introduced the role service before its shell exposed a read command. */
    private fun roleHolders(role: String, userId: Int): Set<String> {
        HiddenApiAccess.ensureReady()
        val type = Class.forName("android.app.role.IRoleManager")
        val stub = Class.forName("android.app.role.IRoleManager\$Stub")
        val manager = stub.getMethod("asInterface", IBinder::class.java).invoke(null,
            ShizukuBinderWrapper(SystemServiceHelper.getSystemService("role")))
        @Suppress("UNCHECKED_CAST")
        return (HiddenApiBypass.invoke(type, manager, "getRoleHoldersAsUser", "android.app.role.$role", userId)
            as List<String>).toSet()
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

}
