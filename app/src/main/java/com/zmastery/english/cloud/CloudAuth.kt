package com.zmastery.english.cloud

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import java.util.Base64

/**
 * Authentication for the learner's personal account.
 *
 * 1. On first run, we sign in ANONYMOUSLY if not yet signed in.
 * 2. When the user taps Google Sign-In, we launch CredentialManager to pick any Google account.
 * 3. The account is linked to the existing UID so no progress or stats are lost.
 */
object CloudAuth {
    private const val TAG = "CloudAuth"

    private val auth get() = Firebase.auth

    val currentUser: FirebaseUser? get() = auth.currentUser
    val uid: String? get() = auth.currentUser?.uid
    val isAnonymous: Boolean get() = auth.currentUser?.isAnonymous ?: true

    val displayName: String? get() = auth.currentUser?.displayName
    val email: String? get() = auth.currentUser?.email
    val photoUrl: String? get() = auth.currentUser?.photoUrl?.toString()

    /**
     * Web Client ID from Firebase Console -> Authentication -> Sign-in method -> Google
     */
    var webClientId: String = ""

    fun resolveEffectiveWebClientId(context: Context): String {
        if (webClientId.isNotBlank()) return webClientId.trim()
        return try {
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (resId != 0) context.getString(resId) else ""
        } catch (e: Exception) {
            ""
        }
    }

    val googleSignInAvailable: Boolean get() = true

    /**
     * Ensure SOME Firebase user exists — call once at app startup.
     */
    suspend fun ensureSignedIn(): FirebaseUser? {
        auth.currentUser?.let { return it }
        return runCatching { auth.signInAnonymously().await().user }.getOrNull()
    }

    /**
     * Launch Google Sign-In via Credential Manager and LINK it to the current
     * Firebase user so all local data carries over.
     */
    suspend fun signInWithGoogle(context: Context): Result<FirebaseUser?> = runCatching {
        val effectiveClientId = resolveEffectiveWebClientId(context)
        if (effectiveClientId.isBlank()) {
            throw IllegalStateException("يرجى إدخال Web Client ID من إعدادات الحساب أو لوحة Firebase")
        }

        val nonce = randomNonce()
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false) // Show all Google accounts on device
            .setServerClientId(effectiveClientId)
            .setAutoSelectEnabled(false) // Prompt account chooser bottom sheet
            .setNonce(nonce)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val credentialManager = CredentialManager.create(context)
        val result = try {
            credentialManager.getCredential(request = request, context = context)
        } catch (e: GetCredentialCancellationException) {
            Log.d(TAG, "User cancelled Google Sign-in")
            return@runCatching null // Cancelled by user - not an error
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Credential Manager error", e)
            throw IllegalStateException("تعذّر فتح نافذة اختيار الحساب: ${e.message}", e)
        }

        val credential = result.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw IllegalStateException("نوع بيانات اعتماد غير متوقع من Google")
        }

        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
        val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)

        val current = auth.currentUser
        val user = if (current != null && current.isAnonymous) {
            try {
                current.linkWithCredential(firebaseCredential).await().user
            } catch (e: Exception) {
                // If credential already linked to another user, sign into that user
                auth.signInWithCredential(firebaseCredential).await().user
            }
        } else {
            auth.signInWithCredential(firebaseCredential).await().user
        }
        user ?: throw IllegalStateException("فشل إتمام تسجيل الدخول بحساب Google")
    }

    /** Sign out of Google and drop back to a fresh anonymous session. */
    suspend fun signOut() {
        auth.signOut()
        ensureSignedIn()
    }

    private fun randomNonce(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
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

    fun refresh() {
        uid = CloudAuth.uid
        isAnonymous = CloudAuth.isAnonymous
        displayName = CloudAuth.displayName
        email = CloudAuth.email
        photoUrl = CloudAuth.photoUrl
    }
}
