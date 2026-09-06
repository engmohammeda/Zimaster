package com.zmastery.english.cloud

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Representation of an authenticated user in Supabase.
 */
data class CloudUser(
    val uid: String,
    val email: String? = null,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val isAnonymous: Boolean = false,
    val isEmailVerified: Boolean = false,
)

/**
 * Authentication for the learner's personal account via Supabase Auth.
 *
 * Supports Jetpack CredentialManager, native GoogleSignInClient,
 * and Supabase anonymous / email authentication.
 */
object CloudAuth {
    private const val TAG = "CloudAuth"

    private val auth: Auth get() = SupabaseClientProvider.client.auth

    private fun UserInfo.toCloudUser(): CloudUser {
        val meta = userMetadata
        val name = meta?.get("full_name")?.jsonPrimitive?.content
            ?: meta?.get("name")?.jsonPrimitive?.content
            ?: meta?.get("display_name")?.jsonPrimitive?.content
        val avatar = meta?.get("avatar_url")?.jsonPrimitive?.content
            ?: meta?.get("picture")?.jsonPrimitive?.content
        val anon = identities.isNullOrEmpty() && email.isNullOrBlank()
        val verified = emailConfirmedAt != null
        return CloudUser(
            uid = id,
            email = email,
            displayName = name,
            photoUrl = avatar,
            isAnonymous = anon,
            isEmailVerified = verified
        )
    }

    val currentUser: CloudUser? get() = auth.currentUserOrNull()?.toCloudUser()
    val uid: String? get() = currentUser?.uid
    val isAnonymous: Boolean get() = currentUser?.isAnonymous ?: true

    val displayName: String? get() = currentUser?.displayName
    val email: String? get() = currentUser?.email
    val photoUrl: String? get() = currentUser?.photoUrl

    val isEmailVerified: Boolean get() = currentUser?.isEmailVerified ?: false

    const val DEFAULT_WEB_CLIENT_ID = "836170376747-1ctsqum4pd34hf3bcvvvdkg42t7f6ni5.apps.googleusercontent.com"

    var webClientId: String = DEFAULT_WEB_CLIENT_ID

    fun resolveEffectiveWebClientId(context: Context): String {
        val current = webClientId.trim()
        if (current.isNotBlank() && !current.contains("567438543557")) {
            return current
        }
        val resId = try {
            context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        } catch (e: Exception) { 0 }
        val fromRes = if (resId != 0) runCatching { context.getString(resId) }.getOrNull() else null
        return if (fromRes != null && fromRes.isNotBlank() && !fromRes.contains("567438543557")) {
            fromRes
        } else {
            DEFAULT_WEB_CLIENT_ID
        }
    }

    val googleSignInAvailable: Boolean get() = true

    /**
     * Build standard GoogleSignInClient for account picker compatibility.
     */
    fun getGoogleSignInClient(context: Context): GoogleSignInClient {
        val effectiveClientId = resolveEffectiveWebClientId(context)
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(effectiveClientId)
            .requestEmail()
            .requestProfile()
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    fun getGoogleSignInIntent(context: Context): Intent {
        val client = getGoogleSignInClient(context)
        runCatching { client.signOut() }
        return client.signInIntent
    }

    /**
     * Ensure SOME Supabase user exists — call once at app startup.
     */
    suspend fun ensureSignedIn(): CloudUser? {
        currentUser?.let { return it }
        return runCatching {
            auth.signInAnonymously()
            currentUser
        }.getOrNull()
    }

    /**
     * Authenticate or link with Google ID Token in Supabase Auth.
     */
    suspend fun signInWithIdToken(idToken: String): Result<CloudUser?> = runCatching {
        auth.signInWith(IDToken) {
            this.idToken = idToken
            this.provider = Google
        }
        val user = currentUser
        user ?: throw IllegalStateException("فشل التحقق من هوية Google لدى Supabase")
    }

    /**
     * Sign in with Email and Password
     */
    suspend fun signInWithEmail(email: String, pass: String): Result<CloudUser?> = runCatching {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank() || pass.isBlank()) {
            throw IllegalArgumentException("يرجى إدخال البريد الإلكتروني وكلمة المرور")
        }
        auth.signInWith(Email) {
            this.email = trimmedEmail
            this.password = pass
        }
        currentUser ?: throw IllegalStateException("تعذّر تسجيل الدخول بالبريد الإلكتروني")
    }

    /**
     * Sign up (Create new account) with Email, Password and Display Name
     */
    suspend fun signUpWithEmail(email: String, pass: String, name: String): Result<CloudUser?> = runCatching {
        val trimmedEmail = email.trim()
        val trimmedName = name.trim()
        if (trimmedEmail.isBlank() || pass.length < 6) {
            throw IllegalArgumentException("كلمة المرور يجب ألا تقل عن 6 أحرف")
        }
        auth.signUpWith(Email) {
            this.email = trimmedEmail
            this.password = pass
            if (trimmedName.isNotBlank()) {
                data = buildJsonObject {
                    put("full_name", trimmedName)
                    put("name", trimmedName)
                    put("display_name", trimmedName)
                }
            }
        }
        currentUser ?: throw IllegalStateException("تعذّر إنشاء الحساب")
    }

    /** Resend email verification link */
    suspend fun resendEmailVerification(): Result<Unit> = runCatching {
        val email = currentUser?.email ?: throw IllegalStateException("لا يوجد حساب مسجّل الدخول")
        if (isEmailVerified) return@runCatching
        auth.resendEmail(OtpType.Email.SIGNUP, email)
    }

    /** Reload user session from Supabase */
    suspend fun reloadCurrentUser(): Result<Unit> = runCatching {
        runCatching {
            auth.retrieveUserForCurrentSession(updateSession = true)
        }
    }

    /** Send Password Reset Email */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> = runCatching {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank()) {
            throw IllegalArgumentException("يرجى كتابة البريد الإلكتروني")
        }
        auth.resetPasswordForEmail(trimmedEmail)
    }

    /**
     * Jetpack Credential Manager sign in flow.
     * Nonce is intentionally not added to prevent invalid_nonce mismatches.
     */
    suspend fun signInWithCredentialManager(context: Context): Result<CloudUser?> = runCatching {
        val effectiveClientId = resolveEffectiveWebClientId(context)
        if (effectiveClientId.isBlank()) {
            throw IllegalStateException("يرجى إدخال Web Client ID")
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(effectiveClientId)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val credentialManager = CredentialManager.create(context)
        val result = try {
            credentialManager.getCredential(request = request, context = context)
        } catch (e: GetCredentialCancellationException) {
            Log.d(TAG, "User cancelled Google Sign-in")
            return@runCatching null
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Credential Manager error", e)
            throw IllegalStateException("تعذّر فتح نافذة الحسابات: ${e.message}", e)
        }

        val credential = result.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw IllegalStateException("نوع بيانات اعتماد غير متوقع")
        }

        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
        signInWithIdToken(googleIdTokenCredential.idToken).getOrThrow()
    }

    /** Sign out and revert to an anonymous session. */
    suspend fun signOut(context: Context? = null) {
        if (context != null) {
            runCatching { getGoogleSignInClient(context).signOut() }
        }
        runCatching { auth.signOut() }
        ensureSignedIn()
    }
}

/** Observable auth state for Compose UI */
class CloudAuthState {
    var uid by mutableStateOf<String?>(null)
        private set
    var isAnonymous by mutableStateOf(true)
        private set
    var displayName by mutableStateOf<String?>(null)
        private set
    var email by mutableStateOf<String?>(null)
        private set
    var photoUrl by mutableStateOf<String?>(null)
        private set
    var isEmailVerified by mutableStateOf(false)
        private set

    fun refresh() {
        uid = CloudAuth.uid
        isAnonymous = CloudAuth.isAnonymous
        displayName = CloudAuth.displayName
        email = CloudAuth.email
        photoUrl = CloudAuth.photoUrl
        isEmailVerified = CloudAuth.isEmailVerified
    }
}
