package com.nextgenmatrix.matrix.session

import org.matrix.rustcomponents.sdk.Session

/**
 * Persists active Matrix session metadata and receives SDK delegate callbacks.
 */
interface MatrixSessionStore {
    fun updateFromSdkSession(session: Session)
    fun markInvalidAndClearActive(isSoftLogout: Boolean)
}
