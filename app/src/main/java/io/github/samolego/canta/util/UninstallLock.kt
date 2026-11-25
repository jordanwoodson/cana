package io.github.samolego.canta.util

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import java.util.UUID
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getString
import androidx.fragment.app.FragmentActivity
import io.github.samolego.canta.R

private const val TAG = "UninstallLock"

fun showBiometricPrompt(
    context: Context,
    onError: () -> Unit = {},
    onSuccess: () -> Unit
): () -> Unit {
    val activity = context as FragmentActivity
    if (Build.VERSION.SDK_INT < 30) {
        // Android 9 and 10 cannot combine strong biometrics and device credentials in BiometricPrompt.
        @Suppress("DEPRECATION")
        val intent = context.getSystemService(KeyguardManager::class.java)
            .createConfirmDeviceCredentialIntent(context.getString(R.string.auth_required), null)
        if (intent == null) {
            onError()
            return {}
        }
        lateinit var unregister: () -> Unit
        val launcher = activity.activityResultRegistry.register(UUID.randomUUID().toString(),
            ActivityResultContracts.StartActivityForResult()) { result ->
            unregister()
            if (result.resultCode == Activity.RESULT_OK) onSuccess() else onError()
        }
        unregister = launcher::unregister
        try { launcher.launch(intent) } catch (_: android.content.ActivityNotFoundException) {
            unregister()
            onError()
        }
        return unregister
    }

    val executor = ContextCompat.getMainExecutor(context)

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(getString(context, R.string.auth_required))
        .setSubtitle(getString(context, R.string.auth_required_description))
        .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
        .build()


    val biometricManager = BiometricManager.from(context)
    val authStatus = biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
    if (authStatus != BiometricManager.BIOMETRIC_SUCCESS) {
        LogUtils.e(TAG, "Device cannot use biometrics. Auth status: $authStatus")

    }

    val biometricPrompt = BiometricPrompt(activity, executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(
                result: BiometricPrompt.AuthenticationResult
            ) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError()
                LogUtils.e(TAG, "An error occurred while trying to authenticate. Code: $errorCode, message: $errString")
            }

            override fun onAuthenticationFailed() {
                LogUtils.w(TAG, "Authentication failed!")
            }
        })

    biometricPrompt.authenticate(promptInfo)
    return biometricPrompt::cancelAuthentication
}
