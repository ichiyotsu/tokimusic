package com.tokimusic.app.data.sync
import com.tokimusic.app.data.AppStrings
import com.tokimusic.app.R

import android.app.Activity
import android.content.Context
import android.content.IntentSender
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 条 18：登录结果。优雅降级——不可用/未配置都不抛到 UI 崩溃面。 */
sealed interface GoogleSignInResult {
    data class Success(val profile: GoogleProfile) : GoogleSignInResult
    data object Unconfigured : GoogleSignInResult
    data object Cancelled : GoogleSignInResult
    data class Unavailable(val reason: String) : GoogleSignInResult
    data class Failed(val reason: String) : GoogleSignInResult
}

/** 条 18：Drive 授权结果。NeedsResolution 时由页面用 launcher 拉起系统授权页，完成后重走 authorize。 */
sealed interface DriveAuthResult {
    data class Granted(val accessToken: String) : DriveAuthResult
    data class NeedsResolution(val intentSender: IntentSender) : DriveAuthResult
    data class Failed(val reason: String) : DriveAuthResult
}

/**
 * Google 登录（Credential Manager + Google ID）与 Drive appdata 授权
 * （Identity AuthorizationClient）。全部手写官方 API，无第三方封装。
 */
class GoogleAuthManager(private val appContext: Context) {

    suspend fun signIn(activity: Activity): GoogleSignInResult {
        if (!GoogleConfig.isConfigured) return GoogleSignInResult.Unconfigured
        val credentialManager = try {
            CredentialManager.create(activity)
        } catch (e: Exception) {
            return GoogleSignInResult.Unavailable(e.message ?: AppStrings.getOrFallback(R.string.err_google_cm_unavailable, "Credential Manager 不可用"))
        }
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(GoogleConfig.webClientId)
            .setFilterByAuthorizedAccounts(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val response = credentialManager.getCredential(activity, request)
            val credential = GoogleIdTokenCredential.createFrom(response.credential.data)
            GoogleSignInResult.Success(
                GoogleProfile(
                    sub = credential.id,
                    name = credential.displayName ?: "",
                    email = credential.id,
                    avatarUrl = credential.profilePictureUri?.toString() ?: "",
                ),
            )
        } catch (e: GetCredentialCancellationException) {
            GoogleSignInResult.Cancelled
        } catch (e: NoCredentialException) {
            GoogleSignInResult.Unavailable(AppStrings.getOrFallback(R.string.err_google_no_account, "设备上没有可用的 Google 账号"))
        } catch (e: GetCredentialException) {
            GoogleSignInResult.Unavailable(e.message ?: AppStrings.getOrFallback(R.string.err_google_login_unavailable, "Google 登录不可用"))
        } catch (e: Exception) {
            GoogleSignInResult.Failed(e.message ?: AppStrings.getOrFallback(R.string.err_google_login_failed, "Google 登录失败"))
        }
    }

    suspend fun signOut() {
        runCatching {
            CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest())
        }
    }

    /** 请求 drive.appdata 授权；已授权直接回令牌，需要用户确认时回 NeedsResolution。 */
    suspend fun authorizeDrive(activity: Activity): DriveAuthResult =
        suspendCancellableCoroutine { cont ->
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(GoogleConfig.DRIVE_APPDATA_SCOPE)))
                .build()
            Identity.getAuthorizationClient(activity).authorize(request)
                .addOnSuccessListener { result ->
                    when {
                        result.hasResolution() -> {
                            val sender = result.pendingIntent?.intentSender
                            if (sender != null) {
                                cont.resume(DriveAuthResult.NeedsResolution(sender))
                            } else {
                                cont.resume(DriveAuthResult.Failed(AppStrings.getOrFallback(R.string.err_google_auth_no_page, "授权需要确认但系统未提供确认页")))
                            }
                        }
                        !result.accessToken.isNullOrBlank() ->
                            cont.resume(DriveAuthResult.Granted(result.accessToken!!))
                        else -> cont.resume(DriveAuthResult.Failed(AppStrings.getOrFallback(R.string.err_google_auth_no_token, "授权未返回访问令牌")))
                    }
                }
                .addOnFailureListener { e ->
                    cont.resume(DriveAuthResult.Failed(e.message ?: AppStrings.getOrFallback(R.string.err_drive_auth_failed, "Drive 授权失败")))
                }
        }
}
