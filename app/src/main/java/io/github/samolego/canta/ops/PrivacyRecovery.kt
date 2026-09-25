package io.github.samolego.canta.ops

import androidx.annotation.Keep
import kotlin.system.exitProcess

/** The same narrow UID adapter is available from an exported recovery script over adb. */
@Keep
object PrivacyRecovery {
    @JvmStatic fun main(args: Array<String>) {
        try { println(PrivacyPlatform.exec(args.toList())) }
        catch (e: Throwable) {
            System.err.println((e.cause ?: e).message ?: "Privacy recovery failed")
            exitProcess(1)
        }
    }
}
