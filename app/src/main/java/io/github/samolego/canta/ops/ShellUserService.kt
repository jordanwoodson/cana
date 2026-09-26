package io.github.samolego.canta.ops

import androidx.annotation.Keep
import kotlin.system.exitProcess

/** Instantiated by Shizuku as shell/root, never registered as an exported Android service. */
@Keep
class ShellUserService : IShellService.Stub() {
    @Synchronized
    override fun exec(argv: Array<String>, timeoutMs: Long): ShellResult {
        if (argv.firstOrNull() == "cana-privacy") {
            return try {
                val command = PrivacyCommand.parse(argv.drop(1))
                val path = PrivacyPlatform.packageInfo("io.github.jordanwoodson.cana", command.ownerUserId)
                    .applicationInfo!!.sourceDir
                val output = ProcessRunner.exec(listOf("/system/bin/app_process", "/system/bin",
                    "io.github.samolego.canta.ops.PrivacyRecovery") + argv.drop(1), timeoutMs,
                    mapOf("CLASSPATH" to path))
                ShellResult(output.exitCode, output.stdout, output.stderr)
            } catch (e: Exception) { ShellResult(125, "", (e.cause ?: e).message ?: "Privacy interface unavailable") }
        }
        // Call fixed platform tools directly; user-supplied arguments never enter a shell parser.
        if (argv.isEmpty() || argv[0] !in allowedCommands) {
            return ShellResult(126, "", "Unsupported command")
        }
        val output = ProcessRunner.exec(listOf("/system/bin/${argv[0]}") + argv.drop(1), timeoutMs)
        return ShellResult(output.exitCode, output.stdout, output.stderr)
    }

    override fun destroy() = exitProcess(0)

    companion object {
        private val allowedCommands = setOf("cmd", "settings", "appops", "am", "pm", "dumpsys", "id")
    }
}
