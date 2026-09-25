package io.github.samolego.canta.util.shizuku

import android.content.Context
import android.os.IBinder
import io.github.samolego.canta.util.LogUtils
import io.github.samolego.canta.util.apps.UserProfile
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * Queries users / profiles on the device through Shizuku, as Canta itself can only see the
 * profile it runs in.
 */
object ShizukuUserUtils {
    private const val TAG = "ShizukuUserUtils"

    private const val SHELL_UID = 2000

    // See frameworks/base/core/java/android/content/pm/UserInfo.java
    private const val FLAG_MANAGED_PROFILE = 0x00000020
    private const val USER_TYPE_PROFILE_PREFIX = "android.os.usertype.profile."
    private const val USER_TYPE_PROFILE_MANAGED = "android.os.usertype.profile.MANAGED"
    private const val USER_TYPE_PROFILE_CLONE = "android.os.usertype.profile.CLONE"
    private const val USER_TYPE_PROFILE_PRIVATE = "android.os.usertype.profile.PRIVATE"

    // See UserManager.DISALLOW_*
    private const val DISALLOW_DEBUGGING_FEATURES = "no_debugging_features"
    private const val DISALLOW_UNINSTALL_APPS = "no_uninstall_apps"

    private val USER_MANAGER: Any by lazy {
        HiddenApiBypass.addHiddenApiExemptions(
            "Landroid/os/IUserManager",
            "Landroid/content/pm/UserInfo",
        )

        Class.forName("android.os.IUserManager\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(
                null,
                ShizukuBinderWrapper(SystemServiceHelper.getSystemService(Context.USER_SERVICE))
            )!!
    }

    /** Lists all (fully created) users and profiles on the device. Requires Shizuku permission. */
    fun getUsers(): List<UserProfile> {
        val userManager = USER_MANAGER

        // Android 9 & 10: getUsers(excludeDying)
        // Android 11+:    getUsers(excludePartial, excludeDying, excludePreCreated)
        val getUsers = userManager.javaClass.methods
            .filter { method ->
                method.name == "getUsers" &&
                        method.parameterTypes.all { it == Boolean::class.javaPrimitiveType }
            }
            .maxBy { it.parameterCount }
        val excludeAll = Array<Any>(getUsers.parameterCount) { true }

        @Suppress("UNCHECKED_CAST")
        val users = getUsers.invoke(userManager, *excludeAll) as List<Any>
        val shizukuIsShell = Shizuku.getUid() == SHELL_UID

        return users.map { toUserProfile(it, userManager, shizukuIsShell) }.sortedBy { it.id }
    }

    private fun toUserProfile(userInfo: Any, userManager: Any, shizukuIsShell: Boolean): UserProfile {
        val userInfoClass = userInfo.javaClass
        val id = userInfoClass.getField("id").getInt(userInfo)
        val name = userInfoClass.getField("name").get(userInfo) as String?
        val flags = userInfoClass.getField("flags").getInt(userInfo)
        // Only present on Android 11+
        val userType = runCatching { userInfoClass.getField("userType").get(userInfo) as String? }
            .getOrNull()

        val kind = when {
            userType == USER_TYPE_PROFILE_MANAGED || (flags and FLAG_MANAGED_PROFILE) != 0 ->
                UserProfile.Kind.WORK
            userType == USER_TYPE_PROFILE_CLONE -> UserProfile.Kind.CLONE
            userType == USER_TYPE_PROFILE_PRIVATE -> UserProfile.Kind.PRIVATE
            id == 0 -> UserProfile.Kind.PERSONAL
            userType?.startsWith(USER_TYPE_PROFILE_PREFIX) == true -> UserProfile.Kind.OTHER
            else -> UserProfile.Kind.USER
        }

        return UserProfile(
            id = id,
            name = name,
            kind = kind,
            // Android only enforces this restriction against the shell uid, not root
            blocksShell = shizukuIsShell &&
                    hasUserRestriction(userManager, DISALLOW_DEBUGGING_FEATURES, id),
            blocksUninstall = hasUserRestriction(userManager, DISALLOW_UNINSTALL_APPS, id),
        )
    }

    private fun hasUserRestriction(userManager: Any, restriction: String, userId: Int): Boolean {
        return try {
            userManager.javaClass
                .getMethod("hasUserRestriction", String::class.java, Int::class.javaPrimitiveType)
                .invoke(userManager, restriction, userId) as Boolean
        } catch (e: Exception) {
            LogUtils.w(TAG, "Failed to check '$restriction' for user $userId: ${e.cause ?: e}")
            false
        }
    }
}
