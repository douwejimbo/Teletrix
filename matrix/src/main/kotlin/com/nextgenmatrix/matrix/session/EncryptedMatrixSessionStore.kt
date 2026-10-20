package com.nextgenmatrix.matrix.session

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.matrix.rustcomponents.sdk.Session
import org.matrix.rustcomponents.sdk.SlidingSyncVersion
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class PersistedSession(
    val accessToken: String,
    val refreshToken: String?,
    val userId: String,
    val deviceId: String,
    val homeserverUrl: String,
    val slidingSyncVersion: String,
    @SerialName("oAuthData")
    val oauthData: String? = null,
)

@Singleton
class EncryptedMatrixSessionStore @Inject constructor(
    @ApplicationContext context: Context,
) : MatrixSessionStore {

    private val json = Json { ignoreUnknownKeys = true }
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "nextgen_matrix_secure_sessions",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _activeUserId = MutableStateFlow<String?>(null)
    val activeUserId: StateFlow<String?> = _activeUserId.asStateFlow()

    init {
        _activeUserId.value = prefs.getString(KEY_ACTIVE_USER, null)
    }

    fun readPersistedSession(): Session? {
        val userId = prefs.getString(KEY_ACTIVE_USER, null) ?: return null
        val raw = prefs.getString(sessionKey(userId), null) ?: return null
        return runCatching {
            val p = json.decodeFromString<PersistedSession>(raw)
            val sliding = runCatching { SlidingSyncVersion.valueOf(p.slidingSyncVersion) }
                .getOrElse { SlidingSyncVersion.NATIVE }
            Session(
                accessToken = p.accessToken,
                refreshToken = p.refreshToken,
                userId = p.userId,
                deviceId = p.deviceId,
                homeserverUrl = p.homeserverUrl,
                oauthData = p.oauthData,
                slidingSyncVersion = sliding,
            )
        }.getOrNull()
    }

    fun rememberActiveUser(userId: String) {
        prefs.edit().putString(KEY_ACTIVE_USER, userId).commit()
        _activeUserId.value = userId
    }

    fun clearActiveUser() {
        prefs.edit().remove(KEY_ACTIVE_USER).commit()
        _activeUserId.value = null
    }

    override fun updateFromSdkSession(session: Session) {
        val p = PersistedSession(
            accessToken = session.accessToken,
            refreshToken = session.refreshToken,
            userId = session.userId,
            deviceId = session.deviceId,
            homeserverUrl = session.homeserverUrl,
            slidingSyncVersion = session.slidingSyncVersion.name,
            oauthData = session.oauthData,
        )
        prefs.edit()
            .putString(sessionKey(session.userId), json.encodeToString(p))
            .putString(KEY_ACTIVE_USER, session.userId)
            .commit()
        _activeUserId.value = session.userId
    }

    fun hasPersistedSession(): Boolean {
        val userId = prefs.getString(KEY_ACTIVE_USER, null) ?: return false
        return !prefs.getString(sessionKey(userId), null).isNullOrBlank()
    }

    override fun markInvalidAndClearActive(isSoftLogout: Boolean) {
        if (isSoftLogout) return
        val userId = prefs.getString(KEY_ACTIVE_USER, null)
        if (userId != null) {
            prefs.edit().remove(sessionKey(userId)).commit()
        }
        clearActiveUser()
    }

    private fun sessionKey(userId: String): String = "session_$userId"
}

private const val KEY_ACTIVE_USER = "active_user_id"
