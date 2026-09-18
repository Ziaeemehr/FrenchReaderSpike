package com.ziaee.frenchreader.backup

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.tasks.await

private const val WEB_CLIENT_ID = "623452723151-dn9i6noo7c40m72nf7fbsspa8uispjd8.apps.googleusercontent.com"
private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

data class GoogleAccountInfo(val email: String, val displayName: String?)

sealed interface AuthorizationOutcome {
    data class Authorized(val accessToken: String) : AuthorizationOutcome
    data class NeedsResolution(val pendingIntent: PendingIntent) : AuthorizationOutcome
}

/** Two separate Google APIs on purpose: [signIn] (Credential Manager) only proves
 * who the user is; it does not grant Drive access. [requestDriveAuthorization]
 * (the Identity Authorization API) is the one that actually authorizes
 * `drive.appdata` and hands back a usable access token. */
class GoogleAuthManager(private val context: Context) {
    private val credentialManager = CredentialManager.create(context)

    suspend fun signIn(): GoogleAccountInfo {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(WEB_CLIENT_ID)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val response = credentialManager.getCredential(context, request)
        val credential = GoogleIdTokenCredential.createFrom(response.credential.data)
        return GoogleAccountInfo(email = credential.id, displayName = credential.displayName)
    }

    suspend fun requestDriveAuthorization(): AuthorizationOutcome {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
            .build()
        val result = Identity.getAuthorizationClient(context).authorize(request).await()
        return if (result.hasResolution()) {
            AuthorizationOutcome.NeedsResolution(result.pendingIntent!!)
        } else {
            AuthorizationOutcome.Authorized(result.accessToken!!)
        }
    }

    fun extractAccessTokenFromResolutionResult(intent: Intent): String {
        val result = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(intent)
        return result.accessToken
            ?: throw IllegalStateException("Drive authorization resolution did not return an access token")
    }

    suspend fun signOut() {
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
    }
}
