package com.nextgenmatrix.data.push

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.nextgenmatrix.core.common.AppDispatchers
import com.nextgenmatrix.matrix.MatrixRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.UnifiedPush
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val matrix: MatrixRepository,
    private val notifier: TeletrixNotifier,
    dispatchers: AppDispatchers,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val prefs = context.getSharedPreferences("teletrix_push", Context.MODE_PRIVATE)

    private val _openRoomId = MutableStateFlow<String?>(null)
    val openRoomId: StateFlow<String?> = _openRoomId.asStateFlow()

    @Volatile
    private var unifiedPushReady = false

    init {
        notifier.ensureChannels()
        scope.launch {
            matrix.isLoggedIn.collect { signedIn ->
                if (signedIn) onSignedIn() else onSignedOut()
            }
        }
        scope.launch {
            matrix.incomingNotifications.collect { notifier.show(it) }
        }
    }

    fun offerOpenRoom(roomId: String) {
        _openRoomId.value = roomId
        notifier.clearRoom(roomId)
        matrix.setActiveConversation(roomId)
    }

    fun consumeOpenRoom() {
        _openRoomId.value = null
    }

    fun onNewEndpoint(url: String) {
        if (url.isBlank()) return
        unifiedPushReady = true
        prefs.edit().putString(KEY_UP_ENDPOINT, url).apply()
        stopNtfy()
        scope.launch { runCatching { matrix.registerHttpPusher(url) } }
    }

    fun onUnregistered() {
        unifiedPushReady = false
        prefs.edit().remove(KEY_UP_ENDPOINT).apply()
        if (matrix.isLoggedIn.value) startNtfyFallback()
    }

    fun handlePushBytes(bytes: ByteArray) {
        val ids = parsePushIds(bytes) ?: return
        scope.launch {
            if (!matrix.isLoggedIn.value) {
                runCatching { matrix.restoreSession() }
            }
            val item = matrix.notificationForEvent(ids.roomId, ids.eventId)
            if (item != null) notifier.show(item)
        }
    }

    private fun onSignedIn() {
        registerUnifiedPush()
        val saved = prefs.getString(KEY_UP_ENDPOINT, null)
        if (!saved.isNullOrBlank()) {
            unifiedPushReady = true
            scope.launch { runCatching { matrix.registerHttpPusher(saved) } }
        } else {
            // Give UnifiedPush a moment to deliver an endpoint before ntfy takes over.
            scope.launch {
                kotlinx.coroutines.delay(2500)
                if (!unifiedPushReady && matrix.isLoggedIn.value) startNtfyFallback()
            }
        }
    }

    private fun onSignedOut() {
        unifiedPushReady = false
        runCatching { UnifiedPush.unregister(context) }
        stopNtfy()
        prefs.edit().clear().apply()
    }

    private fun registerUnifiedPush() {
        UnifiedPush.tryUseCurrentOrDefaultDistributor(context) { available ->
            if (available) {
                UnifiedPush.register(context)
            }
        }
    }

    private fun startNtfyFallback() {
        val topic = ntfyTopic()
        val pushkey = "https://ntfy.sh/$topic"
        scope.launch { runCatching { matrix.registerHttpPusher(pushkey) } }
        val intent = Intent(context, TeletrixNtfyService::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private fun stopNtfy() {
        context.stopService(Intent(context, TeletrixNtfyService::class.java))
    }

    fun ntfyTopic(): String {
        val existing = prefs.getString(KEY_NTFY_TOPIC, null)
        if (!existing.isNullOrBlank()) return existing
        val created = java.util.UUID.randomUUID().toString().replace("-", "")
        prefs.edit().putString(KEY_NTFY_TOPIC, created).apply()
        return created
    }

    companion object {
        private const val KEY_UP_ENDPOINT = "up_endpoint"
        private const val KEY_NTFY_TOPIC = "ntfy_topic"
    }
}
