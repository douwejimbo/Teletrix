package com.nextgenmatrix.feature.chat

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Persists which mentions and reactions the user has already seen in a room. */
@Singleton
class AttentionMarks @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("teletrix_attention", Context.MODE_PRIVATE)

    fun mentionWatermark(roomId: String): Long = prefs.getLong(mentionKey(roomId), UNSET)

    fun reactionWatermark(roomId: String): Long = prefs.getLong(reactionKey(roomId), UNSET)

    fun ensureMentionWatermark(roomId: String, hasUnreadMentions: Boolean) {
        if (prefs.contains(mentionKey(roomId))) return
        val seed = if (hasUnreadMentions) 0L else System.currentTimeMillis()
        prefs.edit().putLong(mentionKey(roomId), seed).apply()
    }

    fun ensureReactionWatermark(roomId: String) {
        if (prefs.contains(reactionKey(roomId))) return
        prefs.edit().putLong(reactionKey(roomId), System.currentTimeMillis()).apply()
    }

    fun raiseMentionWatermark(roomId: String, timestampMs: Long): Long {
        val next = maxOf(mentionWatermark(roomId), timestampMs)
        prefs.edit().putLong(mentionKey(roomId), next).apply()
        return next
    }

    fun raiseReactionWatermark(roomId: String, timestampMs: Long): Long {
        val next = maxOf(reactionWatermark(roomId), timestampMs)
        prefs.edit().putLong(reactionKey(roomId), next).apply()
        return next
    }

    private fun mentionKey(roomId: String) = "m:$roomId"

    private fun reactionKey(roomId: String) = "r:$roomId"

    private companion object {
        const val UNSET = -1L
    }
}
