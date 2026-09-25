package io.github.samolego.canta.util.apps

import android.os.Process

/**
 * An Android user or profile (personal, work, clone, private space ...) whose apps Canta can
 * manage.
 */
data class UserProfile(
    val id: Int,
    val name: String?,
    val kind: Kind,
    /**
     * Shizuku runs as shell (adb) and the profile's admin set DISALLOW_DEBUGGING_FEATURES, so
     * Android refuses every shell attempt to modify apps in this profile.
     */
    val blocksShell: Boolean = false,
    /** The profile's admin set DISALLOW_UNINSTALL_APPS. */
    val blocksUninstall: Boolean = false,
) {
    enum class Kind {
        PERSONAL,
        WORK,
        CLONE,
        PRIVATE,
        USER,
        OTHER,
    }

    companion object {
        /** Id of the user Canta itself runs in (UserHandle.PER_USER_RANGE = 100000). */
        val currentUserId: Int = Process.myUid() / 100_000
    }
}
