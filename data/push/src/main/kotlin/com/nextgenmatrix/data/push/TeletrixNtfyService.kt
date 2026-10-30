package com.nextgenmatrix.data.push

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class TeletrixNtfyService : Service() {
    @Inject lateinit var coordinator: PushCoordinator
    @Inject lateinit var notifier: TeletrixNotifier

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listenJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(TeletrixNotifier.CONNECTION_NOTIFICATION_ID, notifier.connectionNotification())
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "teletrix:ntfy").apply {
            setReferenceCounted(false)
            acquire(12 * 60 * 60 * 1000L)
        }
        listenJob = scope.launch { listenLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        listenJob?.cancel()
        scope.cancel()
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        super.onDestroy()
    }

    private suspend fun listenLoop() {
        val client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
        while (scope.isActive) {
            val topic = coordinator.ntfyTopic()
            val request = Request.Builder()
                .url("https://ntfy.sh/$topic/json")
                .header("Accept", "application/x-ndjson")
                .build()
            val ok = runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use false
                    val reader = response.body?.charStream()?.buffered() ?: return@use false
                    while (scope.isActive) {
                        val line = reader.readLine() ?: return@use true
                        if (line.isBlank()) continue
                        coordinator.handlePushBytes(line.toByteArray())
                    }
                    true
                }
            }.getOrDefault(false)
            if (!scope.isActive) return
            delay(if (ok) 1_500 else 8_000)
        }
    }
}
