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
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.tasks.await

/**
 * Authentication for the learner's personal account.
 *
 * Supports both Google Play Services GoogleSignInClient (Native Universal Account Picker)
 * and Jetpack CredentialManager.
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

    const val DEFAULT_WEB_CLIENT_ID = "836170376747-1ctsqum4pd34hf3bcvvvdkg42t7f6ni5.apps.googleusercontent.com"

    var webClientId: String = DEFAULT_WEB_CLIENT_ID

    fun resolveEffectiveWebClientId(context: Context): String {
        if (webClientId.isNotBlank()) return webClientId.trim()
        val resId = try {
            context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        } catch (e: Exception) { 0 }
        val fromRes = if (resId != 0) runCatching { context.getString(resId) }.getOrNull() else null
        return fromRes?.takeIf { it.isNotBlank() } ?: DEFAULT_WEB_CLIENT_ID
    }

    val googleSignInAvailable: Boolean get() = true

    /**
     * Build the standard GoogleSignInClient used by all Android apps to show the native account picker.
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
        // Sign out first so the user can choose from all accounts every time
        runCatching { client.signOut() }
        return client.signInIntent
    }

    /**
     * Ensure SOME Firebase user exists — call once at app startup.
     */
    suspend fun ensureSignedIn(): FirebaseUser? {
        auth.currentUser?.let { return it }
        return runCatching { auth.signInAnonymously().await().user }.getOrNull()
    }

    /**
     * Authenticate or link with Google ID Token in Firebase Auth.
     */
    suspend fun signInWithIdToken(idToken: String): Result<FirebaseUser?> = runCatching {
        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
        val current = auth.currentUser
        val user = if (current != null && current.isAnonymous) {
            try {
                current.linkWithCredential(firebaseCredential).await().user
            } catch (e: Exception) {
                // If already linked to another account, sign in directly with that account
                auth.signInWithCredential(firebaseCredential).await().user
            }
        } else {
            auth.signInWithCredential(firebaseCredential).await().user
        }
        user ?: throw IllegalStateException("فشل التحقق من هوية Google لدى Firebase")
    }

    /**
     * Fallback or direct Credential Manager sign in
     */
    suspend fun signInWithCredentialManager(context: Context): Result<FirebaseUser?> = runCatching {
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

    /** Sign out of Google and drop back to a fresh anonymous session. */
    suspend fun signOut(context: Context? = null) {
        if (context != null) {
            runCatching { getGoogleSignInClient(context).signOut().await() }
        }
        auth.signOut()
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

    fun refresh() {
        uid = CloudAuth.uid
        isAnonymous = CloudAuth.isAnonymous
        displayName = CloudAuth.displayName
        email = CloudAuth.email
        photoUrl = CloudAuth.photoUrl
    }
}
