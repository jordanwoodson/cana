package io.github.samolego.canta.ops

import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.samolego.canta.util.shizuku.ShizukuPackageInstallerUtils as Packages
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/** Probe the actual shell identity before exposing privacy controls. */
@RunWith(AndroidJUnit4::class)
class PrivacyCapabilityDeviceTest {
    private val pkg = "io.github.jordanwoodson.cana.fixture"
    private val permission = "android.permission.CAMERA"
    private val shell get() = CanaServices.getInstance().shell
    private suspend fun exec(vararg args: String): String {
        val result = shell.exec(args.toList())
        assertTrue(args.joinToString(" ") + ": " + result.message, result.success)
        return result.stdout.trim()
    }
    private fun binder(type: String, service: String): Any =
        Class.forName("$type\$Stub").getMethod("asInterface", IBinder::class.java)
            .invoke(null, ShizukuBinderWrapper(SystemServiceHelper.getSystemService(service)))!!
    private fun call(instance: Any, type: String, method: String, vararg args: Any): Any? =
        HiddenApiBypass.invoke(Class.forName(type), instance, method, *args)

    @Test fun permissionBackgroundMeteredAndUidFirewallAreProfileScoped() = runBlocking {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        withTimeout(30_000) { while (!Shizuku.pingBinder()) delay(100) }
        assertEquals(PackageManager.PERMISSION_GRANTED, Shizuku.checkSelfPermission())
        HiddenApiBypass.addHiddenApiExemptions("Landroid/net/", "Landroid/permission/")
        val permissionType = "android.permission.IPermissionManager"
        val permissionManager = binder(permissionType, "permissionmgr")
        val connectivityType = "android.net.IConnectivityManager"
        val connectivity = binder(connectivityType, "connectivity")
        Log.i("PrivacyProbe", Class.forName(connectivityType).declaredMethods.filter { "Firewall" in it.name }.joinToString("\n"))
        val chain = Class.forName("android.net.ConnectivityManager").getDeclaredField("FIREWALL_CHAIN_OEM_DENY_3").getInt(null)
        fun flags(user: Int) = call(permissionManager, permissionType, "getPermissionFlags", pkg, permission, "default:0", user) as Int
        fun uid(user: Int) = user * 100_000 + Packages.getPackageInfo(pkg, 0, user)!!.applicationInfo!!.uid % 100_000
        fun rule(user: Int) = call(connectivity, connectivityType, "getUidFirewallRule", chain, uid(user)) as Int
        val chainEnabled = call(connectivity, connectivityType, "getFirewallChainEnabled", chain) as Boolean
        for ((user, peer) in listOf(0 to 10, 10 to 0)) {
            val originalFlags = flags(user)
            val originalGranted = Packages.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS, user)!!.let {
                it.requestedPermissionsFlags!![it.requestedPermissions!!.indexOf(permission)] and PackageInfoGranted != 0
            }
            val peerFlags = flags(peer)
            val oldRule = rule(user)
            val peerRule = rule(peer)
            val bucket = exec("am", "get-standby-bucket", "--user", "$user", pkg)
            val oldOp = exec("cmd", "appops", "get", "--user", "$user", pkg, "RUN_ANY_IN_BACKGROUND")
            val oldBlacklist = exec("cmd", "netpolicy", "list", "restrict-background-blacklist")
            try {
                exec("pm", "clear-permission-flags", "--user", "$user", pkg, permission, "user-fixed", "user-set")
                exec("pm", "grant", "--user", "$user", pkg, permission)
                exec("pm", "revoke", "--user", "$user", pkg, permission)
                exec("pm", "set-permission-flags", "--user", "$user", pkg, permission, "user-fixed")
                assertEquals(2, flags(user) and 2)
                assertEquals(peerFlags, flags(peer))
                exec("cmd", "appops", "set", "--user", "$user", pkg, "RUN_ANY_IN_BACKGROUND", "ignore")
                assertTrue(exec("cmd", "appops", "get", "--user", "$user", pkg, "RUN_ANY_IN_BACKGROUND").contains("ignore"))
                if (bucket != "5") {
                    exec("am", "set-standby-bucket", "--user", "$user", pkg, "restricted")
                    assertEquals("45", exec("am", "get-standby-bucket", "--user", "$user", pkg))
                }
                exec("cmd", "netpolicy", "add", "restrict-background-blacklist", "${uid(user)}")
                assertTrue(exec("cmd", "netpolicy", "list", "restrict-background-blacklist").contains("${uid(user)}"))
                call(connectivity, connectivityType, "setFirewallChainEnabled", chain, true)
                call(connectivity, connectivityType, "setUidFirewallRule", chain, uid(user), 2)
                assertEquals(2, rule(user))
                assertEquals(peerRule, rule(peer))
                Log.i("PrivacyProbe", "Verified user=$user uid=${uid(user)} flags=${flags(user)} rule=${rule(user)} bucket=45 op=ignore")
            } finally {
                exec("pm", "clear-permission-flags", "--user", "$user", pkg, permission, "user-fixed", "user-set")
                exec("pm", if (originalGranted) "grant" else "revoke", "--user", "$user", pkg, permission)
                if (originalFlags and 1 != 0) exec("pm", "set-permission-flags", "--user", "$user", pkg, permission, "user-set")
                if (originalFlags and 2 != 0) exec("pm", "set-permission-flags", "--user", "$user", pkg, permission, "user-fixed")
                if (bucket != "5") exec("am", "set-standby-bucket", "--user", "$user", pkg, bucket)
                val op = Regex("RUN_ANY_IN_BACKGROUND: (\\w+)").find(oldOp)?.groupValues?.get(1) ?: "default"
                exec("cmd", "appops", "set", "--user", "$user", pkg, "RUN_ANY_IN_BACKGROUND", op)
                if (!Regex("\\b${uid(user)}\\b").containsMatchIn(oldBlacklist)) exec("cmd", "netpolicy", "remove", "restrict-background-blacklist", "${uid(user)}")
                call(connectivity, connectivityType, "setUidFirewallRule", chain, uid(user), oldRule)
                call(connectivity, connectivityType, "setFirewallChainEnabled", chain, chainEnabled)
            }
        }
    }
    private companion object { const val PackageInfoGranted = 2 }
}
