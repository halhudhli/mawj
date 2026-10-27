package com.nuvio.app.features.luxo

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.nuvio.app.features.profiles.ProfileBiometricAuth
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val LUXO_UNLOCK_AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

internal actual fun luxoDeviceUnlockAvailable(): Boolean {
    val host = ProfileBiometricAuth.hostActivity() ?: return false
    return BiometricManager.from(host).canAuthenticate(LUXO_UNLOCK_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
}

internal actual suspend fun luxoDeviceUnlock(title: String, subtitle: String): Boolean {
    val host = ProfileBiometricAuth.hostActivity() ?: return true
    return suspendCancellableCoroutine { continuation ->
        val prompt = BiometricPrompt(
            host,
            ContextCompat.getMainExecutor(host),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (continuation.isActive) continuation.resume(false)
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(LUXO_UNLOCK_AUTHENTICATORS)
            .setConfirmationRequired(false)
            .build()
        continuation.invokeOnCancellation { prompt.cancelAuthentication() }
        prompt.authenticate(info)
    }
}
