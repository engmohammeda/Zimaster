package com.zmastery.english.cloud

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
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
 * Authentication for the learner's single personal account.
 *
 * The app ALWAYS has a signed-in Firebase user, from the very first launch:
 *
 *  1. On first run (no user yet) we sign in ANONYMOUSLY. This alone is
 *     enough to sync progress + content to Firestore under a stable uid —
 *     no setup required, works immediately, completely free.
 *
 *  2. Whenever the learner wants a "real" account (so the same data can be
 *     restored on a new phone), they can link that anonymous account to
 *     Google Sign-In via [signInWithGoogle]. Linking (not replacing) means
 *     every document already written under the anonymous uid stays exactly
 *     where it is — nothing is lost or duplicated.
 *
 * Google Sign-In requires a Web Client ID that only exists once "Google" is
 * enabled as a Sign-in provider in the Firebase console (Authentication →
 * Sign-in method). Until that is configured, [googleSignInAvailable] is
 * false and the app quietly keeps using the anonymous account — the rest of
 * the app (Firestore sync) doesn't care which auth method backs the uid.
 */
object CloudAuth {

    private val auth get() = Firebase.auth

    /** Currently signed-in user, or null before the very first auth call resolves. */
    val currentUser: FirebaseUser? get() = auth.currentUser

    val uid: String? get() = auth.currentUser?.uid

    val isAnonymous: Boolean get() = auth.currentUser?.isAnonymous ?: true

    val displayName: String? get() = auth.currentUser?.displayName
    val email: String? get() = auth.currentUser?.email
    val photoUrl: String? get() = auth.currentUser?.photoUrl?.toString()

    /**
     * Set from Settings or defaulted from google-services.json Web Client ID.
     */
    var webClientId: String = "567438543557-93ce76v8d4kiqcf9scl8qk04tsf90num.apps.googleusercontent.com"

    val googleSignInAvailable: Boolean get() = webClientId.isNotBlank()

    /**
     * Ensure SOME Firebase user exists — call once at app startup. Anonymous
     * sign-in is instant, free, and requires no configuration at all.
     */
    suspend fun ensureSignedIn(): FirebaseUser? {
        auth.currentUser?.let { return it }
        return runCatching { auth.signInAnonymously().await().user }.getOrNull()
    }

    /**
     * Launch Google Sign-In via Credential Manager and LINK it to the current
     * (anonymous) Firebase user so existing synced data carries over. If the
     * Google credential already belongs to another Firebase account, falls
     * back to signing into THAT account instead (merge case).
     */
    suspend fun signInWithGoogle(context: Context): Result<FirebaseUser> = runCatching {
        require(googleSignInAvailable) { "Google Sign-In غير مفعّل بعد — أضف Web Client ID من الإعدادات" }
        val nonce = randomNonce()
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(true)
            .setNonce(nonce)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        val credentialManager = CredentialManager.create(context)
        val result = try {
            credentialManager.getCredential(request = request, context = context)
        } catch (e: GetCredentialException) {
            throw IllegalStateException("تعذّر فتح نافذة حسابات جوجل: ${e.message}", e)
        }
        val credential = result.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw IllegalStateException("نوع بيانات اعتماد غير متوقع من جوجل")
        }
        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
        val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)

        val current = auth.currentUser
        val user = if (current != null && current.isAnonymous) {
            try {
                current.linkWithCredential(firebaseCredential).await().user
            } catch (e: Exception) {
                // Credential already belongs to another account -> sign into that one.
                auth.signInWithCredential(firebaseCredential).await().user
            }
        } else {
            auth.signInWithCredential(firebaseCredential).await().user
        }
        user ?: throw IllegalStateException("فشل تسجيل الدخول بحساب جوجل")
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

/** Observable auth state for Compose UI (login banner, account row in Settings). */
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
