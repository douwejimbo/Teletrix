package com.nextgenmatrix.data.push

import android.content.Context
import dagger.hilt.android.AndroidEntryPoint
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.MessagingReceiver
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage
import javax.inject.Inject

@AndroidEntryPoint
class TeletrixPushReceiver : MessagingReceiver() {
    @Inject lateinit var coordinator: PushCoordinator

    override fun onNewEndpoint(context: Context, endpoint: PushEndpoint, instance: String) {
        coordinator.onNewEndpoint(endpoint.url)
    }

    override fun onMessage(context: Context, message: PushMessage, instance: String) {
        coordinator.handlePushBytes(message.content)
    }

    override fun onUnregistered(context: Context, instance: String) {
        coordinator.onUnregistered()
    }

    override fun onRegistrationFailed(context: Context, reason: FailedReason, instance: String) {
        coordinator.onUnregistered()
    }
}
