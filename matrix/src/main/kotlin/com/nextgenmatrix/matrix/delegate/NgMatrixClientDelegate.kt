package com.nextgenmatrix.matrix.delegate

import com.nextgenmatrix.matrix.session.MatrixSessionStore
import org.matrix.rustcomponents.sdk.ClientDelegate
import org.matrix.rustcomponents.sdk.ClientSessionDelegate
import org.matrix.rustcomponents.sdk.Session
import uniffi.matrix_sdk_common.BackgroundTaskFailureReason
import javax.inject.Inject

/**
 * Persists refreshed Matrix tokens and surfaces auth / background errors.
 * Implements both session + client delegate hooks required by the Rust SDK.
 */
class NgMatrixClientDelegate @Inject constructor(
    private val sessionStore: MatrixSessionStore,
) : ClientSessionDelegate, ClientDelegate {

    override fun saveSessionInKeychain(session: Session) {
        sessionStore.updateFromSdkSession(session)
    }

    override fun retrieveSessionFromKeychain(userId: String): Session {
        error("retrieveSessionFromKeychain is not used on Android single-process clients")
    }

    override fun didReceiveAuthError(isSoftLogout: Boolean) {
        sessionStore.markInvalidAndClearActive(isSoftLogout)
    }

    override fun onBackgroundTaskErrorReport(taskName: String, error: BackgroundTaskFailureReason) {
        if (error is BackgroundTaskFailureReason.Panic) {
            throw IllegalStateException("Matrix SDK panic in $taskName: $error")
        }
    }
}
