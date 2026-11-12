package com.nextgenmatrix.data.push

import android.app.Service
import android.content.Intent
import android.os.IBinder
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(TeletrixNotifier.CONNECTION_NOTIFICATION_ID, notifier.connectionNotification())
        listenJob = scope.launch { listenLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        listenJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun listenLoop() {
        val client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
        while (scope.isActive) {
            if (!coordinator.shouldStreamNtfy()) {
                delay(1_000)
                continue
            }
            val topic = coordinator.ntfyTopic()
            val since = java.net.URLEncoder.encode(coordinator.ntfySince(), Charsets.UTF_8.name())
            val request = Request.Builder()
                .url("https://ntfy.sh/$topic/json?since=$since")
                .header("Accept", "application/x-ndjson")
                .build()
            val call = client.newCall(request)
            coordinator.attachNtfyCall(call)
            val ok = runCatching {
                call.execute().use { response ->
                    if (!response.isSuccessful) return@use false
                    val reader = response.body?.charStream()?.buffered() ?: return@use false
                    while (scope.isActive && coordinator.shouldStreamNtfy()) {
                        val line = reader.readLine() ?: return@use true
                        if (line.isBlank()) continue
                        coordinator.onNtfyLine(line)
                    }
                    true
                }
            }.getOrDefault(false)
            if (!scope.isActive) return
            delay(if (ok) 1_500 else 8_000)
        }
    }
}
