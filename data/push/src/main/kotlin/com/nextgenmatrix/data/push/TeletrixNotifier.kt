package com.nextgenmatrix.data.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.nextgenmatrix.core.model.IncomingNotification
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TeletrixNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val seen = ConcurrentHashMap.newKeySet<String>()
    private val roomLines = ConcurrentHashMap<String, MutableList<String>>()

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "New messages"
                enableVibration(true)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_CONNECTION, "Connection", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Keeps notifications arriving while Teletrix is in the background"
                setShowBadge(false)
            },
        )
    }

    fun show(item: IncomingNotification) {
        val key = "${item.roomId}|${item.eventId}"
        if (item.eventId.isNotBlank() && !seen.add(key)) return
        if (seen.size > 400) seen.clear()
        ensureChannels()
        val lines = roomLines.getOrPut(item.roomId) { mutableListOf() }
        val line = if (item.isDirect) item.body else "${item.senderName}: ${item.body}"
        synchronized(lines) {
            lines.add(line)
            while (lines.size > 8) lines.removeAt(0)
        }
        val title = item.roomTitle
        val text = if (item.isDirect) item.body else "${item.senderName}: ${item.body}"
        val inbox = NotificationCompat.InboxStyle().setSummaryText(title)
        synchronized(lines) { lines.forEach { inbox.addLine(it) } }
        val person = Person.Builder().setName(item.senderName).build()
        val messaging = NotificationCompat.MessagingStyle(Person.Builder().setName("You").build())
            .setConversationTitle(if (item.isDirect) null else title)
            .setGroupConversation(!item.isDirect)
            .addMessage(item.body, System.currentTimeMillis(), person)
        val notif = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(if (item.isDirect) messaging else inbox)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(
                if (item.noisy) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT,
            )
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setGroup(item.roomId)
            .setContentIntent(openRoomIntent(item.roomId))
            .setColor(ContextCompat.getColor(context, android.R.color.holo_blue_dark))
            .setNumber(synchronized(lines) { lines.size })
            .build()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify(item.roomId.hashCode(), notif)
    }

    fun clearRoom(roomId: String) {
        roomLines.remove(roomId)
        NotificationManagerCompat.from(context).cancel(roomId.hashCode())
    }

    fun connectionNotification(): android.app.Notification {
        ensureChannels()
        return NotificationCompat.Builder(context, CHANNEL_CONNECTION)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Teletrix")
            .setContentText("Notifications on")
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openAppIntent())
            .build()
    }

    private fun openRoomIntent(roomId: String): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("teletrix://room/${Uri.encode(roomId)}")).apply {
            setClassName(context.packageName, "com.nextgenmatrix.app.MainActivity")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            roomId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent().apply {
            setClassName(context.packageName, "com.nextgenmatrix.app.MainActivity")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_MESSAGES = "messages"
        const val CHANNEL_CONNECTION = "connection"
        const val CONNECTION_NOTIFICATION_ID = 71
    }
}
