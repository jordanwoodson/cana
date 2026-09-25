package io.github.samolego.canta.ops

import android.content.pm.PackageInfo
import android.os.Build
import android.os.IBinder
import org.json.JSONObject
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.SystemServiceHelper

/** Runs only inside the shell UserService/PC recovery process, using the caller's real uid.
 * The connectivity shell command resolves appIds, not profile UIDs. This narrow adapter
 * is needed for profile isolation; it never uses guessed Binder transaction numbers.
 */
internal object PrivacyPlatform {
    private fun service(type: String, name: String): Any = Class.forName("$type\$Stub")
        .getMethod("asInterface", IBinder::class.java).invoke(null, SystemServiceHelper.getSystemService(name))!!
    private fun invoke(type: String, name: String, method: String, vararg args: Any): Any? =
        HiddenApiBypass.invoke(Class.forName(type), service(type, name), method, *args)

    fun packageInfo(pkg: String, user: Int): PackageInfo {
        HiddenApiBypass.addHiddenApiExemptions("Landroid/content/pm/", "Landroid/net/", "Landroid/permission/")
        val flags: Any = if (Build.VERSION.SDK_INT >= 33) 0L else 0
        return invoke("android.content.pm.IPackageManager", "package", "getPackageInfo", pkg, flags, user) as? PackageInfo
            ?: error("Package is not installed in user $user")
    }

    fun exec(args: List<String>): String {
        require(args.size in 4..5)
        val action = args[0]
        val pkg = args[1]
        val user = args[2].toInt()
        require(user >= 0 && pkg.isNotBlank())
        val info = packageInfo(pkg, user)
        return when (action) {
            "desired-set", "metered-desired-set", "metered-set" -> {
                require(args.size == 5)
                val appId = info.applicationInfo!!.uid % 100_000
                require(appId == args[3].toInt()) { "App UID changed; inspect this package again" }
                val uid = PrivacyPolicy.uid(user, appId)
                fun command(vararg command: String): String {
                    val result = ProcessRunner.exec(command.toList(), 25_000)
                    check(result.exitCode == 0) { result.stderr.ifBlank { result.stdout } }
                    return result.stdout.trim()
                }
                if (action != "metered-set") {
                    val blocked = args[4].toBooleanStrict()
                    val output = command("/system/bin/content", "call", "--user", "0", "--uri", "content://io.github.jordanwoodson.cana.recovery",
                        "--method", if (action == "desired-set") "network-desired" else "metered-desired", "--extra", "package:s:$pkg", "--extra", "user:i:$user",
                        "--extra", "appId:i:$appId", "--extra", "blocked:b:$blocked")
                    check(output.contains("restored=true")) { output.ifBlank { "Cana could not restore the desired network state" } }
                    output
                } else {
                    val policy = args[4].toInt()
                    require(policy in setOf(0, 1, 4, 5))
                    for ((list, bit) in listOf("restrict-background-blacklist" to 1, "restrict-background-whitelist" to 4)) {
                        val current = uid in PrivacyPolicy.uidList(command("/system/bin/cmd", "netpolicy", "list", list))
                        if (current != (policy and bit != 0)) command("/system/bin/cmd", "netpolicy",
                            if (policy and bit != 0) "add" else "remove", list, "$uid")
                    }
                    val denied = uid in PrivacyPolicy.uidList(command("/system/bin/cmd", "netpolicy", "list", "restrict-background-blacklist"))
                    val allowed = uid in PrivacyPolicy.uidList(command("/system/bin/cmd", "netpolicy", "list", "restrict-background-whitelist"))
                    val actual = (if (denied) 1 else 0) or (if (allowed) 4 else 0)
                    check(actual == policy) { "Metered policy was not restored: expected $policy, actual $actual" }
                    JSONObject().put("meteredPolicy", actual).toString()
                }
            }
            "permission-flags", "permission-flags-list" -> {
                require(args.size == 4)
                fun flags(permission: String): String =
                if (Build.VERSION.SDK_INT < 30) {
                    invoke("android.content.pm.IPackageManager", "package", "getPermissionFlags", permission, pkg, user).toString()
                } else {
                    val type = "android.permission.IPermissionManager"
                    val method = Class.forName(type).declaredMethods.single { it.name == "getPermissionFlags" }
                    val parameters = if (method.parameterTypes.size == 3) arrayOf<Any>(pkg, permission, user)
                    else arrayOf<Any>(pkg, permission, if (method.parameterTypes[2] == String::class.java) "default:0" else 0, user)
                    invoke(type, "permissionmgr", "getPermissionFlags", *parameters).toString()
                }
                if (action == "permission-flags") flags(args[3]) else JSONObject().apply {
                    val permissions = args[3].split(',')
                    require(permissions.size <= 512)
                    permissions.forEach { put(it, flags(it).toInt()) }
                }.toString()
            }
            "network-get", "network-set" -> {
                check(Build.VERSION.SDK_INT >= 34) { "Per-profile network blocking requires Android 14 or newer" }
                val appId = info.applicationInfo!!.uid % 100_000
                require(appId == args[3].toInt()) { "App UID changed; inspect this package again" }
                val uid = PrivacyPolicy.uid(user, appId)
                val type = "android.net.IConnectivityManager"
                val chain = Class.forName("android.net.ConnectivityManager").getDeclaredField("FIREWALL_CHAIN_OEM_DENY_3").getInt(null)
                if (action == "network-set") {
                    require(args.size == 5)
                    require(pkg !in SafetyPolicy.protectedPackages("io.github.jordanwoodson.cana"))
                    val rule = args[4].toInt()
                    require(rule in 0..2)
                    if (rule == 2) invoke(type, "connectivity", "setFirewallChainEnabled", chain, true)
                    val effective = if (rule == 0) 1 else rule
                    if (invoke(type, "connectivity", "getUidFirewallRule", chain, uid) != effective)
                        invoke(type, "connectivity", "setUidFirewallRule", chain, uid, rule)
                }
                JSONObject().put("networkRule", invoke(type, "connectivity", "getUidFirewallRule", chain, uid))
                    .put("chainEnabled", invoke(type, "connectivity", "getFirewallChainEnabled", chain)).toString()
            }
            else -> error("Unsupported privacy command")
        }
    }
}
