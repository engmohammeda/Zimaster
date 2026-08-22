package com.zmastery.english.ui.components

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Device-credential gate for destructive actions.
 *
 * Uses the OS confirm-credential screen, which accepts the user's fingerprint,
 * face, PIN, pattern or password — whatever they already have set up. This is
 * deliberately NOT the androidx.biometric library: KeyguardManager needs no
 * extra dependency, no FragmentActivity, and never locks out a user who has a
 * PIN but no registered fingerprint.
 *
 * When the device has no lock screen at all we cannot authenticate anything,
 * so the caller falls back to a typed confirmation phrase instead.
 */
object DeviceAuth {

    /** True when the device has a PIN / pattern / password / biometric set. */
    fun isAvailable(ctx: Context): Boolean = runCatching {
        val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        km.isDeviceSecure
    }.getOrDefault(false)
}

/**
 * Remember an authentication launcher.
 *
 * @return a function that takes an `onSuccess` callback and starts the system
 *         prompt. If the device is unsecured the callback is never invoked and
 *         `onUnavailable` fires so the UI can show its fallback.
 */
@Composable
fun rememberDeviceAuth(
    title: String,
    subtitle: String,
    onUnavailable: () -> Unit = {},
): (onSuccess: () -> Unit) -> Unit {
    val ctx = LocalContext.current
    // Holder so the result callback can reach the caller's lambda.
    val pending = remember { arrayOfNulls<(() -> Unit)>(1) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pending[0]?.invoke()
        }
        pending[0] = null
    }

    return remember(title, subtitle) {
        { onSuccess: () -> Unit ->
            val km = runCatching {
                ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            }.getOrNull()
            @Suppress("DEPRECATION")
            val intent = km?.takeIf { it.isDeviceSecure }
                ?.createConfirmDeviceCredentialIntent(title, subtitle)
            if (intent != null) {
                pending[0] = onSuccess
                runCatching { launcher.launch(intent) }.onFailure {
                    pending[0] = null
                    onUnavailable()
                }
            } else {
                onUnavailable()
            }
        }
    }
}
