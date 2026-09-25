package io.github.samolego.canta.ops

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal object ProcessRunner {
    data class Output(val exitCode: Int, val stdout: String, val stderr: String)

    fun exec(argv: List<String>, timeoutMs: Long): Output {
        if (argv.isEmpty() || argv.size > 128 || argv.sumOf { it.length } > 32_768 ||
            argv.any { '\u0000' in it }) return Output(2, "", "Invalid command arguments")
        val process = try {
            ProcessBuilder(argv).start()
        } catch (e: Exception) {
            return Output(127, "", e.message ?: e.javaClass.simpleName)
        }
        val readers = Executors.newFixedThreadPool(2) { task ->
            Thread(task, "CanaCommandOutput").apply { isDaemon = true }
        }
        try {
            process.outputStream.close()
            val stdout = readers.submit<Captured> { capture(process.inputStream) }
            val stderr = readers.submit<Captured> { capture(process.errorStream) }
            val finished = process.waitFor(timeoutMs.coerceIn(1, 30_000), TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                process.waitFor(500, TimeUnit.MILLISECONDS)
            }
            val out = stdout.get(500, TimeUnit.MILLISECONDS)
            val err = stderr.get(500, TimeUnit.MILLISECONDS)
            return when {
                !finished -> Output(124, out.text, err.text + "\nCommand timed out; outcome must be verified")
                out.truncated || err.truncated -> Output(125, out.text, err.text + "\nCommand exceeded output limit")
                else -> Output(process.exitValue(), out.text, err.text)
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            return Output(130, "", "Command interrupted; outcome must be verified")
        } catch (e: Exception) {
            return Output(125, "", e.message ?: "Could not capture command output")
        } finally {
            process.destroyForcibly()
            process.inputStream.close()
            process.errorStream.close()
            readers.shutdownNow()
        }
    }

    private data class Captured(val text: String, val truncated: Boolean)

    private fun capture(stream: InputStream): Captured {
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var truncated = false
        while (true) {
            val count = stream.read(buffer)
            if (count == -1) break
            val allowed = minOf(count, 65_536 - bytes.size())
            if (allowed < count) truncated = true
            bytes.write(buffer, 0, allowed)
        }
        return Captured(bytes.toString("UTF-8"), truncated)
    }
}
