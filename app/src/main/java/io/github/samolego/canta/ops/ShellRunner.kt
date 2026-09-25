package io.github.samolego.canta.ops

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import io.github.samolego.canta.BuildConfig
import io.github.samolego.canta.util.LogUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ShellRunner(context: Context) {
    private val args = Shizuku.UserServiceArgs(ComponentName(context, ShellUserService::class.java))
        .tag("cana-shell").daemon(false).processNameSuffix("shell")
        .debuggable(BuildConfig.DEBUG).version(BuildConfig.VERSION_CODE)
    private val mutex = Mutex()
    @Volatile private var service: IShellService? = null
    private var connection: ServiceConnection? = null

    suspend fun exec(argv: List<String>, timeoutMs: Long = 15_000): ShellResult = mutex.withLock {
        LogUtils.i("ShellRunner", "exec ${argv.joinToString(" ")}")
        val result = try {
            if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                ShellResult(126, "", "Shizuku is not running or permission is missing")
            } else {
                val remote = connect()
                withContext(Dispatchers.IO) { remote.exec(argv.toTypedArray(), timeoutMs.coerceIn(1, 30_000)) }
            }
        } catch (e: TimeoutCancellationException) {
            ShellResult(124, "", "Timed out connecting to Shizuku")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            service = null
            ShellResult(125, "", (e.cause ?: e).message ?: "Shizuku command failed")
        }
        if (result.success) LogUtils.i("ShellRunner", "exit ${result.exitCode}: ${result.message}")
        else LogUtils.e("ShellRunner", "exit ${result.exitCode}: ${result.message}")
        result
    }

    private suspend fun connect(): IShellService {
        service?.takeIf { it.asBinder().isBinderAlive }?.let { return it }
        return withContext(Dispatchers.Main) {
            withTimeout(15_000) {
                suspendCancellableCoroutine { continuation ->
                    connection?.let { Shizuku.unbindUserService(args, it, false) }
                    val callback = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                            if (!continuation.isActive) return
                            val remote = IShellService.Stub.asInterface(binder)
                            service = remote
                            continuation.resume(remote)
                        }

                        override fun onServiceDisconnected(name: ComponentName) {
                            service = null
                            if (continuation.isActive) {
                                continuation.resumeWithException(IllegalStateException("Shizuku service disconnected"))
                            }
                        }
                    }
                    connection = callback
                    continuation.invokeOnCancellation {
                        runCatching { Shizuku.unbindUserService(args, callback, false) }
                    }
                    try {
                        Shizuku.bindUserService(args, callback)
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                }
            }
        }
    }
}
