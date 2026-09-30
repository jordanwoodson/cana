package io.github.samolego.canta.util.shizuku

import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import io.github.samolego.canta.SHIZUKU_PACKAGE_NAME
import io.github.samolego.canta.packageName
import io.github.samolego.canta.util.LogUtils
import rikka.shizuku.Shizuku
import rikka.sui.Sui

/** Process-owned listeners never capture an Activity, dialog or operation callback. */
object ShizukuPermission {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val authorization: ShizukuAuthorization = ShizukuAuthorization(::readAuthorization) { code ->
        Shizuku.requestPermission(code)
        mainHandler.postDelayed({ authorizationTimeout(code) }, 60_000)
    }
    val state = authorization.state

    init {
        runCatching { Sui.init(packageName) }
        Shizuku.addBinderReceivedListenerSticky { refresh() }
        Shizuku.addBinderDeadListener { refresh() }
        Shizuku.addRequestPermissionResultListener { code, result ->
            authorization.onResult(code, result == PackageManager.PERMISSION_GRANTED)
        }
    }

    private fun authorizationTimeout(code: Int) { authorization.onTimeout(code) }

    /** Call from the main thread; repeated taps reuse the in-flight request. */
    fun requestShizukuPermission() = authorization.request()
    fun refresh() = authorization.refresh()

    private fun readAuthorization(): AuthorizationState = try {
        when {
            !Shizuku.pingBinder() -> AuthorizationState.DISCONNECTED
            Shizuku.isPreV11() -> AuthorizationState.UNSUPPORTED
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> AuthorizationState.GRANTED
            Shizuku.shouldShowRequestPermissionRationale() -> AuthorizationState.BLOCKED
            else -> AuthorizationState.READY
        }
    } catch (e: Exception) {
        LogUtils.w("ShizukuPermission", "Unable to read authorization: ${e.message}")
        AuthorizationState.DISCONNECTED
    }

    fun isCantaAuthorized(): Boolean = readAuthorization() == AuthorizationState.GRANTED

    fun checkShizukuActive(packageManager: PackageManager): ShizukuStatus {
        // A live binder also covers Sui and compatible managers with another package name.
        val live = readAuthorization()
        if (live != AuthorizationState.DISCONNECTED) return ShizukuStatus.ACTIVE
        return try {
            packageManager.getPackageInfo(SHIZUKU_PACKAGE_NAME, 0)
            ShizukuStatus.NOT_ACTIVE
        } catch (_: PackageManager.NameNotFoundException) {
            ShizukuStatus.NOT_INSTALLED
        }
    }
}

enum class ShizukuStatus { ACTIVE, NOT_ACTIVE, NOT_INSTALLED }
