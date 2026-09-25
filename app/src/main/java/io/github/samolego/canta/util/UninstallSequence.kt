package io.github.samolego.canta.util

/** Coordinates reset and uninstall while keeping the PackageInstaller status at each boundary. */
internal object UninstallSequence {
    fun execute(
        resetFirst: Boolean,
        uninstallFlags: Int,
        hasUpdates: () -> Boolean?,
        uninstall: (Int) -> PackageInstallerResult.Result,
    ): PackageInstallerResult.Result {
        if (resetFirst) {
            // Without DELETE_SYSTEM_APP Android removes the update for all users.
            val reset = uninstall(0)
            if (!reset.success) return reset
            when (hasUpdates()) {
                true -> return PackageInstallerResult.Result(false, "System update is still present after reset")
                null -> return PackageInstallerResult.Result(false, "Could not verify system update removal")
                false -> Unit
            }
        }
        return uninstall(uninstallFlags)
    }
}
