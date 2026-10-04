package io.github.samolego.canta.ops

import io.github.samolego.canta.util.PackageInstallerResult

internal data class UpdateState(val updated: Boolean, val installed: Boolean)

internal object UpdateCleanupSequence {
    suspend fun execute(
        before: UpdateState,
        readState: () -> UpdateState?,
        reset: suspend () -> PackageInstallerResult.Result,
        installExisting: suspend () -> PackageInstallerResult.Result,
        uninstallForUser: suspend () -> PackageInstallerResult.Result,
    ): PackageInstallerResult.Result {
        if (!before.updated) return PackageInstallerResult.Result(true, null)
        if (before.installed) return PackageInstallerResult.Result(false, "App is still installed in the selected profile")
        suspend fun attempt(action: suspend () -> PackageInstallerResult.Result) = try { action() }
            catch (e: Exception) { PackageInstallerResult.Result(false, (e.cause ?: e).message) }
        fun state() = try { readState() } catch (_: Exception) { null }

        // A thrown preflight/isolation refusal is not an Android installer failure. Never
        // respond to that refusal by installing the app as a fallback.
        var fallbackAllowed = true
        var result = try { reset() }
            catch (e: PackageMutationRefused) { return PackageInstallerResult.Result(false, e.message) }
            catch (e: Exception) {
                // Verification may throw after Android changed state. Inspect/repair below,
                // but do not turn an unknown transport/read failure into another installation.
                fallbackAllowed = false
                PackageInstallerResult.Result(false, (e.cause ?: e).message)
            }
        if (result.outcomeUnknown) return result
        var installedForFallback = false
        if (!result.success && fallbackAllowed) {
            val install = attempt(installExisting)
            if (install.outcomeUnknown) return install
            // Even a failed callback may have changed state; always inspect and repair below.
            installedForFallback = install.success
            result = if (install.success) attempt(reset) else install
            if (result.outcomeUnknown) return result
        }
        val afterReset = state()
        if (afterReset?.installed == true || (afterReset == null && installedForFallback)) {
            val repair = attempt(uninstallForUser)
            if (!repair.success) return repair
        }
        if (!result.success) return result
        val verified = state()
            ?: return PackageInstallerResult.Result(false, "Cannot verify package state after removing updates")
        if (verified.updated || verified.installed) {
            return PackageInstallerResult.Result(false, "Update removal did not preserve the requested package state")
        }
        return PackageInstallerResult.Result(true, null)
    }
}
