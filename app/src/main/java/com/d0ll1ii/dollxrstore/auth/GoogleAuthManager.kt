package com.d0ll1ii.dollxrstore.auth

import android.content.Context
import android.widget.Toast
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.d0ll1ii.dollxrstore.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

data class UserProfile(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val username: String = "",
    val isSignedIn: Boolean = false
)

object GoogleAuthManager {
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    private val _currentUserState = MutableStateFlow(UserProfile())
    val currentUserState: StateFlow<UserProfile> = _currentUserState.asStateFlow()

    init {
        try {
            auth.currentUser?.let { firebaseUser ->
                val name = firebaseUser.displayName ?: "DollXR User"
                val email = firebaseUser.email ?: ""
                val defaultUsername = "@" + name.lowercase().replace(" ", "_")
                _currentUserState.value = UserProfile(
                    uid = firebaseUser.uid,
                    displayName = name,
                    email = email,
                    photoUrl = firebaseUser.photoUrl?.toString() ?: "",
                    username = defaultUsername,
                    isSignedIn = true
                )
            }
        } catch (_: Exception) {}
    }

    suspend fun signInWithGoogle(context: Context): Result<UserProfile> {
        if (context.getString(R.string.google_web_client_id).isBlank()) {
            val error = IllegalStateException("Configure dollxr.google.webClientId in local.properties and add app/google-services.json")
            Toast.makeText(context, error.message, Toast.LENGTH_LONG).show()
            return Result.failure(error)
        }
        // Attempt 1: Credential Manager API
        val credManagerResult = tryCredentialManager(context)
        if (credManagerResult.isSuccess) {
            return credManagerResult
        }

        // Attempt 2: Legacy GoogleSignInClient Fallback
        val legacyResult = tryLegacyGoogleSignIn(context)
        if (legacyResult.isSuccess) {
            return legacyResult
        }

        val err = credManagerResult.exceptionOrNull() ?: legacyResult.exceptionOrNull() ?: Exception("Google Sign-In failed")
        val msg = if (err.message?.contains("10") == true || err.message?.contains("28444") == true) {
            "Ошибка конфигурации Google (Code 10). Убедитесь, что SHA-1 добавлен в Firebase Console!"
        } else {
            "Ошибка авторизации Google: ${err.localizedMessage}"
        }
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        return Result.failure(err)
    }

    private suspend fun tryCredentialManager(context: Context): Result<UserProfile> {
        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(context.getString(R.string.google_web_client_id))
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(request = request, context = context)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                authenticateWithFirebase(idToken, googleIdTokenCredential.displayName, googleIdTokenCredential.id, googleIdTokenCredential.profilePictureUri?.toString())
            } else {
                Result.failure(Exception("Unsupported credential type"))
            }
        } catch (e: GetCredentialCancellationException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun tryLegacyGoogleSignIn(context: Context): Result<UserProfile> {
        return try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(context.getString(R.string.google_web_client_id))
                .requestEmail()
                .build()

            val client = GoogleSignIn.getClient(context, gso)
            val account = GoogleSignIn.getLastSignedInAccount(context)
            if (account != null && account.idToken != null) {
                authenticateWithFirebase(account.idToken!!, account.displayName, account.email, account.photoUrl?.toString())
            } else {
                Result.failure(Exception("No cached Google account"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun authenticateWithFirebase(
        idToken: String,
        fallbackName: String?,
        fallbackEmail: String?,
        fallbackPhoto: String?
    ): Result<UserProfile> {
        return try {
            val authCredential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(authCredential).await()
            val firebaseUser = authResult.user

            val name = firebaseUser?.displayName ?: fallbackName ?: "DollXR User"
            val email = firebaseUser?.email ?: fallbackEmail ?: ""
            val photoUrl = firebaseUser?.photoUrl?.toString() ?: fallbackPhoto ?: ""
            val username = "@" + name.lowercase().replace(" ", "_")

            val profile = UserProfile(
                uid = firebaseUser?.uid ?: "google_uid",
                displayName = name,
                email = email,
                photoUrl = photoUrl,
                username = username,
                isSignedIn = true
            )
            _currentUserState.value = profile
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun updateProfile(displayName: String, username: String) {
        val current = _currentUserState.value
        val formattedUsername = if (!username.startsWith("@")) "@$username" else username
        _currentUserState.value = current.copy(
            displayName = displayName,
            username = formattedUsername
        )
    }

    fun signOut() {
        try {
            auth.signOut()
        } catch (_: Exception) {}
        _currentUserState.value = UserProfile()
    }
}
