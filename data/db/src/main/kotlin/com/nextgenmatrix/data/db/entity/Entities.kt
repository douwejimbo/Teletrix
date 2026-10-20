package com.nextgenmatrix.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "room_previews")
data class RoomPreviewEntity(
    @PrimaryKey val roomId: String,
    val title: String,
    val subtitle: String?,
    val lastPreview: String?,
    val lastTimestampMs: Long?,
    val kindOrdinal: Int,
    val updatedAtMs: Long,
)

@Entity(tableName = "chat_drafts")
data class ChatDraftEntity(
    @PrimaryKey val roomId: String,
    val body: String,
    val updatedAtMs: Long,
)

@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey val localId: String,
    val roomId: String,
    val body: String,
    val createdAtMs: Long,
    val attemptCount: Int,
    val nextAttemptAtMs: Long,
)
