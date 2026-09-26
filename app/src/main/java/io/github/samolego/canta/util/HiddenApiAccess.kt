package io.github.samolego.canta.util

import org.lsposed.hiddenapibypass.HiddenApiBypass

/** These cover the package, profile, safety and privacy adapters in each process. */
internal class HiddenApiInitializer(private val register: (Array<String>) -> Boolean) {
    private var ready = false
    @Synchronized fun ensureReady() {
        if (ready) return
        check(register(arrayOf("Landroid/content/pm/", "Landroid/os/IUserManager",
            "Landroid/app/admin/IDevicePolicyManager", "Landroid/net/", "Landroid/permission/"))) {
            "Android restricted API access could not be initialized"
        }
        ready = true
    }
}

object HiddenApiAccess {
    private val initializer = HiddenApiInitializer { HiddenApiBypass.setHiddenApiExemptions(*it) }
    fun ensureReady() = initializer.ensureReady()
}
