package io.github.samolego.canta.util

/** A debloat request must never downgrade a shared APK or request removal for all users. */
internal object UninstallSequence {
    suspend fun execute(
        resetFirst: Boolean,
        uninstallFlags: Int,
        @Suppress("UNUSED_PARAMETER") hasUpdates: () -> Boolean?,
        uninstall: suspend (Int) -> PackageInstallerResult.Result,
    ): PackageInstallerResult.Result {
        if (resetFirst) return PackageInstallerResult.Result(false,
            "Uninstall only affects the selected profile. Remove leftover updates separately.")
        if (uninstallFlags and 5.inv() != 0) return PackageInstallerResult.Result(false,
            "Refused an uninstall that is not scoped to one profile")
        // DELETE_SYSTEM_APP is safe for ordinary apps too, and closes a package-update race.
        return uninstall(uninstallFlags or 4)
    }
}
